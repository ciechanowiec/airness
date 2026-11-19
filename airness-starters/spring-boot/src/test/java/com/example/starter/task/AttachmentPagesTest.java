package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import com.example.starter.media.Pictures;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AttachmentPagesTest {

    private static final String FILES = "files";

    // Pictures of a size a page draws them at, and smaller ones where only being stored matters.
    private static final int WIDE = 900;

    private static final int HIGH = 600;

    private static final int SMALL = 32;

    private static final Pattern RENDITION = Pattern.compile("/media/[0-9a-f-]{36}/w\\d+\\.avif");

    private static final Pattern ORIGINAL = Pattern.compile("/media/[0-9a-f-]{36}/original");

    private final Users users;

    private final Tasks tasks;

    private final EntityManager entities;

    private final JdbcTemplate database;

    private final LocalDate today;

    AttachmentPagesTest(Users users, Tasks tasks, EntityManager entities, JdbcTemplate database, Clock clock) {
        this.users = users;
        this.tasks = tasks;
        this.entities = entities;
        this.database = database;
        this.today = LocalDate.now(clock);
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    private static String found(Pattern pattern, String page) {
        Matcher match = pattern.matcher(page);
        return match.find() ? match.group() : "";
    }

    // The path and query of an address the application answered with, which it writes out whole.
    private static String within(String address) {
        URI absolute = URI.create(address);
        return absolute.getRawPath() + "?" + absolute.getRawQuery();
    }

    // The reference of a stored file, out of an address under /media/.
    private static String mediaOf(String address) {
        int start = "/media/".length();
        return address.substring(start, start + UUID.randomUUID().toString().length());
    }

    private static String attachments(UUID task) {
        return "/tasks/%s/attachments".formatted(task);
    }

    private static String edit(UUID task) {
        return "/tasks/%s/edit".formatted(task);
    }

    private String reader() {
        return Accounts.admitted(this.users, "attaching", Role.USER);
    }

    private UUID task(String email) {
        return Notes.written(this.tasks, email, Notes.open(Notes.title("Attached"), this.today));
    }

    @Test
    void attachesAPictureAndDrawsItFromItsRenditions(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        HttpResponse<String> answer = reader.upload(
            attachments(task), FILES, Map.of("river.jpg", Pictures.jpeg(WIDE, HIGH))
        );
        String page = reader.get(edit(task)).body();
        HttpResponse<byte[]> rendition = reader.bytes(found(RENDITION, page));
        assertAll(
            () -> assertTrue(landing(answer).endsWith(edit(task) + "?notice=ATTACHED")),
            () -> assertTrue(page.contains("river.jpg")),
            () -> assertEquals(HttpStatus.OK.value(), rendition.statusCode()),
            () -> assertEquals("image/avif", rendition.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("")),
            () -> assertEquals(
                "max-age=31536000, private, immutable",
                rendition.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse("")
            )
        );
    }

    @Test
    void downloadsAFileAsItWasHandedOver(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        byte[] pdf = Pictures.pdf();
        reader.upload(attachments(task), FILES, Map.of("brief.pdf", pdf));
        HttpResponse<byte[]> original = reader.bytes(found(ORIGINAL, reader.get(edit(task)).body()));
        assertAll(
            () -> assertEquals(pdf.length, original.body().length),
            () -> assertTrue(
                original.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse("").startsWith("attachment;")
            )
        );
    }

    @Test
    void saysWhyAFileWasNotAttached(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        HttpResponse<String> answer = reader.upload(
            attachments(task), FILES, Map.of("notes.jpg", "plain text".getBytes(StandardCharsets.US_ASCII))
        );
        assertAll(
            () -> assertTrue(landing(answer).endsWith(edit(task) + "?refused=UNSUPPORTED")),
            () -> assertTrue(
                reader.get(within(landing(answer))).body().contains("One of the files is not an image or a PDF.")
            )
        );
    }

    @Test
    void asksForAFileWhereNoneWasChosen(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        assertAll(
            () -> assertTrue(
                landing(reader.upload(attachments(task), FILES, Map.of("", new byte[0])))
                    .endsWith(edit(task) + "?refused=NONE_CHOSEN")
            ),
            () -> assertTrue(
                landing(reader.upload(attachments(task), FILES, Map.of())).endsWith(
                    edit(task) + "?refused=NONE_CHOSEN"
                )
            )
        );
    }

    @Test
    void attachesNoMoreFilesThanATaskHolds(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        // A full task is written straight into the tables, since what is refused is a file beyond the limit and
        // not the files under it, whose bytes no test here reads.
        this.database.update(
            """
                WITH stored AS (
                    INSERT INTO media_object (reference, kind, purpose, original_name, content_type, byte_size, sha256,
                        created_by, created_at)
                    SELECT gen_random_uuid(), 'IMAGE', 'ATTACHMENT', 'filler.png', 'image/png', 1, repeat('0', 64),
                        task.owner_id, now()
                    FROM task, generate_series(1, ?)
                    WHERE task.reference = ?
                    RETURNING id, created_by
                )
                INSERT INTO task_attachment (task_id, media_id, added_by, added_at)
                SELECT (SELECT id FROM task WHERE reference = ?), stored.id, stored.created_by, now() FROM stored
                """,
            TaskLimits.ATTACHMENTS, task, task
        );
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .upload(attachments(task), FILES, Map.of("extra.png", Pictures.png(SMALL, SMALL)));
        assertTrue(landing(answer).endsWith(edit(task) + "?refused=TOO_MANY"));
    }

    @Test
    void keepsAReadersFilesFromEverybodyElse(@LocalServerPort int port) {
        String owner = this.reader();
        UUID task = this.task(owner);
        Caller reader = Caller.signedIn(port, owner, Accounts.SECRET);
        reader.upload(attachments(task), FILES, Map.of("private.png", Pictures.png(SMALL, SMALL)));
        String original = found(ORIGINAL, reader.get(edit(task)).body());
        Caller stranger = Caller.signedIn(port, this.reader(), Accounts.SECRET);
        assertAll(
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), stranger.bytes(original).statusCode()),
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(),
                stranger.upload(attachments(task), FILES, Map.of("intruder.png", Pictures.png(SMALL, SMALL)))
                    .statusCode()
            ),
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(),
                stranger.post("%s/%s/delete".formatted(attachments(task), mediaOf(original)), "").statusCode()
            ),
            () -> assertEquals(HttpStatus.OK.value(), reader.bytes(original).statusCode())
        );
    }

    @Test
    void recordsAnAdministratorAttachingToSomebodyElsesTask(@LocalServerPort int port) {
        UUID task = this.task(this.reader());
        Caller.administrator(port).upload(attachments(task), FILES, Map.of("audit.png", Pictures.png(SMALL, SMALL)));
        Long recorded = this.entities.createQuery(
            """
                SELECT COUNT(event) FROM AuditEvent event
                WHERE event.deed.target = :target AND event.deed.action = :action
                """,
            Long.class
        ).setParameter("target", task.toString()).setParameter("action", AuditAction.ATTACHMENT_ADDED)
            .getSingleResult();
        assertEquals(1L, recorded);
    }

    @Test
    void removesAFileTakenFromATask(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        reader.upload(attachments(task), FILES, Map.of("gone.png", Pictures.png(SMALL, SMALL)));
        String original = found(ORIGINAL, reader.get(edit(task)).body());
        String media = mediaOf(original);
        HttpResponse<String> answer = reader.post("%s/%s/delete".formatted(attachments(task), media), "");
        assertAll(
            () -> assertTrue(landing(answer).endsWith(edit(task) + "?notice=DETACHED")),
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), reader.bytes(original).statusCode())
        );
    }

    @Test
    void removesTheFilesOfARemovedTask(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        reader.upload(attachments(task), FILES, Map.of("with-task.png", Pictures.png(SMALL, SMALL)));
        String original = found(ORIGINAL, reader.get(edit(task)).body());
        reader.post("/tasks/%s/delete".formatted(task), "");
        assertEquals(HttpStatus.NOT_FOUND.value(), reader.bytes(original).statusCode());
    }

    @Test
    void showsATaskByItsFirstPictureOnTheListAndTheBoard(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = this.task(email);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        reader.upload(attachments(task), FILES, Map.of("cover.jpg", Pictures.jpeg(WIDE, HIGH)));
        String rendition = found(RENDITION, reader.get(edit(task)).body());
        String media = rendition.substring(0, rendition.lastIndexOf('/'));
        assertAll(
            () -> assertTrue(reader.get("/tasks").body().contains(media)),
            () -> assertTrue(reader.get("/tasks/board").body().contains(media))
        );
    }
}
