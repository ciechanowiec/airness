package eu.ciechanowiec.airness.maven;

import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The build may reach the network, so the scan that reads an advisory database has something to read.
 *
 * <p>Maven skips a goal whose descriptor requires online mode, warns in one line, and carries on. The
 * vulnerability scan is such a goal, so an offline Extended verification reports clean without reading a
 * single advisory, and it reports clean on the very dependency set an online run refuses. Nothing else in
 * the profile notices, because a scan that never ran leaves no finding to disagree with. This runs at
 * {@code validate} so the build stops before the profile can speak for advisories nobody read.
 *
 * <p>Only Extended verification is held to this. No goal that Default verification binds requires online
 * mode, so an offline Default build runs everything it claims to run, which is what makes the fast loop
 * worth keeping.
 *
 * <p>What is read is the offline flag the build was started with rather than a network that happens to be
 * unreachable. A scan that runs and cannot reach its feed fails on its own and names the reason, while a
 * scan Maven never started is the one that needs somebody to say so.
 */
@Mojo(name = "require-online", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class RequireOnlineMojo extends AbstractPreflightMojo {

    private static final String OFFLINE
        = "This build is offline, so Maven skips the vulnerability scan and Extended verification "
            + "reports clean without reading one advisory. Run it without -o, and without offline "
            + "mode in settings.xml";

    @Override
    List<String> problems() {
        return this.session().isOffline() ? List.of(OFFLINE) : List.of();
    }
}
