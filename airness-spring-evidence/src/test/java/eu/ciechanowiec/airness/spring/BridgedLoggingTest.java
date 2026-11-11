package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * That an error the platform logger reports reaches the record through the bridge Spring Boot installs, which is
 * how a servlet container reports one.
 */
class BridgedLoggingTest {

    private static final Logger LOG = Logger.getLogger(BridgedLoggingTest.class.getName());

    @Test
    void recordsASevereEventBridgedFromJavaLogging() {
        ServerErrorRecord errors = ServerErrorRecord.open();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        try {
            LOG.severe("severe");
        } finally {
            SLF4JBridgeHandler.uninstall();
            errors.close();
        }
        assertEquals(
            List.of(BridgedLoggingTest.class.getName() + ": severe"),
            errors.drain().stream().map(ServerError::described).toList()
        );
    }
}
