package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.unbescape.html.HtmlEscape;

/**
 * Literal lookups in the same markup inventory the existing template checks read.
 */
public final class TemplateMessageIndex {

    private final MarkupScan scan;
    private final Path root;
    private final List<Path> resources;

    /**
     * Reads production resource roots without examining test templates.
     *
     * @param root      the repository root
     * @param resources the production resource roots
     */
    public TemplateMessageIndex(Path root, Collection<Path> resources) {
        this.root = root;
        this.resources = resources.stream().map(root::resolve).map(Path::normalize).toList();
        this.scan = new MarkupScan(root, resources);
    }

    /**
     * Answers distinct source locations in deterministic order.
     *
     * @return the literal references
     */
    public List<TemplateMessageReference> references() {
        List<TemplateMessageReference> references = this.scan.gathered(
            (path, found) -> new Messages(path, this.resource(path), found)
        );
        return references.stream().distinct().sorted(Comparator.comparing(TemplateMessageReference::encoded)).toList();
    }

    private String resource(Path named) {
        Path file = this.root.resolve(named).normalize();
        Path directory = this.resources.stream().filter(file::startsWith)
            .max(Comparator.comparingInt(Path::getNameCount)).orElseThrow();
        return directory.relativize(file).toString().replace('\\', '/');
    }

    private static final class Messages implements MarkupElement {

        private final Path named;
        private final String resource;
        private final Collection<TemplateMessageReference> found;

        private Messages(Path named, String resource, Collection<TemplateMessageReference> found) {
            this.named = named;
            this.resource = resource;
            this.found = found;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            String location = "%s:%d:%d".formatted(this.named, line, column);
            attributes.entrySet().stream().filter(attribute -> processing(attribute.getKey()))
                .flatMap(attribute -> TemplateMessageNames.in(HtmlEscape.unescapeHtml(attribute.getValue())).stream())
                .map(key -> new TemplateMessageReference(this.resource, location, key)).forEach(this.found::add);
        }

        private static boolean processing(String name) {
            String attribute = name.toLowerCase(Locale.ROOT);
            boolean dialect = attribute.startsWith("th:") || attribute.startsWith("data-th-");
            return dialect && !TemplateFragmentCheck.declares(attribute)
                && !List.of("th:ref", "data-th-ref", "th:inline", "data-th-inline").contains(attribute);
        }
    }
}
