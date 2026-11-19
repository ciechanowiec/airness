package com.example.starter.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the first page of a PDF as a picture, which the renditions of the file are made from, so a PDF is
 * shown by its first page wherever an image would be. The document is read from a file rather than from
 * memory, so a large one is not held whole. A document that cannot be read, such as one locked with a
 * password, has no picture and is shown by its name alone.
 */
@UtilityClass
final class PdfPages {

    private static final Logger LOG = LoggerFactory.getLogger(PdfPages.class);

    private static final float WIDTH = 1600;

    // A page is drawn no taller than this, so a page box of any proportions decodes into a bounded picture.
    private static final float HEIGHT = 4000;

    /**
     * Draws the first page of the PDF in the given file as a PNG about 1600 pixels wide and never taller than
     * 4000.
     *
     * @param pdf the file holding the document
     * @return the picture of its first page, and nothing where the document cannot be read
     */
    static Optional<byte[]> firstPage(Path pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages() == 0 ? Optional.empty() : drawn(document);
        } catch (IOException exception) {
            LOG.warn("The first page of a stored PDF could not be drawn: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    // The scale fits the page into 1600 by 4000 pixels whatever its box says, and a box with no area is drawn
    // not at all, so no document can make the drawing allocate more than that.
    private static Optional<byte[]> drawn(PDDocument document) throws IOException {
        PDRectangle box = document.getPage(0).getMediaBox();
        if (!(box.getWidth() > 0 && box.getHeight() > 0)) {
            return Optional.empty();
        }
        float scale = Math.min(WIDTH / box.getWidth(), HEIGHT / box.getHeight());
        BufferedImage page = new PDFRenderer(document).renderImage(0, scale);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page, "png", png);
        return Optional.of(png.toByteArray());
    }
}
