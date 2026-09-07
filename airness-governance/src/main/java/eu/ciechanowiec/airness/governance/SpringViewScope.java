package eu.ciechanowiec.airness.governance;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The annotations on the type owning a method, and whether its current body returns a view name.
 *
 * @param annotations the owning type's annotations
 * @param view        whether returns in the current executable name views
 */
record SpringViewScope(Set<String> annotations, boolean view) {

    private static final String WEB = "org.springframework.web.bind.annotation.";
    private static final String CONTROLLER = "Controller";
    private static final String ADVICE = "ControllerAdvice";
    private static final String BODY = "ResponseBody";
    private static final Set<String> BODIED = Set.of(BODY, "RestController", "RestControllerAdvice");
    private static final Set<String> MAPPINGS = Set.of(
        "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"
    );

    SpringViewScope {
        annotations = Set.copyOf(annotations);
    }

    /**
     * A type starts a new scope rather than inheriting annotations from an enclosing Java class.
     *
     * @param modifiers the type's own modifiers
     * @return the scope before entering any method
     */
    static SpringViewScope type(ModifiersTree modifiers) {
        return new SpringViewScope(names(modifiers), false);
    }

    /**
     * Whether this method's strings are handled as view names by the declared Spring annotations.
     *
     * @param method the method being entered
     * @return its executable scope
     */
    SpringViewScope method(MethodTree method) {
        Set<String> own = names(method.getModifiers());
        return new SpringViewScope(this.annotations, this.handler(own) && !this.content(own));
    }

    private boolean handler(Collection<String> own) {
        boolean controller = this.annotations.contains(CONTROLLER);
        boolean mapped = controller && MAPPINGS.stream().anyMatch(own::contains);
        boolean exception = (controller || this.annotations.contains(ADVICE)) && own.contains("ExceptionHandler");
        return mapped || exception;
    }

    private boolean content(Collection<String> own) {
        return this.annotations.stream().anyMatch(BODIED::contains)
            || own.contains(BODY) || own.contains("ModelAttribute");
    }

    private static Set<String> names(ModifiersTree modifiers) {
        return modifiers.getAnnotations().stream()
            .map(annotation -> annotation.getAnnotationType().toString())
            .map(SpringViewScope::simple)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String simple(String name) {
        if (name.startsWith(WEB)) {
            return name.substring(WEB.length());
        }
        return "org.springframework.stereotype.Controller".equals(name) ? CONTROLLER : name;
    }
}
