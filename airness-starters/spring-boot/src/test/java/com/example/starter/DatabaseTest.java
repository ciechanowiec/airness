package com.example.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * Marks a test that reaches the running application and its database. The declarations are composed into
 * one rather than inherited from a base class, and every test carrying this marker names the same context,
 * so Spring builds one for the whole suite together with the single database {@link Containers} declares.
 * The application listens on a port of its own, so a test reaches it over real HTTP.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ImportTestcontainers(Containers.class)
public @interface DatabaseTest {
}
