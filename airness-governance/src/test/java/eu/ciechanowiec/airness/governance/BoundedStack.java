package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Runs a reader on a stack of a known size.
 *
 * <p>A scan that goes one frame deeper for every character it reads passes every test written against a
 * short fixture. What it fails on is whichever source grew last, and how long that source has to be
 * depends on the stack the runner happened to leave, so the failure lands on a commit that touched
 * neither the rule nor the file it read. A test that means to pin the shape therefore names the stack it
 * allows, rather than taking the one it was given.
 *
 * <p>The size below is a modest one by the standards of a thread, and generous by the standards of a
 * reader that spends a frame per token rather than per character. Every rule that scans a source is read
 * through here against an input longer than any fixture, so the depth each of them costs is answered by
 * a test rather than by the next long file someone writes.
 */
@UtilityClass
final class BoundedStack {

    private static final int BYTES = 512 * 1024;

    /**
     * The reader's answer, or nothing when it ran out of stack.
     *
     * @param <T>    what the reader answers with
     * @param reader the reader to run
     * @return what it answered, or empty when the stack it was given ran out
     */
    static <T> Optional<T> read(Supplier<T> reader) {
        List<T> answered = new ArrayList<>();
        Thread thread = new Thread(null, () -> answered.add(reader.get()), "bounded-stack", BYTES);
        thread.start();
        join(thread);
        return answered.stream().findFirst();
    }

    @SneakyThrows
    private static void join(Thread thread) {
        thread.join();
    }
}
