package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The security expressions that name something the ready application could not resolve.
 *
 * <p>A guard reads as a decision and is a string until somebody arrives. The parameters it names are
 * checked where they are written, beside the parameter list, but the bean it calls is checked by
 * nothing a reader of the source could consult: which beans exist is settled by the classes of the
 * module together with every auto-configuration on the classpath. The ready context settles it, and
 * what it settled arrives here as evidence.
 *
 * <p>A wrong name is not a guard that admits the wrong caller. It is a guard that admits nobody and
 * raises instead, so the endpoint behind it answers an error to everybody who reaches it and answers
 * nothing at all to a suite that never did. The annotation still reads as the rule somebody meant,
 * which is why the failure survives review by looking exactly like the guards around it.
 *
 * <p>Two shapes are reported. A bean the application declares nothing under, which is the name itself
 * being wrong, and a method that bean does not answer to, which is the call being wrong. They are
 * told apart because the repair differs and because a reader who is told only that the guard is
 * unresolvable has to work out which half to look at.
 */
@UtilityClass
final class SpringGuardBeanRules {

    private static final String UNKNOWN_BEAN = "guard-bean ";

    private static final String UNKNOWN_CALL = "guard-call ";

    private static final String SEPARATOR = " ";

    /**
     * Every guard naming a bean the application declares nothing under.
     *
     * @param recorded the evidence lines this build wrote
     * @return one offence per such guard
     */
    static List<String> unknownBeans(Collection<String> recorded) {
        return offences(recorded, UNKNOWN_BEAN, SpringGuardBeanRules::bean);
    }

    /**
     * Every guard calling a method that the bean it names does not answer to.
     *
     * @param recorded the evidence lines this build wrote
     * @return one offence per such guard
     */
    static List<String> unknownCalls(Collection<String> recorded) {
        return offences(recorded, UNKNOWN_CALL, SpringGuardBeanRules::call);
    }

    // One family of evidence line, read and worded. The prefix says which family, and what follows it
    // is the reference and the declaration that wrote it, in that order.
    private static List<String> offences(Collection<String> recorded, String prefix, Wording wording) {
        return recorded.stream()
            .filter(line -> line.startsWith(prefix))
            .map(line -> line.substring(prefix.length()))
            .map(line -> stated(line, wording))
            .flatMap(Optional::stream)
            .distinct()
            .sorted()
            .toList();
    }

    // A line the writer of the evidence and the reader of it disagree about is dropped rather than
    // reported half read, because an offence naming no declaration is an offence nobody can act on.
    private static Optional<String> stated(String line, Wording wording) {
        int split = line.indexOf(SEPARATOR);
        return split > 0
            ? Optional.of(wording.of(line.substring(0, split), line.substring(split + 1)))
            : Optional.empty();
    }

    private static String bean(String reference, String where) {
        return where + ": the security expression calls @" + reference
            + ", and this application declares no bean under that name."
            + " The guard raises rather than decides, so the method behind it answers nobody."
            + " Name a bean the application declares";
    }

    private static String call(String reference, String where) {
        return where + ": the security expression calls @" + reference
            + ", and the bean it names answers to no method of that name."
            + " The guard raises rather than decides, so the method behind it answers nobody."
            + " Call a method that bean declares";
    }

    /**
     * How one family of evidence line is worded as an offence.
     */
    @FunctionalInterface
    private interface Wording {

        /**
         * Words one unresolvable reference.
         *
         * @param reference what the expression named
         * @param where     the declaration the expression guards
         * @return the offence
         */
        String of(String reference, String where);
    }
}
