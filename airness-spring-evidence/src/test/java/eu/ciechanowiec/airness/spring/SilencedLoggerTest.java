package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * That a logger switched off still reports its errors to the record.
 */
class SilencedLoggerTest {

    private static final Logger LOG = (Logger) LoggerFactory.getLogger(SilencedLoggerTest.class);

    @Test
    void recordsAnErrorWhoseLoggerIsSwitchedOff() {
        ServerErrorRecord errors = ServerErrorRecord.open();
        Level configured = LOG.getLevel();
        LOG.setLevel(Level.OFF);
        try {
            LOG.error("silenced");
        } finally {
            LOG.setLevel(configured);
            errors.close();
        }
        assertEquals(
            List.of(SilencedLoggerTest.class.getName() + ": silenced"),
            errors.drain().stream().map(ServerError::described).toList()
        );
    }
}
