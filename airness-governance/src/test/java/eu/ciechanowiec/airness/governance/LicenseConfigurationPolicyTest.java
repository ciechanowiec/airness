package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class LicenseConfigurationPolicyTest {

    @Test
    void parentNormalizesNetworkntsApacheLicenseName() {
        Element parent = Xml.parse(read(repository().resolve("airness-parent/pom.xml")))
            .getDocumentElement();
        boolean normalized = IntStream.range(0, parent.getElementsByTagName("licenseMerge").getLength())
            .mapToObj(index -> parent.getElementsByTagName("licenseMerge").item(index))
            .map(Node::getTextContent)
            .anyMatch(
                merge -> merge.startsWith("Apache-2.0|")
                    && merge.contains("|Apache License Version 2.0|")
            );
        assertTrue(normalized);
    }

    @SneakyThrows
    private static String read(Path path) {
        return Files.readString(path);
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }
}
