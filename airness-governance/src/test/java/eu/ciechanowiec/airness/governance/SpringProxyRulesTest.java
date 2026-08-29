package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringProxyRulesTest {

    @Test
    void reportsACallFromOneMethodToAProxiedMethodBesideIt() {
        String source = """
            package com.example;

            class Ledger {

                @Transactional(readOnly = false, timeout = 5)
                public void post() {
                }

                public void postTwice() {
                    post();
                    post();
                }
            }
            """;

        List<String> offences = SpringProxyRules.selfInvocations(source);

        assertEquals(2, offences.size(), "each call bypasses the proxy on its own");
        assertTrue(offences.getFirst().contains("the call to post"), "the offence names the method");
    }

    @Test
    void readsTheQualifiedFormOfTheSameCall() {
        String source = """
            package com.example;

            class Ledger {

                @Async
                public void publish() {
                }

                public void run() {
                    this.publish();
                }
            }
            """;

        assertEquals(1, SpringProxyRules.selfInvocations(source).size(), "this. never leaves the object");
    }

    @Test
    void acceptsTheSameCallMadeOnAnotherBean() {
        String source = """
            package com.example;

            class Caller {

                @Transactional(readOnly = false, timeout = 5)
                public void post() {
                }

                public void delegate(Ledger ledger) {
                    ledger.post();
                }
            }
            """;

        assertEquals(List.of(), SpringProxyRules.selfInvocations(source), "another bean is the fix");
    }

    @Test
    void passesOverTheDeclarationOfTheProxiedMethodItself() {
        String source = """
            package com.example;

            class Ledger {

                @Cacheable("names")
                public String read() {
                    return "";
                }
            }
            """;

        assertEquals(List.of(), SpringProxyRules.selfInvocations(source), "a declaration is not a call");
    }

    @Test
    void passesOverASourceCarryingNoProxiedAnnotation() {
        String source = """
            package com.example;

            class Plain {

                public void act() {
                    this.helper();
                }

                void helper() {
                }
            }
            """;

        assertEquals(List.of(), SpringProxyRules.selfInvocations(source), "nothing here is proxied");
    }

    @Test
    void readsAProxiedNameInsideALiteralAsText() {
        String source = """
            package com.example;

            class Ledger {

                @Retryable
                public void post() {
                }

                public String describe() {
                    return "post()";
                }
            }
            """;

        assertEquals(List.of(), SpringProxyRules.selfInvocations(source), "a literal is not a call");
    }

    @Test
    void reportsACallMadeWhileTheBeanIsStillBeingBuilt() {
        String source = """
            package com.example;

            class Ledger {

                Ledger() {
                    warm();
                }

                @Cacheable("names")
                public void warm() {
                }
            }
            """;

        List<String> offences = SpringProxyRules.constructorInvocations(source);

        assertEquals(1, offences.size(), "the constructor runs before the proxy exists");
        assertTrue(offences.getFirst().contains("before the proxy exists"), "the offence says why");
    }

    @Test
    void separatesAnInstantiationFromTheConstructorDeclaration() {
        String source = """
            package com.example;

            class Ledger {

                @Transactional(readOnly = false, timeout = 5)
                public void post() {
                }

                public Ledger copy() {
                    Ledger other = new Ledger();
                    return other;
                }
            }
            """;

        assertEquals(
            List.of(), SpringProxyRules.constructorInvocations(source),
            "new Ledger() opens no constructor body to search"
        );
    }

    @Test
    void passesOverACallMadeOutsideAnyConstructor() {
        String source = """
            package com.example;

            class Ledger {

                @Async
                public void publish() {
                }

                public void later() {
                    publish();
                }
            }
            """;

        assertEquals(List.of(), SpringProxyRules.constructorInvocations(source), "this call is not a lifecycle one");
    }

    @Test
    void passesOverASourceDeclaringNoType() {
        assertEquals(List.of(), SpringProxyRules.constructorInvocations("@X"), "no type to read");
    }

    @Test
    void acceptsACallQualifiedBySomethingThatIsNotAnIdentifier() {
        String source = """
            package com.example;

            class Ledger {

                @Transactional(readOnly = false, timeout = 5)
                public void post() {
                }

                public void chain(Factory factory) {
                    factory.create().post();
                }
            }
            """;

        assertEquals(
            List.of(), SpringProxyRules.selfInvocations(source),
            "the call arrives on whatever create returned"
        );
    }

    @Test
    void passesOverACallSittingOutsideTheConstructorOfATypeThatHasOne() {
        String source = """
            package com.example;

            class Ledger {

                private final String name;

                Ledger(String name) {
                    this.name = name;
                }

                @Cacheable("names")
                public void warm() {
                }

                public void later() {
                    warm();
                }
            }
            """;

        assertEquals(
            List.of(), SpringProxyRules.constructorInvocations(source),
            "the constructor is present and this call is not inside it"
        );
    }
}
