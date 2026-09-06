package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How the guard evidence one build wrote is worded as something a reader can act on.
 *
 * <p>Nothing here starts a context. What the container decided arrives as lines, and what is asked of
 * this rule is only that it tells the two ways a guard fails to resolve apart and names both halves of
 * each one.
 */
class SpringGuardBeanRulesTest {

    private static final String UNKNOWN_BEAN = "guard-bean clearence com.example.Ledger#post";

    private static final String UNKNOWN_CALL = "guard-call clearance.grantd com.example.Ledger#post";

    private static final String OPEN_MAPPING = "open GET /ledger";

    @Test
    void readsAGuardNamingABeanTheApplicationDoesNotDeclare() {
        assertEquals(1, SpringGuardBeanRules.unknownBeans(List.of(UNKNOWN_BEAN)).size());
    }

    @Test
    void namesTheBeanThatWasAskedForAndTheDeclarationThatAskedForIt() {
        String reported = SpringGuardBeanRules.unknownBeans(List.of(UNKNOWN_BEAN)).getFirst();
        assertTrue(
            reported.startsWith("com.example.Ledger#post") && reported.contains("@clearence"),
            "an offence leads with where to go and says what was written there"
        );
    }

    @Test
    void readsAGuardCallingAMethodTheBeanDoesNotAnswerTo() {
        String reported = SpringGuardBeanRules.unknownCalls(List.of(UNKNOWN_CALL)).getFirst();
        assertTrue(reported.contains("@clearance.grantd"), "the whole reference is quoted back");
    }

    @Test
    void tellsTheTwoWaysAGuardFailsToResolveApart() {
        List<String> lines = List.of(UNKNOWN_BEAN, UNKNOWN_CALL);
        assertEquals(
            List.of(1, 1),
            List.of(
                SpringGuardBeanRules.unknownBeans(lines).size(), SpringGuardBeanRules.unknownCalls(lines).size()
            ),
            "each rule reads its own family and leaves the other one alone"
        );
    }

    @Test
    void leavesEveryOtherKindOfEvidenceAlone() {
        List<String> lines = List.of(OPEN_MAPPING, "com.example.Application");
        assertTrue(
            SpringGuardBeanRules.unknownBeans(lines).isEmpty() && SpringGuardBeanRules.unknownCalls(lines).isEmpty(),
            "the evidence file carries several families and each rule reads one of them"
        );
    }

    @Test
    void readsOneGuardWrittenTwiceAsOneOffence() {
        assertEquals(1, SpringGuardBeanRules.unknownBeans(List.of(UNKNOWN_BEAN, UNKNOWN_BEAN)).size());
    }

    @Test
    void dropsALineThatNamesNoDeclarationAtAll() {
        assertTrue(
            SpringGuardBeanRules.unknownBeans(List.of("guard-bean clearence")).isEmpty(),
            "an offence naming nowhere to go is an offence nobody can act on"
        );
    }

    @Test
    void tellsAReaderWhatTheGuardDoesInsteadOfDeciding() {
        String reported = SpringGuardBeanRules.unknownCalls(List.of(UNKNOWN_CALL)).getFirst();
        assertTrue(
            reported.contains("answers nobody"),
            "the cost of the defect is stated, since a guard that raises is not a guard that refuses"
        );
    }
}
