package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * The files the harness owns, as the working tree being built is held to them.
 *
 * <p>A governed project is held to the shipped bytes. The harness's own tree is held to the same bytes
 * followed by the self additions the catalogue ships, so each clone of the harness installs into a local
 * Maven repository of its own. Every goal that checks or writes a pinned file asks here, so the check, the
 * repair and the repository policy never disagree about which reading applies.
 */
@UtilityClass
final class TreeCatalogue {

    /**
     * Reads the catalogue for one working tree.
     *
     * @param current the module being built
     * @param root    the working tree root
     * @return the catalogue of the harness's own tree when the tree is the harness's, else the shipped one
     */
    static AssetCatalogue of(MavenProject current, Path root) {
        AssetCatalogue shipped = new AssetCatalogue(TreeCatalogue.class.getClassLoader());
        return RepositoryProjects.selfTree(current, root) ? shipped.selfTree() : shipped;
    }
}
