package eu.ciechanowiec.airness.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * An immutable snapshot of Maven's effective plugin configuration.
 */
final class InputConfiguration {

    private final Xpp3Dom content;

    InputConfiguration(Optional<Object> supplied) {
        this.content = supplied.map(InputConfiguration::xml).map(Xpp3Dom::new)
            .orElseGet(() -> new Xpp3Dom("configuration"));
    }

    InputConfiguration merged(Optional<Object> dominant) {
        Xpp3Dom override = dominant.map(InputConfiguration::xml).map(Xpp3Dom::new)
            .orElseGet(() -> new Xpp3Dom("configuration"));
        return new InputConfiguration(Optional.of(Xpp3Dom.mergeXpp3Dom(override, new Xpp3Dom(this.content))));
    }

    List<String> values(String name) {
        return this.children(name).stream().flatMap(child -> Arrays.stream(child.content.getChildren()))
            .map(child -> Optional.ofNullable(child.getValue()).orElseThrow())
            .map(String::strip).toList();
    }

    Optional<String> value(String name) {
        return Optional.ofNullable(this.content.getChild(name)).map(Xpp3Dom::getValue);
    }

    List<InputConfiguration> children(String name) {
        return Arrays.stream(this.content.getChildren(name))
            .map(child -> new InputConfiguration(Optional.of(child))).toList();
    }

    boolean defaultExcludes() {
        String declared = this.value("addDefaultExcludes").orElse("true");
        boolean known = List.of("true", "false").contains(declared);
        if (!known) {
            throw new IllegalStateException("Cannot resolve Maven addDefaultExcludes: " + declared);
        }
        return Boolean.parseBoolean(declared);
    }

    private static Xpp3Dom xml(Object supplied) {
        if (!(supplied instanceof Xpp3Dom node)) {
            throw new IllegalStateException("Maven input configuration is not XML");
        }
        return node;
    }
}
