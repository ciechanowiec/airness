package eu.ciechanowiec.airness.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

/**
 * The beans that a security expression names and the ready application cannot resolve.
 *
 * <p>An expression guarding a method has two halves. The parameters it reads are named beside the
 * parameter list, where a rule reading the source can check them. The bean it calls is named nowhere
 * but inside the string, and whether that bean exists is decided by the classes of the module
 * together with every auto-configuration on the classpath, so no source states it and no rule reading
 * source can find it. That is why this is asked of the built context rather than of the text that
 * produced it: a rule reading source would have to guess, and it would accuse a project naming a bean
 * that a dependency of it declares.
 *
 * <p>What a wrong name costs is a guard that cannot be evaluated. The expression is parsed when the
 * annotation is read and resolved when a caller arrives, so a bean nobody declares and a method
 * nobody wrote both compile, pass every analyzer, and then fail the first request that reaches them.
 * The refusal is at least the safe way round, since nothing gets through a guard that threw, but an
 * endpoint that answers nobody is not what the annotation says and not what any test that never
 * reached it reported.
 *
 * <p>Nothing here builds a bean. Types are read from the definitions rather than from instances, and
 * a factory bean is never asked to make its product, so recording this evidence cannot change what
 * the test around it observes. That is the same promise the open-mapping evidence makes beside it.
 *
 * <p>Only the beans of the application itself are read, under the roots the run declares, because a
 * guard written by a library is that library's business and its beans are resolved against a context
 * this one knows nothing about.
 *
 * <p>Two things are passed over rather than reported. A reference whose bean cannot be typed at all
 * is left alone, since a rule that cannot see the methods of a bean has nothing to say about which of
 * them exist. And a reference naming no member is left alone as well, because a bean handed whole to
 * an expression is a value rather than a call.
 */
@UtilityClass
final class SpringGuardBeans {

    /**
     * The annotations carrying an expression, matched by simple name so that a package the framework
     * moves them to does not quietly empty this set. The two that name roles without an expression
     * carry no reference to resolve and are not read here.
     */
    private static final Set<String> GUARDS = Set.of(
        "PreAuthorize", "PostAuthorize", "PreFilter", "PostFilter"
    );

    // A bean reference, and the member it calls where it calls one. The member is optional because an
    // expression may hand the bean itself to something else, which names no method to look for.
    private static final Pattern REFERENCE = Pattern.compile("@(\\w+)(?:\\s*\\.\\s*(\\w+)\\s*\\()?");

    private static final String EXPRESSION = "value";

    private static final String UNKNOWN_BEAN = "guard-bean ";

    private static final String UNKNOWN_CALL = "guard-call ";

    /**
     * Every reference a security expression of the application makes that the ready context cannot
     * resolve.
     *
     * @param context the context Spring Boot made ready
     * @param roots   the package roots the application declares, which its own beans sit under
     * @return one line per unresolvable reference, sorted, and nothing for a context that is not
     *         active
     */
    static List<String> unresolved(ConfigurableApplicationContext context, Collection<String> roots) {
        return context.isActive() ? asked(context.getBeanFactory(), roots) : List.of();
    }

    /**
     * Every guard of the application, put to the factory that would have to resolve it.
     *
     * @param beans the factory the ready context holds
     * @param roots the package roots the application declares
     * @return the evidence lines
     */
    private static List<String> asked(ConfigurableListableBeanFactory beans, Collection<String> roots) {
        return Stream.of(beans.getBeanDefinitionNames())
            .map(name -> typed(beans, name))
            .flatMap(Optional::stream)
            .filter(type -> owned(type, roots))
            .distinct()
            .flatMap(SpringGuardBeans::guarding)
            .flatMap(guard -> unresolvable(beans, guard))
            .distinct()
            .sorted()
            .toList();
    }

    // The type of one bean, read from its definition. A factory bean is not asked to make its product,
    // which is what the second argument says, so nothing is built in order to be read.
    //
    // What comes back for a guarded bean is the subclass the container generated to apply the guard,
    // and a guard annotation is not inherited, so reading that subclass would find no annotation on
    // the very beans this exists to read. The written class behind it is what is asked instead.
    private static Optional<Class<?>> typed(ConfigurableListableBeanFactory beans, String name) {
        return Optional.ofNullable(beans.getType(name, false)).map(ClassUtils::getUserClass);
    }

    /**
     * Whether the bean was declared by the application rather than contributed to it.
     *
     * @param type  the type the definition names
     * @param roots the package roots the application declares
     * @return whether that type sits under one of those roots
     */
    private static boolean owned(Class<?> type, Collection<String> roots) {
        String declaring = type.getName();
        return roots.stream().anyMatch(root -> declaring.startsWith(root + '.'));
    }

    private static Stream<Guard> guarding(Class<?> type) {
        return Stream.concat(onType(type), onMethods(type));
    }

    private static Stream<Guard> onType(Class<?> type) {
        return expressions(type).map(stated -> new Guard(stated, type.getName()));
    }

    private static Stream<Guard> onMethods(Class<?> type) {
        return Stream.of(type.getDeclaredMethods()).flatMap(method -> onMethod(type, method));
    }

    private static Stream<Guard> onMethod(Class<?> type, Method method) {
        return expressions(method).map(stated -> new Guard(stated, type.getName() + '#' + method.getName()));
    }

    private static Stream<String> expressions(AnnotatedElement element) {
        return Stream.of(element.getAnnotations())
            .filter(annotation -> GUARDS.contains(annotation.annotationType().getSimpleName()))
            .map(SpringGuardBeans::stated)
            .flatMap(Optional::stream);
    }

    private static Optional<String> stated(Annotation annotation) {
        return Optional.ofNullable(AnnotationUtils.getValue(annotation, EXPRESSION))
            .filter(String.class::isInstance)
            .map(String.class::cast);
    }

    /**
     * Every reference of one expression that the factory would not resolve.
     *
     * @param beans the factory the ready context holds
     * @param guard one expression and where it is written
     * @return the evidence lines this expression earns
     */
    private static Stream<String> unresolvable(ConfigurableListableBeanFactory beans, Guard guard) {
        return REFERENCE.matcher(guard.expression())
            .results()
            .map(reference -> refused(beans, guard, reference))
            .flatMap(Optional::stream);
    }

    private static Optional<String> refused(
        ConfigurableListableBeanFactory beans, Guard guard, MatchResult reference
    ) {
        String bean = reference.group(1);
        return beans.containsBean(bean)
            ? missing(beans, guard, bean, Optional.ofNullable(reference.group(2)))
            : Optional.of(UNKNOWN_BEAN + bean + ' ' + guard.where());
    }

    private static Optional<String> missing(
        ConfigurableListableBeanFactory beans, Guard guard, String bean, Optional<String> call
    ) {
        return call.filter(named -> !declares(beans, bean, named))
            .map(named -> UNKNOWN_CALL + bean + '.' + named + ' ' + guard.where());
    }

    // Whether the bean answers to a method of that name. A bean whose type the factory cannot state is
    // read as answering, because a rule that cannot see any method of a bean has no ground to say that
    // this one is absent.
    private static boolean declares(ConfigurableListableBeanFactory beans, String bean, String call) {
        return typed(beans, bean).map(type -> answers(type, call)).orElse(true);
    }

    // What the bean inherits publicly and what its own type declares, together, because the engine
    // resolves a call against both and a rule reading only the first would accuse a guard that works.
    private static boolean answers(Class<?> type, String call) {
        return Stream.concat(Stream.of(type.getMethods()), Stream.of(type.getDeclaredMethods()))
            .anyMatch(method -> method.getName().equals(call));
    }

    /**
     * One expression and the declaration it guards.
     *
     * @param expression the expression as the annotation states it
     * @param where      the type, and the method where the guard sits on one
     */
    private record Guard(String expression, String where) {
    }
}
