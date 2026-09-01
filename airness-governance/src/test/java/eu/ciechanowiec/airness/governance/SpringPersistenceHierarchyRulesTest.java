package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringPersistenceHierarchyRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final String ROOT = "src/main/java/sample/Account.java";
    private static final String CHILD = "src/main/java/sample/Customer.java";
    private static final String SINGLE_ROOT = """
        package sample;

        @Entity(name = "Account")
        @Table(name = "account")
        @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
        @DiscriminatorColumn(name = "kind")
        class Account {
        }
        """;
    private static final String SINGLE_CHILD = """
        package sample;

        @Entity(name = "Customer")
        @DiscriminatorValue("customer")
        class Customer inherits Account {
        }
        """.replace("inherits", "extends");

    @Test
    void acceptsACompleteSingleTableHierarchy() {
        SpringTypes types = types(
            new GitFixture("hierarchy-single").write(ROOT, SINGLE_ROOT).write(CHILD, SINGLE_CHILD)
        );

        assertEquals(List.of(), SpringPersistenceHierarchyRules.implicitMappings(types), "all names are stated");
    }

    @Test
    void reportsARootThatLeavesTheInheritanceStrategyImplicit() {
        String root = SINGLE_ROOT.replace("@Inheritance(strategy = InheritanceType.SINGLE_TABLE)\n", "");
        SpringTypes types = types(
            new GitFixture("hierarchy-strategy").write(ROOT, root).write(CHILD, SINGLE_CHILD)
        );

        assertTrue(
            SpringPersistenceHierarchyRules.implicitMappings(types).getFirst().contains("@Inheritance"),
            "the root owns the missing decision"
        );
    }

    @Test
    void reportsASingleTableSubtypeWithoutADiscriminatorValue() {
        String child = SINGLE_CHILD.replace("@DiscriminatorValue(\"customer\")\n", "");
        SpringTypes types = types(
            new GitFixture("hierarchy-discriminator").write(ROOT, SINGLE_ROOT).write(CHILD, child)
        );

        assertEquals(1, SpringPersistenceHierarchyRules.implicitMappings(types).size(), "the subtype is unnamed");
    }

    @Test
    void requiresJoinedSubtypesToNameTheirTableAndPrimaryKeyJoin() {
        String root = SINGLE_ROOT
            .replace("SINGLE_TABLE", "JOINED")
            .replace("@DiscriminatorColumn(name = \"kind\")\n", "");
        String child = SINGLE_CHILD.replace("@DiscriminatorValue(\"customer\")\n", "");
        SpringTypes types = types(
            new GitFixture("hierarchy-joined").write(ROOT, root).write(CHILD, child)
        );

        assertEquals(2, SpringPersistenceHierarchyRules.implicitMappings(types).size(), "both schema edges are absent");
    }

    @Test
    void requiresTablePerClassSubtypesToNameTheirTable() {
        String root = SINGLE_ROOT
            .replace("SINGLE_TABLE", "TABLE_PER_CLASS")
            .replace("@DiscriminatorColumn(name = \"kind\")\n", "");
        String child = SINGLE_CHILD.replace("@DiscriminatorValue(\"customer\")\n", "");
        SpringTypes types = types(
            new GitFixture("hierarchy-table-per-class").write(ROOT, root).write(CHILD, child)
        );

        assertEquals(1, SpringPersistenceHierarchyRules.implicitMappings(types).size(), "the child owns a table");
    }

    @Test
    void requiresAnEntityExtendingALocalMappedSuperclassToNameItsTable() {
        String base = """
            package sample;

            @MappedSuperclass
            class Account {
            }
            """;
        String child = """
            package sample;

            @Entity(name = "Customer")
            class Customer inherits Account {
            }
            """.replace("inherits", "extends");
        SpringTypes types = types(
            new GitFixture("hierarchy-mapped-base").write(ROOT, base).write(CHILD, child)
        );

        assertEquals(1, SpringPersistenceHierarchyRules.implicitMappings(types).size(), "the entity owns the table");
    }

    @Test
    void passesOverAnUnresolvedExternalEntityParent() {
        String child = SINGLE_CHILD.replace("extends Account", "extends external.Account");
        SpringTypes types = types(new GitFixture("hierarchy-external").write(CHILD, child));

        assertEquals(List.of(), SpringPersistenceHierarchyRules.implicitMappings(types), "ownership is unknown");
    }

    private static SpringTypes types(GitFixture fixture) {
        Path root = fixture.root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES));
    }
}
