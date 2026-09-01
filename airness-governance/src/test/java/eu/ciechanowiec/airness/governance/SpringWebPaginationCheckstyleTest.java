package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringWebPaginationCheckstyleTest {

    private static final String PAGEABLE = "AirnessSpringWebPageableHasDefaults";
    private static final String SORT = "AirnessSpringWebSortHasDefaults";
    private static final String QUALIFIED = "AirnessSpringWebPaginationIsQualified";

    @Test
    void reportsPageableDefaultsOnEverySupportedMapping(@TempDir Path directory) {
        String source = """
            class Pages {
                @RequestMapping void first(Pageable page) {}
                @GetMapping void second(Pageable page) {}
                @PostMapping void third(Pageable page) {}
                @PutMapping void fourth(Pageable page) {}
                @DeleteMapping void fifth(Pageable page) {}
                @PatchMapping void sixth(Pageable page) {}
            }
            """;

        assertEquals(6, findings(directory, source, PAGEABLE, "PageableDefault"), "all handler forms are scoped");
    }

    @Test
    void acceptsExplicitPageableDefaults(@TempDir Path directory) {
        String source = """
            class Pages {
                @GetMapping
                void read(
                    @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.ASC) Pageable page
                ) {}
            }
            """;

        assertEquals(0, findings(directory, source, PAGEABLE, "PageableDefault"), "size and order are explicit");
    }

    @Test
    void reportsAMappedSortWithoutDefaults(@TempDir Path directory) {
        String source = """
            class Pages {
                @GetMapping void read(Sort order) {}
            }
            """;

        assertEquals(1, findings(directory, source, SORT, "SortDefault"), "the handler inherits its order");
    }

    @Test
    void acceptsExplicitSortDefaults(@TempDir Path directory) {
        String source = """
            class Pages {
                @GetMapping
                void read(@SortDefault(sort = "id", direction = Sort.Direction.ASC) Sort order) {}
            }
            """;

        assertEquals(0, findings(directory, source, SORT, "SortDefault"), "the handler states its order");
    }

    @Test
    void reportsEveryUnqualifiedMappedPaginationParameter(@TempDir Path directory) {
        String source = """
            class Pages {
                @GetMapping void read(Pageable current, Sort archived) {}
            }
            """;

        assertEquals(2, findings(directory, source, QUALIFIED, "count("), "both request namespaces collide");
    }

    @Test
    void acceptsQualifiedMappedPaginationParameters(@TempDir Path directory) {
        String source = """
            class Pages {
                @GetMapping
                void read(@Qualifier("current") Pageable current, @Qualifier("archived") Sort archived) {}
            }
            """;

        assertEquals(0, findings(directory, source, QUALIFIED, "count("), "both request namespaces are named");
    }

    @Test
    void passesOverPaginationOutsideMappedHandlers(@TempDir Path directory) {
        String source = """
            interface Rows {
                Object page(Pageable page);
                Object sort(Sort order);
                void compare(Pageable current, Sort archived);
            }
            """;
        List<Integer> reported = List.of(
            findings(directory, source, PAGEABLE, "PageableDefault"),
            findings(directory, source, SORT, "SortDefault"),
            findings(directory, source, QUALIFIED, "count(")
        );

        assertEquals(List.of(0, 0, 0), reported, "repository and application parameters are not web contracts");
    }

    private static int findings(Path directory, String source, String rule, String marker) {
        return CheckstyleRule.findings(directory, source, rule, marker);
    }
}
