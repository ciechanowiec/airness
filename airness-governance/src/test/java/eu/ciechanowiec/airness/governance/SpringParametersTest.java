package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringParametersTest {

    @Test
    void dividesParametersWithoutDividingNestedAnnotationOrGenericArguments() {
        String source = """
            class Queries {

                @Query("select row")
                @Observed(name = "query", tags = {"first", "second"})
                void rows(@Param("owner") Map<String, Integer> owners, Pageable page) {
                }
            }
            """;
        String code = JavaCode.blanked(source);
        int annotation = code.indexOf("@Query");
        int closes = SpringMembers.closing(code, code.indexOf('(', annotation));
        SpringParameters.Range range = SpringParameters.after(code, closes).orElseThrow();

        List<SpringParameters.Parameter> parameters = SpringParameters.in(source, code, range);

        assertEquals(List.of("owners", "page"), parameters.stream().map(SpringParameters.Parameter::name).toList());
    }

    @Test
    void returnsNothingWhenNoMethodFollowsTheAnnotation() {
        String code = JavaCode.blanked("@Query(\"select row\")");
        int closes = SpringMembers.closing(code, code.indexOf('('));

        assertFalse(SpringParameters.after(code, closes).isPresent(), "there is no parameter list");
    }
}
