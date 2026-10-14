package eu.ciechanowiec.airness.maven;

import java.util.Map;
import java.util.Optional;
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

    @SneakyThrows
    static Map<String, Integer> read(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDOutlineNode outline = Optional.ofNullable(document.getDocumentCatalog().getDocumentOutline())
                .orElseThrow(() -> new IllegalStateException("The printed report has no PDF outline"));
            return children(outline).filter(item -> item.getTitle().matches("^[0-9]+\\..*"))
                .collect(
                    Collectors.toUnmodifiableMap(
                        item -> "section-" + item.getTitle().split("\\.", -1)[0],
                        item -> page(document, item)
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
