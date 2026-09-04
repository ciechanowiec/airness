package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SpringContextEvidenceTest {

    private static final String DESTINATION = "airness.spring.context.evidence.file";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void recordsThePrimaryApplicationSourceWhenTheRunIsReady() {
        Path evidence = this.directory.resolve("ready.evidence");

        this.ready(new SpringApplication(ApplicationSource.class), evidence);

        assertEquals(
            List.of(ApplicationSource.class.getName()), Files.readAllLines(evidence),
            "the ready run names its real primary source"
        );
    }

    @Test
    @SneakyThrows
    void appendsEveryReadyRunWithoutReplacingEarlierEvidence() {
        Path evidence = this.directory.resolve("several.evidence");

        this.ready(new SpringApplication(SecondSource.class, ApplicationSource.class), evidence);
        this.ready(new SpringApplication(ApplicationSource.class), evidence);

        assertEquals(
            List.of(
                ApplicationSource.class.getName(),
                SecondSource.class.getName(),
                ApplicationSource.class.getName()
            ),
            Files.readAllLines(evidence),
            "each ready run remains available to the Maven goal"
        );
    }

    @Test
    @SneakyThrows
    void recordsTheMappingsTheReadySecurityChainLeavesOpen() {
        Path evidence = this.directory.resolve("open.evidence");

        this.ready(new SpringApplication(ApplicationSource.class), evidence, this.web());

        assertEquals(
            List.of(ApplicationSource.class.getName(), "open GET /public"),
            Files.readAllLines(evidence),
            "the run records both that it started and what starting it exposed"
        );
    }

    @Test
    void namesNoRootForAnApplicationInTheDefaultPackage() {
        assertEquals(
            Set.of(),
            SpringContextEvidence.roots(List.of("Application")),
            "a source in the default package names no package a handler could sit under"
        );
    }

    @Test
    void namesTheRootOfEachApplicationSourceThatHasOne() {
        assertEquals(
            Set.of("com.example", "com.other"),
            SpringContextEvidence.roots(List.of("com.example.Application", "com.other.Second", "Bare")),
            "each source that declares a package contributes the package it declares"
        );
    }

    @Test
    void writesNothingWhenTheHarnessSuppliedNoDestination() {
        Path evidence = this.directory.resolve("absent.evidence");
        Optional<String> previous = Optional.ofNullable(System.clearProperty(DESTINATION));
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            new SpringContextEvidence(new SpringApplication(ApplicationSource.class))
                .ready(context, Duration.ZERO);
        } finally {
            restore(previous);
        }

        assertFalse(Files.exists(evidence), "a runtime copy outside Airness verification is inert");
    }

    @Test
    void writesNothingForARunWithNoClassValuedPrimarySource() {
        Path evidence = this.directory.resolve("empty.evidence");

        this.ready(new SpringApplication(), evidence);

        assertFalse(Files.exists(evidence), "a run naming no class proves no production application");
    }

    @Test
    @SneakyThrows
    void refusesAChannelThatMakesNoProgress() {
        ByteBuffer evidence = StandardCharsets.UTF_8.encode("evidence");
        try (WritableByteChannel channel = new StalledChannel()) {
            IOException failure = assertThrows(
                IOException.class,
                () -> SpringContextEvidence.writeFully(channel, evidence),
                "a stalled evidence write must fail instead of spinning forever"
            );
            assertEquals("Evidence channel made no progress", failure.getMessage());
        }
    }

    @Test
    @SneakyThrows
    void publishesTheListenerThroughSpringFactories() {
        ClassLoader loader = SpringContextEvidence.class.getClassLoader();
        try (
            InputStream stream = loader.getResourceAsStream("META-INF/spring.factories")
        ) {
            String content = new String(
                Objects.requireNonNull(stream, "spring.factories is absent").readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertTrue(
                content.contains(SpringContextEvidence.class.getName()),
                "Spring Boot can discover the evidence listener"
            );
        }
    }

    private void ready(SpringApplication application, Path evidence) {
        this.ready(application, evidence, new GenericApplicationContext());
    }

    private void ready(
        SpringApplication application, Path evidence, ConfigurableApplicationContext context
    ) {
        Optional<String> previous = Optional.ofNullable(
            System.setProperty(DESTINATION, evidence.toString())
        );
        try (ConfigurableApplicationContext closing = context) {
            new SpringContextEvidence(application).ready(closing, Duration.ZERO);
        } finally {
            restore(previous);
        }
    }

    /**
     * A ready web application whose chain admits anyone to the one mapping it declares.
     */
    private ConfigurableApplicationContext web() {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        context.registerBean("mapping", RequestMappingHandlerMapping.class);
        context.registerBean(
            "springSecurityFilterChain", FilterChainProxy.class,
            () -> new FilterChainProxy(
                new DefaultSecurityFilterChain(
                    AnyRequestMatcher.INSTANCE,
                    new AuthorizationFilter(
                        RequestMatcherDelegatingAuthorizationManager.builder()
                            .add(
                                AnyRequestMatcher.INSTANCE, (_, _) -> new AuthorizationDecision(true)
                            )
                            .build()
                    )
                )
            )
        );
        context.registerBean(PublicEndpoint.class);
        context.refresh();
        return context;
    }

    private static void restore(Optional<String> previous) {
        previous.ifPresentOrElse(
            value -> System.setProperty(DESTINATION, value),
            () -> System.clearProperty(DESTINATION)
        );
    }

    private enum ApplicationSource {

        PRIMARY
    }

    private enum SecondSource {

        SECONDARY
    }

    private static final class StalledChannel implements WritableByteChannel {

        private final AtomicBoolean open;

        private StalledChannel() {
            this.open = new AtomicBoolean(true);
        }

        @Override
        public int write(ByteBuffer source) {
            assertTrue(source.hasRemaining(), "the listener offered evidence to the channel");
            return 0;
        }

        @Override
        public boolean isOpen() {
            return this.open.get();
        }

        @Override
        public void close() {
            this.open.set(false);
        }
    }
}
