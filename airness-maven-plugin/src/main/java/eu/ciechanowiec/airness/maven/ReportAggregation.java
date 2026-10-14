package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Deduplicates coordinates while preserving every module's dependency use and notice attribution.
 */
@UtilityClass
final class ReportAggregation {

    static List<ReportPackage> packages(Collection<ReportModule> modules) {
        return modules.stream().flatMap(module -> module.packages().stream())
            .collect(Collectors.groupingBy(ReportPackage::coordinate)).values().stream()
            .map(ReportAggregation::merge).sorted(Comparator.comparing(ReportPackage::coordinate)).toList();
    }

    private static ReportPackage merge(List<ReportPackage> entries) {
        ReportPackage first = entries.getFirst();
        List<List<String>> licenses = entries.stream().map(ReportPackage::licenses).distinct().toList();
        if (licenses.size() != 1) {
            throw new IllegalStateException("Conflicting license evidence for " + first.coordinate());
        }
        List<ReportUse> uses = entries.stream().flatMap(entry -> entry.uses().stream()).distinct()
            .sorted(Comparator.comparing(ReportUse::module).thenComparing(ReportUse::scope)).toList();
        Map<String, String> notices = entries.stream().flatMap(entry -> entry.notices().entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportAggregation::same));
        return new ReportPackage(first.coordinate(), first.licenses(), uses, notices);
    }

    private static String same(String first, String second) {
        if (!first.equals(second)) {
            throw new IllegalStateException("One resolved artifact carries conflicting license texts");
        }
        return first;
    }
}
