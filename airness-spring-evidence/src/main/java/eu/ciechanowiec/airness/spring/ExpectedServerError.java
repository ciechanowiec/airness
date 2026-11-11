package eu.ciechanowiec.airness.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an error the annotated test provokes in the application on purpose.
 *
 * <p>A Spring test fails when the application logs at ERROR while it runs, because a page that renders while the
 * server reports a failure behind it is a defect the response alone does not show. A test that drives the
 * application into such a failure deliberately, such as a fault page answering with a reference, states it here.
 * The declaration allows the errors logged by {@link #logger()} and the loggers named beneath it, during the
 * annotated test method alone, and fails the test when no such error was logged, so a declaration cannot outlive
 * the failure it was written for.
 *
 * <p>An exception that ends a thread with nothing catching it cannot be declared. A test that expects an exception
 * asserts it where it is thrown.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ExpectedServerErrors.class)
public @interface ExpectedServerError {

    /**
     * The class whose logger reports the error. Loggers named beneath it, such as those of its nested classes or
     * those a library names after one of its components, are matched as well.
     *
     * @return the class whose logger reports the error
     */
    Class<?> logger();

    /**
     * Why the test provokes the error, stated for the reader who meets the declaration rather than the failure.
     *
     * @return the reason, never blank
     */
    String reason();
}
