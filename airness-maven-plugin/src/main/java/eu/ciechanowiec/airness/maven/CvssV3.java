package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The qualitative severity a CVSS 3.0 or 3.1 base vector rates, computed by the specification's base equations.
 *
 * <p>An advisory database that publishes only a vector, as a distribution's tracker does, still rates the
 * advisory: the vector determines the base score, and the score the rating.
 */
@UtilityClass
final class CvssV3 {

    private static final List<String> PREFIXES = List.of("CVSS:3.0/", "CVSS:3.1/");
    private static final List<String> METRICS = List.of("AV", "AC", "PR", "UI", "S", "C", "I", "A");
    private static final Map<String, Double> ATTACK_VECTOR = Map.of("N", 0.85, "A", 0.62, "L", 0.55, "P", 0.2);
    private static final Map<String, Double> ATTACK_COMPLEXITY = Map.of("L", 0.77, "H", 0.44);
    private static final Map<String, Double> PRIVILEGES = Map.of("N", 0.85, "L", 0.62, "H", 0.27);
    private static final Map<String, Double> PRIVILEGES_CHANGED = Map.of("N", 0.85, "L", 0.68, "H", 0.5);
    private static final Map<String, Double> USER_INTERACTION = Map.of("N", 0.85, "R", 0.62);
    private static final Map<String, Double> IMPACT = Map.of("H", 0.56, "L", 0.22, "N", 0.0);
    private static final double EXPLOITABILITY = 8.22;
    private static final double UNCHANGED = 6.42;
    private static final double CHANGED = 7.52;
    private static final double CHANGED_OFFSET = 0.029;
    private static final double CHANGED_TAIL = 3.25;
    private static final double CHANGED_TAIL_OFFSET = 0.02;
    private static final int CHANGED_TAIL_POWER = 15;
    private static final double CHANGED_FACTOR = 1.08;
    private static final double MAXIMUM = 10.0;
    private static final double CRITICAL = 9.0;
    private static final double HIGH = 7.0;
    private static final double MEDIUM = 4.0;
    private static final double LOW = 0.1;
    private static final int PRECISION = 100_000;
    private static final int TENTH = 10_000;
    private static final double TENTHS = 10.0;
    private static final List<String> SCOPES = List.of("U", "C");
    // Each rating with the lowest score it starts at, highest first.
    private static final List<Map.Entry<Double, String>> RATINGS = List.of(
        Map.entry(CRITICAL, "CRITICAL"), Map.entry(HIGH, "HIGH"), Map.entry(MEDIUM, "MEDIUM"), Map.entry(LOW, "LOW")
    );
    private static final int USER = 3;
    private static final int CONFIDENTIALITY = 4;
    private static final int INTEGRITY = 5;
    private static final int AVAILABILITY = 6;

    /**
     * Rates a base vector.
     *
     * @param vector such as {@code CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H}
     * @return {@code CRITICAL}, {@code HIGH}, {@code MEDIUM}, {@code LOW} or {@code NONE}, or empty for a vector
     *         that is not a complete CVSS 3 base vector
     */
    static Optional<String> rating(String vector) {
        return score(vector).map(CvssV3::qualitative);
    }

    /**
     * The base score of a vector.
     *
     * @param vector a CVSS 3.0 or 3.1 vector
     * @return the base score, or empty for a vector that is not a complete CVSS 3 base vector
     */
    static Optional<Double> score(String vector) {
        return PREFIXES.stream().filter(vector::startsWith).findFirst()
            .map(prefix -> metrics(vector.substring(prefix.length())))
            .filter(metrics -> METRICS.stream().allMatch(metrics::containsKey)).flatMap(CvssV3::base);
    }

    // Each metric of the vector by its name. A metric written twice counts as written first.
    private static Map<String, String> metrics(String body) {
        return Stream.of(body.split("/", -1)).map(part -> part.split(":", -1)).filter(pair -> pair.length == 2)
            .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (first, _) -> first));
    }

    private static Optional<Double> base(Map<String, String> metrics) {
        String scope = metrics.getOrDefault("S", "");
        boolean changed = "C".equals(scope);
        List<Optional<Double>> weights = List.of(
            value(ATTACK_VECTOR, metrics, "AV"), value(ATTACK_COMPLEXITY, metrics, "AC"),
            value(changed ? PRIVILEGES_CHANGED : PRIVILEGES, metrics, "PR"), value(USER_INTERACTION, metrics, "UI"),
            value(IMPACT, metrics, "C"), value(IMPACT, metrics, "I"), value(IMPACT, metrics, "A")
        );
        if (!SCOPES.contains(scope) || weights.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(computed(changed, weights.stream().mapToDouble(Optional::orElseThrow).toArray()));
    }

    // The base equations: the attack vector, complexity, privileges and interaction, then the three impacts.
    private static double computed(boolean changed, double... weights) {
        double severity = 1 - (1 - weights[CONFIDENTIALITY]) * (1 - weights[INTEGRITY]) * (1 - weights[AVAILABILITY]);
        double impact = changed
            ? CHANGED * (severity - CHANGED_OFFSET) - CHANGED_TAIL * Math.pow(
                severity - CHANGED_TAIL_OFFSET, CHANGED_TAIL_POWER
            )
            : UNCHANGED * severity;
        if (impact <= 0) {
            return 0.0;
        }
        double exploitability = EXPLOITABILITY * weights[0] * weights[1] * weights[2] * weights[USER];
        double base = changed ? CHANGED_FACTOR * (impact + exploitability) : impact + exploitability;
        return roundUp(Math.min(base, MAXIMUM));
    }

    private static Optional<Double> value(Map<String, Double> weights, Map<String, String> metrics, String name) {
        return Optional.ofNullable(weights.get(metrics.getOrDefault(name, "")));
    }

    // The specification's Roundup: the smallest number with one decimal that is at least the input, computed on
    // integers so a floating-point error never rounds a score up a tenth too far.
    private static double roundUp(double input) {
        long whole = Math.round(input * PRECISION);
        return whole % TENTH == 0 ? whole / (double) PRECISION : (Math.floorDiv(whole, TENTH) + 1) / TENTHS;
    }

    private static String qualitative(double score) {
        return RATINGS.stream().filter(rating -> score >= rating.getKey()).map(Map.Entry::getValue).findFirst()
            .orElse("NONE");
    }
}
