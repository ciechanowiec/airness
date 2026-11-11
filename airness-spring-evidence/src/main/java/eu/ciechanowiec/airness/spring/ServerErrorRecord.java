package eu.ciechanowiec.airness.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * The errors the application reports while one Spring test class runs.
 *
 * <p>Two kinds of error are recorded. An event logged at ERROR is recorded through the filter Logback consults for
 * every logging call before it checks a level, so a logger switched off in the test configuration still reports
 * one, and nothing the configuration writes, or where, changes. An exception that ends a thread with nothing
 * catching it is recorded as the default handler of uncaught exceptions, which hands it on to the handler it
 * replaced.
 *
 * <p>The record exists from the start of a test class to its end, and is found through the default handler slot
 * it occupies for that time. That slot belongs to the JVM rather than to Logback, so the record survives the
 * resets Spring Boot puts Logback through, and anything that needs it, such as the listener that puts it back in
 * front of Logback after an application has set up its logging, finds it there. Outside a test class there is no
 * record, so an error a test caused is never charged to a test that ran without one.
 */
final class ServerErrorRecord extends TurboFilter implements Thread.UncaughtExceptionHandler {

    private final Thread.@Nullable UncaughtExceptionHandler previous;
    private final Queue<ServerError> recorded;
    private final AtomicReference<Optional<Method>> running;
    private final Lock installing;

    private ServerErrorRecord(Thread.@Nullable UncaughtExceptionHandler previous) {
        this.previous = previous;
        this.recorded = new ConcurrentLinkedQueue<>();
        this.running = new AtomicReference<>(Optional.empty());
        this.installing = new ReentrantLock();
    }

    /**
     * Opens a record for a test class, inside the record of any class that encloses it.
     *
     * @return the record, already in front of Logback and in the default handler slot
     */
    static ServerErrorRecord open() {
        ServerErrorRecord errors = new ServerErrorRecord(Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(errors);
        errors.install();
        return errors;
    }

    /**
     * The record of the test class that is running.
     *
     * @return the record, or nothing outside a test class
     */
    static Optional<ServerErrorRecord> current() {
        return Optional.ofNullable(Thread.getDefaultUncaughtExceptionHandler())
            .filter(ServerErrorRecord.class::isInstance)
            .map(ServerErrorRecord.class::cast);
    }

    /**
     * Puts the record in front of every other filter Logback consults, unless it is there already, because a filter
     * that denies an event ends the chain before the rest see it. Spring Boot stops and resets Logback whenever an
     * application it starts initializes logging, which removes every filter, so each run asks for this again.
     */
    void install() {
        LoggerContext logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.installing.lock();
        try {
            if (!logging.getTurboFilterList().contains(this)) {
                this.setContext(logging);
                this.start();
                logging.getTurboFilterList().addFirst(this);
            }
        } finally {
            this.installing.unlock();
        }
    }

    /**
     * Ends the record, handing the default handler slot and Logback back as they were before it opened.
     */
    void close() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getTurboFilterList().remove(this);
        if (this.equals(Thread.getDefaultUncaughtExceptionHandler())) {
            Thread.setDefaultUncaughtExceptionHandler(this.previous);
        }
    }

    /**
     * Notes that a test method starts.
     *
     * @param method the test method
     * @return true when no other test method of the record is running
     */
    boolean begin(Method method) {
        return this.running.compareAndSet(Optional.empty(), Optional.of(method));
    }

    /**
     * The test method of the record that is running.
     *
     * @return the method, or nothing between two test methods
     */
    Optional<Method> running() {
        return Objects.requireNonNull(this.running.get(), "the record always holds a running method or none");
    }

    /**
     * Notes that the test method running has ended.
     */
    void end() {
        this.running.set(Optional.empty());
    }

    /**
     * Takes every error recorded since the last time this was asked.
     *
     * @return the errors, in the order they were recorded
     */
    List<ServerError> drain() {
        List<ServerError> drained = new ArrayList<>();
        Optional<ServerError> next = Optional.ofNullable(this.recorded.poll());
        while (next.isPresent()) {
            drained.add(next.orElseThrow());
            next = Optional.ofNullable(this.recorded.poll());
        }
        return List.copyOf(drained);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        String message = "Exception in thread \"%s\" %s".formatted(thread.getName(), exception);
        this.recorded.add(new ServerError(thread.getName(), message, false));
        Optional.ofNullable(this.previous).ifPresent(handler -> handler.uncaughtException(thread, exception));
    }

    // A logger asked only whether it is enabled passes neither a message nor an exception, and is not an error.
    @Override
    public FilterReply decide(
        @Nullable Marker marker, Logger logger, Level level, @Nullable String format,
        Object @Nullable [] parameters, @Nullable Throwable thrown
    ) {
        Optional<Throwable> carried = Optional.ofNullable(thrown);
        boolean logged = Optional.ofNullable(format).isPresent() || carried.isPresent();
        if (logged && level.isGreaterOrEqual(Level.ERROR)) {
            String message = MessageFormatter.arrayFormat(format, parameters, thrown).getMessage();
            String described = carried.or(() -> Optional.ofNullable(MessageFormatter.getThrowableCandidate(parameters)))
                .map(exception -> "%s %s".formatted(message, exception))
                .orElse(message);
            this.recorded.add(new ServerError(logger.getName(), described, true));
        }
        return FilterReply.NEUTRAL;
    }
}
