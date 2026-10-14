package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.LicenseItem;
import org.cyclonedx.util.LicenseResolver;

/**
 * Preserves supplier declarations as SPDX expressions or identifiers when recognized, otherwise as names.
 */
@UtilityClass
final class ReportLicenseChoice {

    static List<Map<String, Object>> from(List<String> declarations) {
        List<String> present = declarations.stream().filter(declared -> !declared.isBlank()).toList();
        return present.stream().map(declared -> choice(declared, present.size() == 1)).toList();
    }

    private static Map<String, Object> choice(String declared, boolean expressionAllowed) {
        Optional<LicenseChoice> resolved = Optional.ofNullable(LicenseResolver.resolve(declared, false));
        List<LicenseItem> items = resolved.map(LicenseChoice::getItems).orElseGet(List::of);
        if (expressionAllowed && ReportSpdxSyntax.expression(declared)) {
            return Map.of("expression", declared);
        }
        List<License> licenses = items.stream().map(LicenseItem::getLicense)
            .flatMap(value -> Optional.ofNullable(value).stream()).toList();
        boolean identifier = licenses.stream().map(License::getId).anyMatch(declared::equals);
        return Map.of("license", Map.of(identifier ? "id" : "name", declared));
    }
}
