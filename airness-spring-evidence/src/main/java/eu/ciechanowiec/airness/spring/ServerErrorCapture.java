package eu.ciechanowiec.airness.spring;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Keeps the record of server errors in place across the logging setup of every application a Spring test starts.
 *
 * <p>Spring Boot stops and resets Logback when an application initializes logging, which happens as its
 * environment is prepared and removes the filter the record reads through. Spring Boot constructs this listener
 * from {@code spring.factories} for every application run and calls it after its own logging listener, so the
 * record is put back before the application does anything that could report an error, and once more if the run
 * fails, before Spring Boot reports why. Outside a Spring test class there is no record and this does nothing.
 */
public final class ServerErrorCapture implements SpringApplicationRunListener {

    private static final String LOGBACK_CONTEXT = "ch.qos.logback.classic.LoggerContext";
    private static final String RECORD = "eu.ciechanowiec.airness.spring.ServerErrorRecord";

    /**
     * Creates the listener for one Spring application run.
     *
     * @param application the application being run
     * @param arguments   its command-line arguments
     */
    public ServerErrorCapture(SpringApplication application, String... arguments) {
        Objects.requireNonNull(application, "Spring Boot supplied no application");
        Objects.requireNonNull(arguments, "Spring Boot supplied no application arguments");
    }

    /**
     * Whether SLF4J logs through Logback in this JVM, which is the backend the record reads. The factory is compared
     * by name, so the question links no Logback class in a JVM that has none.
     *
     * @return true when the SLF4J logger factory is a Logback context
     */
    static boolean readable() {
        return LOGBACK_CONTEXT.equals(LoggerFactory.getILoggerFactory().getClass().getName());
    }

    @Override
    public void environmentPrepared(
        ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment
    ) {
        recapture();
    }

    @Override
    public void failed(@Nullable ConfigurableApplicationContext context, Throwable exception) {
        recapture();
    }

    // The record is recognized by name before it is touched, so an application run outside a test, in a JVM that
    // may hold no Logback at all, loads none of the classes the record is built on.
    private static void recapture() {
        Optional.ofNullable(Thread.getDefaultUncaughtExceptionHandler())
            .filter(handler -> RECORD.equals(handler.getClass().getName()))
            .ifPresent(handler -> ((ServerErrorRecord) handler).install());
    }
}
