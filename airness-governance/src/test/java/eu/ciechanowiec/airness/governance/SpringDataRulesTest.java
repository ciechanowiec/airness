package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringDataRulesTest {

    private static final String REPOSITORY = "src/main/java/com/example/Orders.java";
    private static final String REDUNDANT = "repository interfaces carrying a redundant";
    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));

    private static final String DIRECT = """
        package com.example;

        import org.springframework.data.jpa.repository.JpaRepository;
        import org.springframework.stereotype.Repository;

        @Repository
        interface Orders extends JpaRepository<Order, Long> {
        }
        """;
    private static final String SPRING_PARENT = """
        package com.example.spring;

        import org.springframework.data.repository.Repository;

        interface BaseRepository<T, I> extends Repository<T, I> {
        }
        """;
    private static final String UNRELATED_PARENT = """
        package com.example.other;

        interface BaseRepository<T, I> {
        }
        """;

    private static List<String> offences(GitFixture fixture) {
        return Verdicts.offences(
            new SpringModuleCheck(fixture.root(), MAIN, List.of()).findings(), REDUNDANT
        );
    }

    private static GitFixture ambiguousFixture(String name, String importedPackage) {
        String child = """
            package com.example.client;

            import %s.BaseRepository;
            import org.springframework.stereotype.Repository;

            @Repository
            interface Orders extends BaseRepository<Order, Long> {
            }
            """.formatted(importedPackage);
        return new GitFixture(name)
            .write("src/main/java/com/example/spring/BaseRepository.java", SPRING_PARENT)
            .write("src/main/java/com/example/other/BaseRepository.java", UNRELATED_PARENT)
            .write("src/main/java/com/example/client/Orders.java", child);
    }

    @Test
    void reportsAnUnnamedStereotypeOnASpringDataRepository() {
        List<String> offences = offences(new GitFixture("data-stereotype").write(REPOSITORY, DIRECT));

        assertEquals(1, offences.size(), "the hierarchy already registers the interface");
        assertTrue(offences.getFirst().contains("remove"), "the finding names the repair");
    }

    @Test
    void acceptsAnExplicitRepositoryBeanName() {
        String named = DIRECT.replace("@Repository", "@Repository(\"orders\")");

        assertEquals(
            List.of(), offences(new GitFixture("data-stereotype-named").write(REPOSITORY, named)),
            "the annotation now states a name the hierarchy does not"
        );
    }

    @Test
    void followsAProjectRepositoryHierarchy() {
        String parent = """
            package com.example;

            import org.springframework.data.repository.Repository;

            interface BaseRepository<T, I> extends Repository<T, I> {
            }
            """;
        String child = DIRECT
            .replace("import org.springframework.data.jpa.repository.JpaRepository;\n", "")
            .replace("JpaRepository<Order, Long>", "BaseRepository<Order, Long>");
        GitFixture fixture = new GitFixture("data-stereotype-inherited")
            .write("src/main/java/com/example/BaseRepository.java", parent)
            .write(REPOSITORY, child);

        assertEquals(1, offences(fixture).size(), "the project interface keeps the Spring Data role");
    }

    @Test
    void readsAFullyQualifiedSpringDataHierarchy() {
        String qualified = DIRECT
            .replace("import org.springframework.data.jpa.repository.JpaRepository;\n", "")
            .replace(
                "JpaRepository<Order, Long>",
                "org.springframework.data.jpa.repository.JpaRepository<Order, Long>"
            );

        assertEquals(
            1, offences(new GitFixture("data-stereotype-qualified").write(REPOSITORY, qualified)).size(),
            "the supplier is stated at the use"
        );
    }

    @Test
    void passesOverAnUnrelatedRepositoryHierarchy() {
        String unrelated = DIRECT
            .replace(
                "import org.springframework.data.jpa.repository.JpaRepository;",
                "import com.example.storage.JpaRepository;"
            );

        assertEquals(
            List.of(), offences(new GitFixture("data-stereotype-unrelated").write(REPOSITORY, unrelated)),
            "the common type name proves no Spring Data relationship"
        );
    }

    @Test
    void passesOverARepositoryClass() {
        String repositoryClass = DIRECT.replace(
            "interface Orders extends JpaRepository<Order, Long>",
            "final class Orders"
        );

        assertEquals(
            List.of(), offences(new GitFixture("data-stereotype-class").write(REPOSITORY, repositoryClass)),
            "a class may use the stereotype for exception translation"
        );
    }

    @Test
    void acceptsDuplicateSimpleNamesInDifferentPackages() {
        String component = """
            package PLACEHOLDER;

            @Component
            final class Numbering {
            }
            """;
        GitFixture fixture = new GitFixture("data-duplicate-names")
            .write(
                "src/main/java/com/example/one/Numbering.java",
                component.replace("PLACEHOLDER", "com.example.one")
            )
            .write(
                "src/main/java/com/example/two/Numbering.java",
                component.replace("PLACEHOLDER", "com.example.two")
            );

        assertEquals(List.of(), offences(fixture), "simple names are not a unique module key");
    }

    @Test
    void resolvesAnInheritedRepositoryThroughItsExactImport() {
        GitFixture fixture = ambiguousFixture("data-imported-hierarchy", "com.example.spring");

        assertEquals(1, offences(fixture).size(), "the exact imported parent is a Spring Data repository");
    }

    @Test
    void passesOverAnImportedUnrelatedParentWithTheSameSimpleName() {
        GitFixture fixture = ambiguousFixture("data-unrelated-import", "com.example.other");

        assertEquals(List.of(), offences(fixture), "the imported parent carries no Spring Data role");
    }
}
