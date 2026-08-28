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
    void reportsABeanMethodCallingAnotherBeanMethod() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean
                Neighbour neighbour() {
                    return new Neighbour();
                }

                @Bean
                Holder holder() {
                    return new Holder(neighbour());
                }
            }
            """;

        List<String> offences = SpringSourceRules.calledBeanMethods(source);

        assertEquals(1, offences.size(), "the call from holder is one offence");
        assertTrue(offences.getFirst().contains("holder calls neighbour"), "the offence names both methods");
    }

    @Test
    void reportsTheQualifiedFormOfTheSameCall() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean
                Neighbour neighbour() {
                    return new Neighbour();
                }

                @Bean
                Holder holder() {
                    return new Holder(this.neighbour());
                }
            }
            """;

        assertEquals(1, SpringSourceRules.calledBeanMethods(source).size(), "this. is the same call");
    }

    @Test
    void acceptsABeanTakenAsAMethodParameter() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean
                Neighbour neighbour() {
                    return new Neighbour();
                }

                @Bean
                Holder holder(Neighbour neighbour) {
                    return new Holder(neighbour);
                }
            }
            """;

        assertEquals(List.of(), SpringSourceRules.calledBeanMethods(source), "the container supplies it");
    }

    @Test
    void findsTheMethodPastAnAnnotationCarryingArguments() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean("first")
                @Qualifier(value = "primary")
                Neighbour neighbour() {
                    return new Neighbour();
                }

                @Bean("second")
                Holder holder() {
                    return new Holder(neighbour());
                }
            }
            """;

        assertEquals(1, SpringSourceRules.calledBeanMethods(source).size(), "annotation arguments are skipped");
    }

    @Test
    void passesOverACallToAMethodThatDeclaresNoBean() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean
                Holder holder() {
                    return new Holder(helper());
                }

                Neighbour helper() {
                    return new Neighbour();
                }
            }
            """;

        assertEquals(List.of(), SpringSourceRules.calledBeanMethods(source), "helper declares no bean");
    }

    @Test
    void readsABeanNameInsideALiteralAsText() {
        String source = """
            package com.example;

            @Configuration(proxyBeanMethods = false)
            final class Wiring {

                @Bean
                Neighbour neighbour() {
                    return new Neighbour();
                }

                @Bean
                Holder holder() {
                    return new Holder("neighbour()");
                }
            }
            """;

        assertEquals(List.of(), SpringSourceRules.calledBeanMethods(source), "a literal is not a call");
    }

    @Test
    void passesOverAnAnnotationThatOpensNoDeclaration() {
        assertEquals(List.of(), SpringSourceRules.calledBeanMethods("@Bean"), "there is no method to read");
    }

    @Test
    void passesOverABeanDeclarationThatOpensNoBody() {
        String source = """
            package com.example;

            interface Wiring {

                @Bean
                Neighbour neighbour();
            }
            """;

        assertEquals(List.of(), SpringSourceRules.calledBeanMethods(source), "an abstract bean has no body");
    }
}
