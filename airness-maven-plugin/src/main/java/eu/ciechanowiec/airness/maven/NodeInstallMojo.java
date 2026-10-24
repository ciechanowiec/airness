package eu.ciechanowiec.airness.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Installs the Node and pnpm this Airness release pins, checked by SHA-256 and cached under the local Maven
 * repository, so nothing but Java and Maven has to be preinstalled.
 *
 * <p>Runs only in a module that declares a node project. Every later node goal runs on exactly this Node and
 * pnpm.
 */
@Mojo(name = "node-install", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class NodeInstallMojo extends AbstractNodeMojo {

    @Override
    public void execute() throws MojoExecutionException {
        if (this.prepares()) {
            this.tools();
        } else {
            this.getLog().debug("No node project to install Node for here");
        }
    }
}
