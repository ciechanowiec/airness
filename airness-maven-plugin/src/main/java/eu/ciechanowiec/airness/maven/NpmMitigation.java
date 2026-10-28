package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One project's explanation of one npm package's advisory, never a package-wide waiver.
 *
 * @param packageName the exact npm package name
 * @param identity    a literal canonical advisory identifier or alias
 * @param mitigation  the explanation, removal plan and date retained in the report
 */
record NpmMitigation(String packageName, String identity, ReportMitigation mitigation) {

    private static final Pattern PACKAGE = Pattern.compile("(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*");
    private static final Pattern IDENTITY = Pattern.compile("[A-Z][A-Za-z0-9-]+");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    static NpmMitigation of(JsonNode declaration) {
        String name = ReportJson.required(declaration, "package");
        String identity = ReportJson.required(declaration, "advisory");
        if (!PACKAGE.matcher(name).matches() || !IDENTITY.matcher(identity).matches()) {
            throw new IllegalStateException("An npm mitigation must name an exact package and a literal advisory");
        }
        String date = date(ReportJson.required(declaration, "addedOn"));
        return new NpmMitigation(
            name, identity, new ReportMitigation(
                ReportJson.required(declaration, "reason"), ReportJson.required(declaration, "mitigation"), date, false
            )
        );
    }

    private static String date(String date) {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(
                "An npm mitigation addedOn must be a calendar date in YYYY-MM-DD form", exception
            );
        }
        if (!DATE.matcher(date).matches()) {
            throw new IllegalStateException("An npm mitigation addedOn must use YYYY-MM-DD form");
        }
        return date;
    }

    boolean matches(OsvAdvisory finding) {
        String name = this.packageName.startsWith("@") ? "%40" + this.packageName.substring(1) : this.packageName;
        return finding.advisory().artifact().startsWith("pkg:npm/" + name + '@')
            && finding.identifiers().contains(this.identity);
    }

    ReportAdvisory apply(ReportAdvisory advisory) {
        return new ReportAdvisory(
            advisory.identity(), advisory.severity(), advisory.artifact(), advisory.modules(), advisory.description(),
            Optional.of(this.mitigation)
        );
    }

    String description() {
        return this.packageName + ' ' + this.identity;
    }
}
