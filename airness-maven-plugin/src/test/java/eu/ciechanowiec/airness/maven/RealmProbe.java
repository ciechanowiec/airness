package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportSource;
import java.nio.file.Path;
import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * Public test entry points loaded inside each plugin realm, without altering production method visibility.
 */
@UtilityClass
public final class RealmProbe {

    /**
     * Records one module's completion inside this realm.
     *
     * @param data    the shared session
     * @param module  the module id
     * @param awaited every participating module
     * @return whether this module owns closing
     */
    public static boolean complete(SessionData data, String module, Collection<String> awaited) {
        return CompletionSession.complete(data, module, awaited);
    }

    /**
     * Claims one repository operation inside this realm.
     *
     * @param data  the shared session
     * @param goal  the goal class
     * @param scope the operation's scope
     * @return whether this caller owns the operation
     */
    public static boolean firstRun(SessionData data, Class<?> goal, String scope) {
        return OncePerSession.firstRun(data, goal, scope);
    }

    /**
     * Captures the module's tree inside this realm.
     *
     * @param data  the shared session
     * @param root  the repository
     * @param scope the module
     */
    public static void snapshot(SessionData data, Path root, String scope) {
        TreeState.snapshot(data, root, scope);
    }

    /**
     * Reads the initial tree across realms.
     *
     * @param data the shared session
     * @param root the repository
     * @return whether the first tree is still current
     */
    public static boolean unchangedSinceStart(SessionData data, Path root) {
        return TreeState.unchangedSinceStart(data, root);
    }

    /**
     * Starts report provenance inside this realm.
     *
     * @param data the shared session
     * @param root the repository
     */
    public static void begin(SessionData data, Path root) {
        ReportSession.begin(data, root);
    }

    /**
     * Reconstructs the source identity in this realm's own report type.
     *
     * @param data the shared session
     * @return the invocation's original source
     */
    public static ReportSource source(SessionData data) {
        return ReportSession.source(data);
    }
}
