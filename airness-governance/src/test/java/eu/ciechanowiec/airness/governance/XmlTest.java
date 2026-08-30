package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class XmlTest {

    @Test
    void returnsEmptyTextWhenTheChildIsAbsent() {
        Element root = Xml.parse("<project/>").getDocumentElement();

        assertEquals("", Xml.idTextOrEmpty(root), "a missing optional element contributes no text");
    }
}
