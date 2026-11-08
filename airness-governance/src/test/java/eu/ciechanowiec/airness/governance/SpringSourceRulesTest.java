package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringSourceRulesTest {

    private static final String ROOT = "com.example";

    @Test
    void acceptsAnApplicationClassAtTheDeclaredRoot() {
        String source = """
            package com.example;

            @SpringBootApplication(proxyBeanMethods = false)
            final class Application {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.misplacedEntryPoint(source, ROOT), "the package matches");
    }

    @Test
    void reportsAnApplicationClassBelowTheDeclaredRoot() {
        String source = """
            package com.example.boot;

            @SpringBootApplication(proxyBeanMethods = false)
            final class Application {
            }
            """;

        List<String> offences = SpringSourceRules.misplacedEntryPoint(source, ROOT);

        assertEquals(1, offences.size(), "the deeper package is one offence");
        assertTrue(offences.getFirst().contains("com.example.boot"), "the offence names the package found");
    }

    @Test
    void passesOverASourceThatDeclaresNoApplicationClass() {
        String source = """
            package com.example.boot;

            final class Neighbour {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.misplacedEntryPoint(source, ROOT), "no annotation is present");
    }

    @Test
    void readsTheAnnotationAsProseWhenACommentCarriesIt() {
        String source = """
            package com.example.boot;

            // @SpringBootApplication lives in another module
            final class Neighbour {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.misplacedEntryPoint(source, ROOT), "a comment is not code");
    }

    @Test
    void reportsAControllerBeanNameWrittenAsAPath() {
        String source = """
            package com.example;

            @Controller("/orders")
            final class Orders {
            }
            """;

        List<String> offences = SpringSourceRules.controllerPaths(source);

        assertEquals(1, offences.size(), "the slash makes the default member look like a mapping");
        assertTrue(offences.getFirst().contains("@RequestMapping"), "the repair names the mapping annotation");
    }

    @Test
    void resolvesAControllerPathFromASameSourceConstant() {
        String source = """
            package com.example;

            @Controller(value = PATH)
            final class Orders {

                private static final String PATH = "/orders";
            }
            """;

        assertEquals(1, SpringSourceRules.controllerPaths(source).size(), "the constant states the path");
    }

    @Test
    void resolvesAControllerPathWhereOneNameIsDeclaredInTwoScopes() {
        String source = """
            package com.example;

            @Controller(value = PATH)
            final class Orders {

                private static final String PATH = "/orders";

                static final class Drafts {

                    private static final String PATH = "/drafts";
                }
            }
            """;

        assertEquals(
            1, SpringSourceRules.controllerPaths(source).size(),
            "one name in two scopes is one reading rather than a failure"
        );
    }

    @Test
    void leavesAControllerWithAnOrdinaryBeanName() {
        String source = """
            package com.example;

            @Controller("orders")
            final class Orders {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.controllerPaths(source), "a bean name is the member's role");
    }

    @Test
    void passesOverAControllerValueItCannotResolve() {
        String source = """
            package com.example;

            @Controller(Names.PATH)
            final class Orders {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.controllerPaths(source), "the source does not state its value");
    }

    @Test
    void passesOverAControllerAnnotationInsideAComment() {
        String source = """
            package com.example;

            // @Controller("/orders")
            final class Orders {
            }
            """;

        assertEquals(List.of(), SpringSourceRules.controllerPaths(source), "prose maps no controller");
    }
}
