package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringTransactionalEventCheckstyleTest {

    private static final String RULE = "AirnessSpringAfterCommitListenerRunsItsOwnTransaction";
    private static final String MARKER = "REQUIRES_NEW";

    @Test
    void reportsAnAfterCommitListenerThatJoinsTheCommittedTransaction(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
                @Transactional
                void deliver(Posted posted) {}
            }
            """;

        assertEquals(1, findings(directory, source), "the listener joins a transaction that has ended");
    }

    @Test
    void reportsAListenerThatInheritsTheAfterCommitPhase(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener
                @Transactional(readOnly = false)
                void deliver(Posted posted) {}
            }
            """;

        assertEquals(1, findings(directory, source), "no phase is the phase after the commit");
    }

    @Test
    void reportsEveryPhaseThatFollowsTheCommit(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
                @Transactional
                void undo(Posted posted) {}
                @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
                @Transactional(propagation = Propagation.REQUIRED)
                void close(Posted posted) {}
            }
            """;

        assertEquals(2, findings(directory, source), "rollback and completion also run after the end");
    }

    @Test
    void acceptsAnAfterCommitListenerThatOpensItsOwnTransaction(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
                @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false, timeout = 30)
                void deliver(Posted posted) {}
            }
            """;

        assertEquals(0, findings(directory, source), "the listener commits a transaction of its own");
    }

    @Test
    void acceptsTheBareConstantSpellings(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = AFTER_COMMIT)
                @Transactional(propagation = REQUIRES_NEW)
                void deliver(Posted posted) {}
                @TransactionalEventListener(phase = BEFORE_COMMIT)
                @Transactional
                void prepare(Posted posted) {}
            }
            """;

        assertEquals(0, findings(directory, source), "a static import spells the same constants");
    }

    @Test
    void acceptsABeforeCommitListenerInsideThePublisherTransaction(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
                @Transactional
                void prepare(Posted posted) {}
            }
            """;

        assertEquals(0, findings(directory, source), "before the commit there is a transaction to join");
    }

    @Test
    void passesOverAnAfterCommitListenerThatWritesNothing(@TempDir Path directory) {
        String source = """
            class Listeners {
                @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
                void notify(Posted posted) {}
                @Transactional
                void unrelated() {}
            }
            """;

        assertEquals(0, findings(directory, source), "only a listener carrying both annotations is scoped");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE, MARKER);
    }
}
