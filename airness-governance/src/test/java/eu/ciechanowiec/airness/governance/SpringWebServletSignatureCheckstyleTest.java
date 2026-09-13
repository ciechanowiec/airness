package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringWebServletSignatureCheckstyleTest {

    private static final String REQUEST = "AirnessSpringWebSignatureIsNotRequestTyped";
    private static final String RESPONSE = "AirnessSpringWebSignatureIsNotResponseTyped";
    private static final String SESSION = "AirnessSpringWebSignatureIsNotSessionTyped";

    @Test
    void reportsAServletRequestInAHandlerSignature(@TempDir Path directory) {
        String source = """
            @RestController
            class Web {
                @GetMapping void read(HttpServletRequest request) {}
            }
            """;

        assertEquals(1, findings(directory, source, REQUEST, "HttpServletRequest"), "the value is not declared");
    }

    @Test
    void acceptsADeclaredRequestValue(@TempDir Path directory) {
        String source = """
            @RestController
            class Web {
                @GetMapping void read(@RequestParam(name = "q", required = false) String query) {}
            }
            """;

        assertEquals(0, findings(directory, source, REQUEST, "HttpServletRequest"), "the value is named");
    }

    @Test
    void reportsAServletResponseInAHandlerSignature(@TempDir Path directory) {
        String source = """
            @RestController
            class Web {
                @GetMapping void read(HttpServletResponse response) {}
            }
            """;

        assertEquals(1, findings(directory, source, RESPONSE, "HttpServletResponse"), "the answer leaves elsewhere");
    }

    @Test
    void acceptsAnAnsweredResponse(@TempDir Path directory) {
        String source = """
            @RestController
            class Web {
                @GetMapping ResponseEntity<String> read() {
                    return ResponseEntity.ok("read");
                }
            }
            """;

        assertEquals(0, findings(directory, source, RESPONSE, "HttpServletResponse"), "the answer is the return");
    }

    @Test
    void reportsAServletSessionInAHandlerSignature(@TempDir Path directory) {
        String source = """
            @Controller
            class Web {
                @GetMapping void read(HttpSession session) {}
            }
            """;

        assertEquals(1, findings(directory, source, SESSION, "HttpSession"), "what is read is hidden");
    }

    @Test
    void acceptsASessionValueDeclaredBesideTheHandler(@TempDir Path directory) {
        String source = """
            @Controller
            @SessionAttributes(names = "opened")
            class Web {
                @GetMapping void read(@SessionAttribute(name = "opened", required = false) String opened) {}
            }
            """;

        assertEquals(0, findings(directory, source, SESSION, "HttpSession"), "the value is named");
    }

    @Test
    void passesOverServletTypesOutsideAController(@TempDir Path directory) {
        String source = """
            class Filtering {
                void before(HttpServletRequest request, HttpServletResponse response, HttpSession session) {}
            }
            """;
        List<Integer> reported = List.of(
            findings(directory, source, REQUEST, "HttpServletRequest"),
            findings(directory, source, RESPONSE, "HttpServletResponse"),
            findings(directory, source, SESSION, "HttpSession")
        );

        assertEquals(List.of(0, 0, 0), reported, "a filter is a different thing and keeps its servlet types");
    }

    @Test
    void keepsTheThreeServletTypesToTheirOwnOwners(@TempDir Path directory) {
        String source = """
            @RestController
            class Web {
                @GetMapping void read(HttpServletRequest request, HttpServletResponse response) {}
            }
            """;
        List<Integer> reported = List.of(
            findings(directory, source, REQUEST, "HttpServletRequest"),
            findings(directory, source, RESPONSE, "HttpServletResponse"),
            findings(directory, source, SESSION, "HttpSession")
        );

        assertEquals(List.of(1, 1, 0), reported, "each type is reported by the one rule that owns it");
    }

    private static int findings(Path directory, String source, String rule, String marker) {
        return CheckstyleRule.findings(directory, source, rule, marker);
    }
}
