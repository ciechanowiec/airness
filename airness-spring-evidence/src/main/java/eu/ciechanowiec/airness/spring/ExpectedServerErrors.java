package eu.ciechanowiec.airness.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Holds the {@link ExpectedServerError} declarations of one test method that declares more than one.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExpectedServerErrors {

    /**
     * The declarations, in the order they are written.
     *
     * @return the declarations
     */
    ExpectedServerError[] value();
}
