package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import org.ec4j.core.ResourceProperties;
import org.ec4j.core.model.Property;
import org.ec4j.core.model.PropertyType;
import org.ec4j.core.model.PropertyType.IndentStyleValue;

/**
 * What the {@code .editorconfig} files of a working tree declare about the indentation of one file.
 *
 * <p>A property no section declares, or one a section resets with {@code unset}, is absent, and a file with
 * nothing declared has nothing to verify under it. {@code indent_size = tab} is absent as a size too: it
 * defers to a tab width, which names no number of columns a nesting step is.
 */
final class IndentationSettings {

    private final ResourceProperties properties;

    /**
     * Holds the properties ec4j resolved for one file.
     *
     * @param properties every property the sections matching the file declare
     */
    IndentationSettings(ResourceProperties properties) {
        this.properties = properties;
    }

    /**
     * The kind of whitespace a line indents with.
     *
     * @return the declared {@code indent_style}, or nothing when no section declares a valid one
     */
    Optional<IndentStyleValue> style() {
        return this.declared(PropertyType.indent_style);
    }

    /**
     * How many columns one nesting step is.
     *
     * @return the declared {@code indent_size}, or nothing when no section declares a number of columns
     */
    Optional<Integer> size() {
        return this.declared(PropertyType.indent_size);
    }

    private <T> Optional<T> declared(PropertyType<T> type) {
        return Optional.ofNullable(this.properties.getProperties().get(type.getName()))
            .filter(Property::isValid)
            .flatMap(property -> Optional.ofNullable(property.<T>getValueAs()));
    }
}
