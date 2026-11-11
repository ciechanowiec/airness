package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * That the record of server errors outlasts the logging setup of an application a test starts.
 */
class ServerErrorCaptureTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerErrorCaptureTest.class);

    private final ServerErrorRecord errors;

    ServerErrorCaptureTest() {
        this.errors = ServerErrorRecord.open();
    }

    @AfterEach
    void closeTheRecord() {
        this.errors.close();
    }

    private static SpringApplication application(Class<?> source) {
        SpringApplication application = new SpringApplication(source);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        return application;
    }

    @Test
    void recordsAnErrorLoggedOnceAnApplicationHasSetUpItsLogging() {
        try (ConfigurableApplicationContext context = application(Quiet.class).run()) {
            LOG.error("after start {}", context.getId());
        }
        List<String> recorded = this.errors.drain().stream().map(ServerError::source).toList();
        assertEquals(List.of(ServerErrorCaptureTest.class.getName()), recorded);
    }

    @Test
    void recordsWhySpringBootReportsAnApplicationFailedToStart() {
        SpringApplication application = application(Failing.class);
        assertThrows(BeanCreationException.class, application::run);
        assertTrue(
            this.errors.drain().stream().anyMatch(error -> error.loggedBy(SpringApplication.class)),
            "Spring Boot's report of the failed run was not recorded"
        );
    }

    @Test
    void readsTheLogbackContextSlf4jLogsThrough() {
        assertTrue(ServerErrorCapture.readable());
    }

    @Test
    void leavesAnApplicationStartedOutsideATestClassAlone() {
        this.errors.close();
        try (ConfigurableApplicationContext context = application(Quiet.class).run()) {
            LOG.error("unattended {}", context.getId());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(this.errors);
        }
        assertEquals(List.of(), this.errors.drain());
    }

    /**
     * An application that starts and reports nothing.
     */
    @Configuration(proxyBeanMethods = false)
    static class Quiet {
    }

    /**
     * An application that fails to start.
     */
    @Configuration(proxyBeanMethods = false)
    static class Failing {

        @Bean
        String refused() {
            throw new IllegalStateException("refused to start");
        }
    }
}
