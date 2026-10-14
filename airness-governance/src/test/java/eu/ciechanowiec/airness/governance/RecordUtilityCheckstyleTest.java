package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordUtilityCheckstyleTest {

    private static final String RULE = "AirnessRecordUtilityIsUtilityClass";

    @Test
    void reportsAComponentlessRecordWithOnlyStaticMethods(@TempDir Path directory) {
        String source = """
            record Texts() {
                static String trim(String text) { return text.strip(); }
                static boolean blank(String text) { return text.isBlank(); }
            }
            """;

        assertEquals(1, findings(directory, source), "a record that carries nothing is a namespace for statics");
    }

    @Test
    void reportsANestedComponentlessRecordWithOnlyStaticMethods(@TempDir Path directory) {
        String source = """
            final class Outer {
                record Helpers() {
                    static int twice(int value) { return value * 2; }
                }
            }
            """;

        assertEquals(1, findings(directory, source), "a nested record is the same construct");
    }

    @Test
    void reportsAnEmptyComponentlessRecord(@TempDir Path directory) {
        String source = """
            record Empty() {}
            """;

        assertEquals(1, findings(directory, source), "an empty record declares nothing that is not static");
    }

    @Test
    void passesOverARecordWithAComponent(@TempDir Path directory) {
        String source = """
            record Name(String value) {
                static Name of(String value) { return new Name(value); }
            }
            """;

        assertEquals(0, findings(directory, source), "a component is a value the record carries");
    }

    @Test
    void passesOverAComponentlessRecordThatImplementsAnInterface(@TempDir Path directory) {
        String source = """
            record Marker() implements Comparable<Marker> {
                public int compareTo(Marker other) { return 0; }
            }
            record Token() implements java.io.Serializable {
                static Token create() { return new Token(); }
            }
            """;

        assertEquals(0, findings(directory, source), "an implemented interface is a contract the instance fulfils");
    }

    @Test
    void reportsAComponentlessRecordWithInstanceMethods(@TempDir Path directory) {
        String source = """
            record Clock() {
                long now() { return System.nanoTime(); }
                static Clock system() { return new Clock(); }
            }
            """;

        assertEquals(1, findings(directory, source), "an instance method of an empty record is a static call");
    }

    @Test
    void reportsANestedComponentlessRecordWithAnInstanceMethod(@TempDir Path directory) {
        String source = """
            final class Outer {
                record Facade() {
                    String greet(String name) { return "Hello " + name; }
                }
            }
            """;

        assertEquals(1, findings(directory, source), "a nested facade record is the same construct");
    }

    @Test
    void reportsAGenericComponentlessRecord(@TempDir Path directory) {
        String source = """
            record Box<T>() {
                T none() { return null; }
            }
            """;

        assertEquals(1, findings(directory, source), "a type parameter gives the record no state of its own");
    }

    @Test
    void passesOverAGenericRecordWithAComponent(@TempDir Path directory) {
        String source = """
            record Box<T>(T value) {
                T get() { return value; }
            }
            """;

        assertEquals(0, findings(directory, source), "a generic component is still a value the record carries");
    }

    @Test
    void passesOverAComponentlessRecordWithInstanceMethodsThatImplementsAnInterface(@TempDir Path directory) {
        String source = """
            record Greeter() implements java.util.function.UnaryOperator<String> {
                public String apply(String name) { return "Hello " + name; }
            }
            """;

        assertEquals(0, findings(directory, source), "an interface needs instances for polymorphic dispatch");
    }

    @Test
    void leavesAUtilityClassToTheClassRule(@TempDir Path directory) {
        String source = """
            final class Texts {
                private Texts() {}
                static String trim(String text) { return text.strip(); }
            }
            """;

        assertEquals(0, findings(directory, source), "a class utility is answered by the class rule, not this one");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
