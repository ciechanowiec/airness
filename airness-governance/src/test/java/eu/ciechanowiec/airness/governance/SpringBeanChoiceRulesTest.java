package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringBeanChoiceRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final String POOLS = "src/main/java/sample/Pools.java";
    private static final String WORK = "src/main/java/sample/Work.java";
    private static final String TWO_POOLS = """
        package sample;

        @Configuration
        class Pools {

            @Bean
            TaskExecutor first() {
                return null;
            }

            @Bean
            TaskExecutor second() {
                return null;
            }
        }
        """;
    private static final String INJECTION = """
        package sample;

        @Service
        class Work {

            Work(TaskExecutor executor) {
            }
        }
        """;

    @Test
    void reportsADirectChoiceAmongTwoUnconditionalBeans() {
        SpringTypes types = types(
            new GitFixture("bean-choice").write(POOLS, TWO_POOLS).write(WORK, INJECTION)
        );

        List<String> offences = SpringBeanChoiceRules.implicitChoices(types);

        assertEquals(1, offences.size(), "the constructor leaves the choice implicit");
        assertTrue(offences.getFirst().contains("first") && offences.getFirst().contains("second"), "both are named");
    }

    @Test
    void acceptsAQualifierNamingOneCandidate() {
        String injection = INJECTION.replace(
            "Work(TaskExecutor executor)", "Work(@Qualifier(\"second\") TaskExecutor executor)"
        );
        SpringTypes types = types(
            new GitFixture("bean-qualified").write(POOLS, TWO_POOLS).write(WORK, injection)
        );

        assertEquals(List.of(), SpringBeanChoiceRules.implicitChoices(types), "the injection chooses second");
    }

    @Test
    void acceptsExactlyOnePrimaryCandidate() {
        String pools = TWO_POOLS.replace(
            "@Bean\n    TaskExecutor first", "@Bean\n    @Primary\n    TaskExecutor first"
        );
        SpringTypes types = types(
            new GitFixture("bean-primary").write(POOLS, pools).write(WORK, INJECTION)
        );

        assertEquals(List.of(), SpringBeanChoiceRules.implicitChoices(types), "primary is the declaration");
    }

    @Test
    void passesOverCandidatesThatAreConditional() {
        String pools = TWO_POOLS.replace(
            "@Bean\n    TaskExecutor second", "@Bean\n    @Profile(\"other\")\n    TaskExecutor second"
        );
        SpringTypes types = types(
            new GitFixture("bean-profile").write(POOLS, pools).write(WORK, INJECTION)
        );

        assertEquals(List.of(), SpringBeanChoiceRules.implicitChoices(types), "the two are not proven simultaneous");
    }

    @Test
    void passesOverCollectionInjectionThatRequestsEveryCandidate() {
        String injection = INJECTION.replace("TaskExecutor executor", "List<TaskExecutor> executors");
        SpringTypes types = types(
            new GitFixture("bean-collection").write(POOLS, TWO_POOLS).write(WORK, injection)
        );

        assertEquals(List.of(), SpringBeanChoiceRules.implicitChoices(types), "the collection asks for both");
    }

    @Test
    void checksBeanMethodParametersAsInjectionPoints() {
        String pools = TWO_POOLS.replace(
            "class Pools {",
            "class Pools {\n\n    @Bean\n    Work work(TaskExecutor executor) {\n        return null;\n    }"
        );
        SpringTypes types = types(new GitFixture("bean-method-choice").write(POOLS, pools));

        assertEquals(1, SpringBeanChoiceRules.implicitChoices(types).size(), "the bean method also injects");
    }

    @Test
    void comparesImportedTypesByTheirQualifiedName() {
        String pools = TWO_POOLS.replace("package sample;", "package sample;\n\nimport task.TaskExecutor;");
        String injection = INJECTION.replace("package sample;", "package sample;\n\nimport task.TaskExecutor;");
        SpringTypes types = types(
            new GitFixture("bean-imports").write(POOLS, pools).write(WORK, injection)
        );

        assertEquals(1, SpringBeanChoiceRules.implicitChoices(types).size(), "both imports name task.TaskExecutor");
    }

    @Test
    void acceptsAnExplicitBeanNameOrBeanQualifier() {
        String pools = TWO_POOLS
            .replace("@Bean\n    TaskExecutor first", "@Bean(\"firstPool\")\n    TaskExecutor first")
            .replace(
                "@Bean\n    TaskExecutor second",
                "@Bean(name = {\"secondPool\"})\n    @Qualifier(\"workerPool\")\n    TaskExecutor second"
            );
        String injection = INJECTION.replace(
            "Work(TaskExecutor executor)", "Work(@Qualifier(\"workerPool\") TaskExecutor executor)"
        );
        SpringTypes types = types(
            new GitFixture("bean-explicit-names").write(POOLS, pools).write(WORK, injection)
        );

        assertEquals(
            List.of(), SpringBeanChoiceRules.implicitChoices(types), "the method qualifier is a candidate name"
        );
    }

    @Test
    void comparesFullyQualifiedDeclaredTypes() {
        String pools = TWO_POOLS.replace("TaskExecutor", "task.TaskExecutor");
        String injection = INJECTION.replace("TaskExecutor", "task.TaskExecutor");
        SpringTypes types = types(
            new GitFixture("bean-qualified-types").write(POOLS, pools).write(WORK, injection)
        );

        assertEquals(1, SpringBeanChoiceRules.implicitChoices(types).size(), "the written type is already resolved");
    }

    @Test
    void passesOverATypeShapeTheNarrowRuleCannotResolve() {
        String pools = TWO_POOLS.replace("TaskExecutor", "Map<String, List<Integer>>");
        String injection = INJECTION.replace("TaskExecutor", "Map<String, List<Integer>>");
        SpringTypes types = types(
            new GitFixture("bean-nested-generic").write(POOLS, pools).write(WORK, injection)
        );

        assertEquals(
            List.of(), SpringBeanChoiceRules.implicitChoices(types), "nested generic candidates are not guessed"
        );
    }

    private static SpringTypes types(GitFixture fixture) {
        Path root = fixture.root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES));
    }
}
