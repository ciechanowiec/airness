package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * That the declarations a test method repeats are held, in order, by their container.
 */
class ExpectedServerErrorsTest {

    @Test
    @SneakyThrows
    void holdsEveryDeclarationOfAMethodInTheOrderWritten() {
        ExpectedServerErrors held = Provocations.class.getDeclaredMethod("declaredTwice")
            .getAnnotation(ExpectedServerErrors.class);
        List<Class<?>> loggers = Arrays.stream(held.value()).<Class<?>>map(ExpectedServerError::logger).toList();
        assertEquals(List.of(ServerErrorListenerTest.class, Provocations.class), loggers);
    }
}
