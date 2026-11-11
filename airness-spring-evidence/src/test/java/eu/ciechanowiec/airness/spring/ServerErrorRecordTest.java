package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.LoggerContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

/**
 * What the record of server errors reads from Logback and from dying threads, and what it leaves alone.
 */
class ServerErrorRecordTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerErrorRecordTest.class);
    private static final String SOURCE = ServerErrorRecordTest.class.getName();
    private static final Duration ENDING = Duration.ofSeconds(10);
    private final IllegalStateException fault;
    private final ServerErrorRecord errors;

    ServerErrorRecordTest() {
        this.fault = new IllegalStateException("broken");
        this.errors = ServerErrorRecord.open();
    }

    @AfterEach
    void closeTheRecord() {
        this.errors.close();
    }

    private List<String> recorded() {
        return this.errors.drain().stream().map(ServerError::described).toList();
    }

    @Test
    void isTheRecordOfTheClassThatRuns() {
        assertSame(this.errors, ServerErrorRecord.current().orElseThrow());
    }

    @Test
    void recordsAPlainMessage() {
        LOG.error("plain");
        assertEquals(List.of(SOURCE + ": plain"), this.recorded());
    }

    @Test
    void recordsMessagesWithOneTwoAndMoreArguments() {
        LOG.error("one {}", 1);
        LOG.error("two {} {}", 1, 2);
        LOG.error("three {} {} {}", 1, 2, 3);
        assertEquals(List.of(SOURCE + ": one 1", SOURCE + ": two 1 2", SOURCE + ": three 1 2 3"), this.recorded());
    }

    @Test
    void recordsTheExceptionAMessageCarries() {
        LOG.error("failed", this.fault);
        LOG.error("failed {}", "here", this.fault);
        assertEquals(
            List.of(SOURCE + ": failed " + this.fault, SOURCE + ": failed here " + this.fault), this.recorded()
        );
    }

    @Test
    void recordsAnErrorThatCarriesOnlyItsException() {
        LOG.atError().setCause(this.fault).log();
        assertEquals(List.of(SOURCE + ": null " + this.fault), this.recorded());
    }

    @Test
    void recordsAMarkedMessage() {
        LOG.error(MarkerFactory.getMarker("AUDIT"), "marked");
        assertEquals(List.of(SOURCE + ": marked"), this.recorded());
    }

    @Test
    void recordsTheFluentForms() {
        LOG.atError().log("fluent");
        LOG.atError().setCause(this.fault).log("fluent {}", "cause");
        LOG.atLevel(Level.ERROR).log("levelled");
        assertEquals(
            List.of(SOURCE + ": fluent", SOURCE + ": fluent cause " + this.fault, SOURCE + ": levelled"),
            this.recorded()
        );
    }

    @Test
    void leavesWarningsAndEnablementQuestionsAlone() {
        LOG.warn("warned");
        assertTrue(LOG.isErrorEnabled());
        assertEquals(List.of(), this.recorded());
    }

    @Test
    void returnsInFrontOfLogbackAfterItIsReset() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
        context.reset();
        LOG.error("lost");
        this.errors.install();
        this.errors.install();
        LOG.error("kept");
        context.start();
        assertEquals(List.of(SOURCE + ": kept"), this.recorded());
    }

    @Test
    void recordsAnUncaughtExceptionAsAnErrorNoTestCanDeclare() {
        Thread worker = Thread.currentThread();
        this.errors.uncaughtException(worker, this.fault);
        List<ServerError> recorded = this.errors.drain();
        assertEquals(
            List.of("%s: Exception in thread \"%s\" %s".formatted(worker.getName(), worker.getName(), this.fault)),
            recorded.stream().map(ServerError::described).toList()
        );
        assertFalse(recorded.getFirst().loggedBy(ServerErrorRecordTest.class));
    }

    @Test
    @SneakyThrows
    void recordsAnExceptionThatEndsARealThread() {
        IllegalStateException ending = this.fault;
        Thread thread = new Thread(
            () -> {
                throw ending;
            }, "ending"
        );
        thread.start();
        assertTrue(thread.join(ENDING));
        assertEquals(List.of("ending: Exception in thread \"ending\" " + this.fault), this.recorded());
    }

    @Test
    void handsAnUncaughtExceptionToTheHandlerItReplaced() {
        AtomicReference<Optional<Throwable>> handed = new AtomicReference<>(Optional.empty());
        Thread.setDefaultUncaughtExceptionHandler((_, exception) -> handed.set(Optional.of(exception)));
        ServerErrorRecord inner = ServerErrorRecord.open();
        try {
            inner.uncaughtException(Thread.currentThread(), this.fault);
        } finally {
            inner.close();
            Thread.setDefaultUncaughtExceptionHandler(this.errors);
        }
        assertEquals(Optional.of(this.fault), handed.get());
    }

    @Test
    void handsTheSlotAndLogbackBackWhenItCloses() {
        ServerErrorRecord inner = ServerErrorRecord.open();
        inner.close();
        LOG.error("outer");
        assertEquals(Optional.of(this.errors), ServerErrorRecord.current());
        assertEquals(List.of(), inner.drain());
        assertEquals(List.of(SOURCE + ": outer"), this.recorded());
    }

    @Test
    void leavesASlotSomebodyElseTookAlone() {
        ServerErrorRecord inner = ServerErrorRecord.open();
        Thread.UncaughtExceptionHandler other = (_, _) -> {
        };
        Thread.setDefaultUncaughtExceptionHandler(other);
        inner.close();
        assertSame(other, Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(this.errors);
    }

    @Test
    @SneakyThrows
    void admitsOneTestMethodAtATime() {
        Method first = ServerErrorRecordTest.class.getDeclaredMethod("admitsOneTestMethodAtATime");
        assertTrue(this.errors.begin(first));
        assertFalse(this.errors.begin(first));
        assertEquals(Optional.of(first), this.errors.running());
        this.errors.end();
        assertEquals(Optional.empty(), this.errors.running());
    }
}
