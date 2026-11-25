package eu.ciechanowiec.airness.maven;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

/**
 * Reads real PDF destinations rather than estimating page numbers from screen geometry.
 */
@UtilityClass
final class ReportPdfContents {

    private static final Pattern NUMBER = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\.(?![0-9])");

    @SneakyThrows
    static Map<String, Integer> read(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDOutlineNode outline = Optional.ofNullable(document.getDocumentCatalog().getDocumentOutline())
                .orElseThrow(() -> new IllegalStateException("The printed report has no PDF outline"));
            // A section's bookmark reads "1.Vulnerabilities" and a subsection's "1.1.All vulnerabilities". The
            // printed contents list both, by the number before the title.
            return children(outline).map(item -> Map.entry(NUMBER.matcher(item.getTitle()), item))
                .filter(entry -> entry.getKey().find()).collect(
                    Collectors.toUnmodifiableMap(
                        entry -> "section-" + entry.getKey().group(1), entry -> page(document, entry.getValue())
                    )
                );
        }
    }

    private static Stream<PDOutlineItem> children(PDOutlineNode parent) {
        return StreamSupport.stream(parent.children().spliterator(), false)
            .flatMap(item -> Stream.concat(Stream.of(item), children(item)));
    }

    @SneakyThrows
    private static int page(PDDocument document, PDOutlineItem item) {
        PDPage page = Optional.ofNullable(item.findDestinationPage(document))
            .orElseThrow(() -> new IllegalStateException("PDF bookmark has no destination: " + item.getTitle()));
        return document.getPages().indexOf(page) + 1;
    }
}
