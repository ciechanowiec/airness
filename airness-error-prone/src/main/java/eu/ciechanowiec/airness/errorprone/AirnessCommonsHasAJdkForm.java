package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Calls the JDK where a helper library's static method only restates it.
 *
 * <p>Each entry names a helper class, a method and its number of parameters, and the JDK form that says
 * the same. Overloads that add behaviour the JDK lacks keep their own arity and are not listed. A
 * {@link Deprecated} helper, such as a commons-io {@code Charsets} constant, is already refused by the compiler.
 */
@BugPattern(
    summary = "Use the JDK form rather than the helper library method that restates it",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessCommonsHasAJdkForm extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Set<String> STRING_UTILS = Set.of(
        "org.apache.commons.lang3.StringUtils", "org.apache.maven.shared.utils.StringUtils",
        "org.codehaus.plexus.util.StringUtils"
    );

    private static final Map<String, String> STRING_FORMS = Map.ofEntries(
        Map.entry("isBlank/1", "value == null || value.isBlank()"),
        Map.entry("isNotBlank/1", "value != null && !value.isBlank()"),
        Map.entry("isEmpty/1", "value == null || value.isEmpty()"),
        Map.entry("isNotEmpty/1", "value != null && !value.isEmpty()"),
        Map.entry("defaultIfBlank/2", "a conditional on isBlank()"),
        Map.entry("abbreviate/2", "a substring with an ellipsis"),
        Map.entry("capitalize/1", "toUpperCase of the first character"),
        Map.entry("defaultString/1", "Objects.toString(value, \"\")"),
        Map.entry("defaultString/2", "Objects.toString(value, fallback)"),
        Map.entry("deleteWhitespace/1", "replaceAll(\"\\\\s+\", \"\")"),
        Map.entry("equalsIgnoreCase/2", "String.equalsIgnoreCase"),
        Map.entry("lowerCase/1", "String.toLowerCase"),
        Map.entry("upperCase/1", "String.toUpperCase"),
        Map.entry("removeEnd/2", "a substring after endsWith"),
        Map.entry("replace/3", "String.replace"),
        Map.entry("reverse/1", "new StringBuilder(value).reverse()"),
        Map.entry("split/1", "String.split"),
        Map.entry("split/2", "String.split"),
        Map.entry("join/2", "String.join"),
        Map.entry("strip/1", "String.strip"),
        Map.entry("trim/1", "String.trim"),
        Map.entry("trimToEmpty/1", "Objects.toString(value, \"\").trim()"),
        Map.entry("trimToNull/1", "a trim followed by an isEmpty check")
    );

    private static final Map<String, String> FILE_FORMS = Map.of(
        "readFileToByteArray/1", "Files.readAllBytes",
        "readLines/2", "Files.readAllLines",
        "writeStringToFile/3", "Files.writeString",
        "writeStringToFile/4", "Files.writeString",
        "write/4", "Files.writeString",
        "getFile/1", "new File",
        "deleteDirectory/1", "Files.walk with Files.delete",
        "forceDelete/1", "Files.delete"
    );

    private static final Set<String> BASE64 = Set.of(
        "encodeBase64", "decodeBase64", "encodeBase64String", "encodeBase64URLSafe", "encodeBase64URLSafeString"
    );

    private static final Map<String, Map<String, String>> HELPERS = Map.of(
        "org.apache.commons.io.FileUtils", FILE_FORMS
    );

    private static final String BASE64_OWNER = "org.apache.commons.codec.binary.Base64";

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        Symbol.MethodSymbol method = ASTHelpers.getSymbol(call);
        if (!method.isStatic()) {
            return Description.NO_MATCH;
        }
        String owner = method.owner.getQualifiedName().toString();
        String key = method.getSimpleName() + "/" + method.getParameters().size();
        return form(owner, key, method.getSimpleName().toString())
            .map(form -> this.buildDescription(call).setMessage("Use " + form + " from the JDK instead").build())
            .orElse(Description.NO_MATCH);
    }

    private static Optional<String> form(String owner, String key, String name) {
        Map<String, String> forms = STRING_UTILS.contains(owner) ? STRING_FORMS : HELPERS.getOrDefault(owner, Map.of());
        boolean codec = BASE64_OWNER.equals(owner) && BASE64.contains(name);
        return Optional.ofNullable(forms.get(key)).or(() -> codec ? Optional.of("java.util.Base64") : Optional.empty());
    }
}
