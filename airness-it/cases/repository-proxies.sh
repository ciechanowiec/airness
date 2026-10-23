#!/usr/bin/env sh

run_repository_proxy_cases() {
    repository_proxy_app="${scratch}/repository-proxy-app"
    clone_tree "${spring_app}" "${repository_proxy_app}"
    # Exception translation is exercised only by these real-context tests, not by the application.
    perl -0pi -e 's|^    </dependencies>|        <dependency>\n            <groupId>org.springframework</groupId>\n            <artifactId>spring-tx</artifactId>\n            <scope>test</scope>\n        </dependency>\n    </dependencies>|m' "${repository_proxy_app}/pom.xml"
    cat > "${repository_proxy_app}/src/main/java/com/example/Catalogue.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Repository;

/**
 * A catalogue whose public lookup can be called through a Spring proxy.
 */
@Repository
public class Catalogue {

    /**
     * Reads a normalized catalogue key.
     *
     * @param key the requested key
     * @return the normalized key
     */
    public String lookup(String key) {
        return key.strip();
    }
}
JAVA
    cat > "${repository_proxy_app}/src/test/java/com/example/RepositoryRuntimeTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.stereotype.Repository;

class RepositoryRuntimeTest {

    @Test
    void startsAnOpenRepositoryWithAClassProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class);
            context.registerBean(Catalogue.class);
            context.refresh();
            Catalogue catalogue = context.getBean(Catalogue.class);
            assertTrue(AopUtils.isCglibProxy(catalogue));
            assertEquals("catalogue", catalogue.lookup(" catalogue "));
        }
    }

    @Test
    void refusesFinalWhenAClassProxyIsRequired() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class);
            context.registerBean(ClosedCatalogue.class);
            BeanCreationException failure = assertThrows(BeanCreationException.class, context::refresh);
            assertTrue(failure.getMostSpecificCause().toString().contains("Cannot subclass final class"));
        }
    }

    @Test
    void allowsFinalWithoutExceptionTranslation() {
        try (
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ClosedCatalogue.class)
        ) {
            ClosedCatalogue catalogue = context.getBean(ClosedCatalogue.class);
            assertFalse(AopUtils.isAopProxy(catalogue));
            assertEquals("unproxied", catalogue.lookup(" unproxied "));
        }
    }

    @Test
    void allowsFinalWithAnInterfaceProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class);
            context.registerBean(InterfaceCatalogue.class);
            context.refresh();
            Lookup catalogue = context.getBean(Lookup.class);
            assertTrue(AopUtils.isJdkDynamicProxy(catalogue));
            assertEquals("interface", catalogue.lookup(" interface "));
        }
    }

    @Repository
    @TestComponent
    static final class ClosedCatalogue {

        String lookup(String key) {
            return key.strip();
        }
    }

    @FunctionalInterface
    interface Lookup {

        String lookup(String key);
    }

    @Repository
    @TestComponent
    static final class InterfaceCatalogue implements Lookup {

        @Override
        public String lookup(String key) {
            return key.strip();
        }
    }
}
JAVA
    git -C "${repository_proxy_app}" add --all
    run_maven repository_proxy_verify spring "${repository_proxy_app}" clean verify
    expect_exit repository_proxy_verify 'spring: a concrete repository verifies with the installed finality exemption' 0
    expect_match repository_proxy_verify 'spring: all real repository proxy controls execute successfully' \
        'Tests run: 4, Failures: 0, Errors: 0, Skipped: 0.*RepositoryRuntimeTest'
    expect_match repository_proxy_verify 'spring: the repository consumer reaches the full verification verdict' \
        'BUILD SUCCESS'

    # The two finality variants, an ordinary component under the Spring parent and a repository under the
    # plain parent, are rule questions. CheckstyleVariantTest decides the first in process, and the plain
    # parent's wiring is asserted by the batched analysis consumer, which plants the same repository.
}
