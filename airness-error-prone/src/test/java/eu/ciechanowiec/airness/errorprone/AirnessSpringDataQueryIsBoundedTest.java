package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirnessSpringDataQueryIsBoundedTest {

    private static final String CHECK = "AirnessSpringDataQueryIsBounded";

    private static final long CALL_LINE = 12;

    private static final long DECLARATION_LINE = 10;

    private static final long SECOND_DECLARATION_LINE = 15;

    private static final String CALLER = """
        package example;

        import java.util.*;
        import java.util.function.Supplier;
        import org.springframework.data.domain.*;
        import org.springframework.data.jpa.domain.Specification;
        import org.springframework.data.jpa.repository.*;
        import org.springframework.data.repository.query.FluentQuery;

        class Reader {
            Object read(TaskStore store, Specification<Task> spec, Example<Task> example, Pageable page, Sort sort) {
                return %s;
            }
        }

        interface TaskStore extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
        }

        class Task {
        }
        """;

    private static final String DECLARATION = """
        package example;

        import java.util.*;
        import java.util.stream.Stream;
        import org.springframework.data.domain.*;
        import org.springframework.data.repository.Repository;

        interface TaskStore extends Repository<Task, Long> {

            %s;
        }

        class Task {
        }
        """;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "store.findAll()",
            "store.findAll(sort)",
            "store.findAll(spec)",
            "store.findAll(spec, sort)",
            "store.findAll(example)",
            "store.findAll(example, sort)",
            "store.findBy(spec, query -> query.all())",
            "store.findBy(example, query -> query.stream())",
            "store.findBy(example, FluentQuery.FetchableFluentQuery::all)",
            "(Supplier<List<Task>>) store::findAll",
            "store.findAll(Pageable.unpaged())",
            "store.findAll(spec, Pageable.unpaged(sort))",
            "Limit.unlimited()"
        }
    )
    void reportsAnUnboundedCallToAMethodSpringDataDeclares(String call, @TempDir Path directory) {
        assertEquals(List.of(CALL_LINE), ErrorProneRule.findings(directory, CHECK, CALLER.formatted(call)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "store.findAll(page)",
            "store.findAll(spec, page)",
            "store.findAll(example, page)",
            "store.findAllById(List.of(1L, 2L))",
            "store.saveAll(List.of(new Task()))",
            "store.saveAllAndFlush(List.<Task>of())",
            "store.count(spec)",
            "store.count()",
            "store.findOne(spec)",
            "store.findById(1L)",
            "store.findBy(spec, query -> query.limit(5).all())",
            "store.findBy(example, query -> query.limit(3).sortBy(sort).stream())",
            "store.findBy(spec, query -> query.page(page))",
            "store.findBy(example, query -> query.scroll(ScrollPosition.offset()))"
        }
    )
    void acceptsACallBoundedByAPageALimitOrItsInput(String call, @TempDir Path directory) {
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, CALLER.formatted(call)));
    }

    @Test
    void reportsAnInheritedMethodOfARepositoryOnlyItsBaseNames(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.data.domain.Sort;
            import org.springframework.data.repository.CrudRepository;
            import org.springframework.data.repository.PagingAndSortingRepository;

            class Reader {
                Object read(Tasks tasks, Sort sort) {
                    return java.util.List.of(tasks.findAll(), tasks.findAll(sort));
                }
            }

            interface Tasks extends CrudRepository<Task, Long>, PagingAndSortingRepository<Task, Long> {
            }

            class Task {
            }
            """;
        assertEquals(List.of(9L, 9L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsAnUnboundedQuerydslCall(@TempDir Path directory) {
        String predicate = """
            package com.querydsl.core.types;

            public interface Predicate {
            }
            """;
        String order = """
            package com.querydsl.core.types;

            public class OrderSpecifier<T> {
            }
            """;
        String source = """
            package example;

            import com.querydsl.core.types.Predicate;
            import org.springframework.data.querydsl.QuerydslPredicateExecutor;
            import org.springframework.data.repository.Repository;

            class Reader {
                Object read(Tasks tasks, Predicate predicate) {
                    return tasks.findAll(predicate);
                }
            }

            interface Tasks extends Repository<Task, Long>, QuerydslPredicateExecutor<Task> {
            }

            class Task {
            }
            """;
        assertEquals(List.of(9L), ErrorProneRule.findings(directory, CHECK, predicate, order, source));
    }

    @Test
    void reportsAnUnboundedCallInADefaultMethodOfTheRepository(@TempDir Path directory) {
        String source = """
            package example;

            import org.springframework.data.jpa.repository.JpaRepository;

            interface TaskStore extends JpaRepository<Task, Long> {

                default int size() {
                    return findAll().size();
                }
            }

            class Task {
            }
            """;
        assertEquals(List.of(8L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void judgesACallToAMethodTheRepositoryDeclaresAtItsDeclarationAlone(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.List;
            import org.springframework.data.domain.Limit;
            import org.springframework.data.repository.Repository;

            class Reader {
                Object read(TaskStore store) {
                    return List.of(store.findByName("a"), store.findByOwner("b", Limit.of(5)));
                }
            }

            interface TaskStore extends Repository<Task, Long> {

                List<Task> findByName(String name);

                List<Task> findByOwner(String owner, Limit limit);
            }

            class Task {
            }
            """;
        assertEquals(List.of(SECOND_DECLARATION_LINE), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void passesOverAnUnboundedCallInATestSource(@TempDir Path directory) {
        String source = CALLER.formatted("store.findAll(Pageable.unpaged())");
        assertEquals(List.of(), ErrorProneRule.findingsIn(directory, CHECK, "test", source));
    }

    @Test
    void honorsASuppression(@TempDir Path directory) {
        String suppressed = "    @SuppressWarnings(\"" + CHECK + "\")\n    Object read(";
        String source = CALLER.formatted("store.findAll()").replace("    Object read(", suppressed);
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "List<Task> findByName(String name)",
            "Set<Task> findByName(String name)",
            "Collection<Task> findByName(String name)",
            "Stream<Task> findByName(String name)",
            "Iterable<Task> findByName(String name)",
            "List<Task> findTop10ByOrderByNameDesc()",
            "List<Task> findByOwnerIn(Collection<String> owners)"
        }
    )
    void reportsAnUnboundedQueryARepositoryDeclares(String method, @TempDir Path directory) {
        String source = DECLARATION.formatted(method);
        assertEquals(List.of(DECLARATION_LINE), ErrorProneRule.findings(directory, CHECK, source));
        assertEquals(List.of(DECLARATION_LINE), ErrorProneRule.findingsIn(directory, CHECK, "test", source));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "List<Task> findByName(String name, Pageable page)",
            "List<Task> findByName(String name, Limit limit)",
            "Page<Task> findByName(String name, Pageable page)",
            "Slice<Task> findByName(String name, Pageable page)",
            "Window<Task> findFirst10ByName(String name, ScrollPosition position)",
            "Optional<Task> findByName(String name)",
            "long countByName(String name)",
            "default List<Task> none() { return List.of(); }"
        }
    )
    void acceptsABoundedQueryARepositoryDeclares(String method, @TempDir Path directory) {
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, DECLARATION.formatted(method)));
    }

    @Test
    void readsARepositoryByItsDefinitionOrItsBaseWhateverItsName(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.List;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.RepositoryDefinition;

            @RepositoryDefinition(domainClass = Task.class, idClass = Long.class)
            interface Tasks {
                List<Task> findByName(String name);
            }

            @NoRepositoryBean
            interface Base<T> extends JpaRepository<T, Long> {
                List<T> findByOwner(String owner);
            }

            interface TaskStore extends Base<Task> {
            }

            class Task {
            }
            """;
        List<Long> declarations = List.of(DECLARATION_LINE, SECOND_DECLARATION_LINE);
        assertEquals(declarations, ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void passesOverAnInterfaceThatIsNoSpringDataRepository(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.List;

            interface TaskRepository {
                List<Task> all();
            }

            class Task {
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }
}
