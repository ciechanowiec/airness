package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.MessageParityCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every language of a message bundle declares the same names, and declares each of them once: a name
 * one language omits is answered out of another rather than reported, and a name declared twice in one
 * file is replaced by its own second declaration without anything saying so.
 */
@Mojo(name = "message-parity", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class MessageParityMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        MessageParityCheck check = new MessageParityCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Message parity read " + check.scanned() + " message bundle(s)");
        return check.findings();
    }
}
