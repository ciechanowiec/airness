package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;

/**
 * Refuses a version property that nothing in the pom declaring it reads.
 *
 * <p>A property moves a resolved version only where a version reads it. The Spring platform reaches a
 * project as a bill of materials the parent imports rather than inherits, and an imported bill of
 * materials resolves its own properties, so a child that writes {@code tomcat.version} beside its other
 * settings moves nothing at all. The build stays green and the pom carries a pin that is not there,
 * which is the one failure a pin must never have. Two projects met it, each found it by hand, and each
 * repaired it the same way: the embedded Tomcat artifacts managed in a {@code dependencyManagement}
 * block of their own, every one of them versioned from the property, which is what turns the property
 * into the pin it claimed to be.
 *
 * <p>The rule holds because this parent imports. Under a parent that inherits a platform, a child
 * declaring {@code tomcat.version} is read by the ancestor rather than by itself, and refusing it would
 * refuse the documented idiom. That is also why the names {@link ManagedVersions} owns are passed over
 * here: it refuses each of them already with a message that names the owner rather than a repair, and
 * the root pom of this harness declares one of them for the parent below it to read, which is a use no
 * rule reading one file can see.
 *
 * <p>What counts as a use is taken off {@link PomPropertyOrder}, so the two rules cannot come to
 * disagree about it. They part in one place: a property read by another property counts here, because
 * Maven resolves a property value into a property value, while the order rule passes over such a read
 * so that a block is sorted by what the pom outside it asks for.
 */
@UtilityClass
final class VersionProperties {

    private static final String VERSION_SUFFIX = ".version";

    /**
     * Reads every property block of a raw project file.
     *
     * @param root the project element
     * @return one problem per declared version property that nothing in the same pom reads
     */
    static Stream<String> problems(Element root) {
        List<String> declared = DeclaredCoordinates.propertyBlocks(root)
            .flatMap(block -> PomPropertyOrder.propertyNames(block).stream())
            .filter(VersionProperties::governed)
            .distinct()
            .toList();
        // Every element is a reference site, the property blocks included, because Maven resolves a
        // property value into a property value. The order rule excludes them for a reason of its own.
        Set<String> referenced = PomPropertyOrder.firstUses(root, declared, List.of()).keySet();
        return declared.stream()
            .filter(name -> !referenced.contains(name))
            .map(VersionProperties::problem);
    }

    private static boolean governed(String property) {
        return property.endsWith(VERSION_SUFFIX)
            && !ManagedVersions.protectedProperties().contains(property);
    }

    private static String problem(String property) {
        return "Reference ${" + property + "} from a <version> in this pom or remove the property"
            + "; a version property nothing in the pom reads changes no version the build resolves";
    }
}
