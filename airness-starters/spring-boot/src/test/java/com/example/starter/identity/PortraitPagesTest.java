package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.media.Pictures;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@DatabaseTest
class PortraitPagesTest {

    private static final String ACCOUNT = "/account";

    private static final String PORTRAIT = ACCOUNT + "/portrait";

    private static final String PICTURE = "picture";

    private static final int SIDE = 300;

    private static final int OTHER_SIDE = 280;

    private static final Pattern RENDITION = Pattern.compile("/media/[0-9a-f-]{36}/w\\d+\\.webp");

    private final Users users;

    PortraitPagesTest(Users users) {
        this.users = users;
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    private static String rendition(String page) {
        Matcher match = RENDITION.matcher(page);
        return match.find() ? match.group() : "";
    }

    private Caller reader(int port) {
        return Caller.signedIn(port, Accounts.admitted(this.users, "portrait", Role.USER), Accounts.SECRET);
    }

    @Test
    void showsAReaderWithTheirPortraitToEverybodySignedIn(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        HttpResponse<String> answer = reader.upload(PORTRAIT, PICTURE, Map.of("me.png", Pictures.png(SIDE, SIDE)));
        String portrait = rendition(reader.get(ACCOUNT).body());
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/account?notice=PORTRAIT_CHANGED")),
            () -> assertEquals(HttpStatus.OK.value(), this.reader(port).bytes(portrait).statusCode()),
            () -> assertEquals(HttpStatus.FOUND.value(), new Caller(port).bytes(portrait).statusCode())
        );
    }

    @Test
    void replacesAPortraitAndRemovesThePictureItReplaced(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        reader.upload(PORTRAIT, PICTURE, Map.of("first.png", Pictures.png(SIDE, SIDE)));
        String first = rendition(reader.get(ACCOUNT).body());
        reader.upload(PORTRAIT, PICTURE, Map.of("second.png", Pictures.png(OTHER_SIDE, OTHER_SIDE)));
        String second = rendition(reader.get(ACCOUNT).body());
        assertAll(
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), reader.bytes(first).statusCode()),
            () -> assertEquals(HttpStatus.OK.value(), reader.bytes(second).statusCode())
        );
    }

    @Test
    void removesAPortraitAndDrawsTheReaderByTheirInitials(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        reader.upload(PORTRAIT, PICTURE, Map.of("me.png", Pictures.png(SIDE, SIDE)));
        String portrait = rendition(reader.get(ACCOUNT).body());
        HttpResponse<String> answer = reader.post(PORTRAIT + "/delete", "");
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/account?notice=PORTRAIT_REMOVED")),
            () -> assertEquals("", rendition(reader.get(ACCOUNT).body())),
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), reader.bytes(portrait).statusCode())
        );
    }

    @Test
    void refusesADocumentAndSaysWhy(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        HttpResponse<String> answer = reader.upload(PORTRAIT, PICTURE, Map.of("cv.pdf", Pictures.pdf()));
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/account?portrait=UNSUPPORTED")),
            () -> assertTrue(reader.get("/account?portrait=UNSUPPORTED").body().contains("The file is not a picture.")),
            () -> assertEquals("", rendition(reader.get(ACCOUNT).body()))
        );
    }

    @Test
    void asksForAPictureWhereNoneWasChosenOrItCannotBeStored(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        assertAll(
            () -> assertTrue(landing(reader.upload(PORTRAIT, PICTURE, Map.of())).endsWith("?portrait=NONE_CHOSEN")),
            () -> assertTrue(
                landing(reader.upload(PORTRAIT, PICTURE, Map.of("", new byte[0]))).endsWith("?portrait=NONE_CHOSEN")
            ),
            () -> assertTrue(
                landing(reader.upload(PORTRAIT, PICTURE, Map.of("empty.png", new byte[0]))).endsWith("?portrait=EMPTY")
            )
        );
    }

    @Test
    void showsEveryAccountWithItsPortraitToAnAdministrator(@LocalServerPort int port) {
        Caller reader = this.reader(port);
        reader.upload(PORTRAIT, PICTURE, Map.of("me.png", Pictures.png(SIDE, SIDE)));
        String portrait = rendition(reader.get(ACCOUNT).body());
        String media = portrait.substring(0, portrait.lastIndexOf('/'));
        assertTrue(Caller.administrator(port).get("/users").body().contains(media));
    }
}
