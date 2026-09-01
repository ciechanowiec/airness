package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringJpaElementCollectionCheckstyleTest {

    private static final String JOIN = "AirnessSpringJpaJoinIsNamed";
    private static final String COLUMN = "AirnessSpringJpaColumnIsNamed";

    @Test
    void reportsAnElementCollectionWithImplicitMappings(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                Set<String> tags;
            }
            """;

        assertEquals(1, joinFindings(directory, source), "the table and element column are inferred");
    }

    @Test
    void acceptsAColumnForABasicElementCollection(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                @CollectionTable(
                    name = "room_tag",
                    joinColumns = @JoinColumn(name = "room_id", referencedColumnName = "id")
                )
                @Column(name = "tag")
                Set<String> tags;
            }
            """;

        assertEquals(0, joinFindings(directory, source), "the basic element owns one named column");
    }

    @Test
    void acceptsAnAttributeOverrideForAnEmbeddableCollection(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                @CollectionTable(
                    name = "room_capacity",
                    joinColumns = @JoinColumn(name = "room_id", referencedColumnName = "id")
                )
                @AttributeOverride(name = "layout", column = @Column(name = "layout"))
                Set<Capacity> capacities;
            }
            """;

        assertEquals(0, joinFindings(directory, source), "the embeddable component column is overridden");
    }

    @Test
    void acceptsAttributeOverridesForAnEmbeddableProperty(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                @CollectionTable(
                    name = "room_capacity",
                    joinColumns = @JoinColumn(name = "room_id", referencedColumnName = "id")
                )
                @AttributeOverrides({
                    @AttributeOverride(name = "layout", column = @Column(name = "layout")),
                    @AttributeOverride(name = "seats", column = @Column(name = "seats"))
                })
                Set<Capacity> getCapacities() {
                    return null;
                }
            }
            """;

        assertEquals(0, joinFindings(directory, source), "every embeddable component column is overridden");
    }

    @Test
    void stillRequiresACollectionTableBesideAnOverride(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                @AttributeOverride(name = "layout", column = @Column(name = "layout"))
                Set<Capacity> capacities;
            }
            """;

        assertEquals(1, joinFindings(directory, source), "the collection table remains implicit");
    }

    @Test
    void stillRequiresTheNestedOverrideColumnName(@TempDir Path directory) {
        String source = """
            class Room {
                @ElementCollection
                @CollectionTable(
                    name = "room_capacity",
                    joinColumns = @JoinColumn(name = "room_id", referencedColumnName = "id")
                )
                @AttributeOverride(name = "layout", column = @Column)
                Set<Capacity> capacities;
            }
            """;

        assertEquals(
            1,
            CheckstyleRule.findings(directory, source, COLUMN, "MapKeyColumn"),
            "the nested column cannot fall back to the component name"
        );
    }

    private static int joinFindings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, JOIN, "ElementCollection");
    }
}
