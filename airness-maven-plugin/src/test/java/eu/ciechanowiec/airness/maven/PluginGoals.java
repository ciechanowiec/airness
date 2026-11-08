package eu.ciechanowiec.airness.maven;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;

/**
 * The goals of this plugin, as Maven reads them from the descriptor this module's build writes.
 *
 * <p>Tests read the descriptor rather than the annotations: it is what Maven itself binds, whatever spelling an
 * annotation is written in.
 */
@UtilityClass
final class PluginGoals {

    private static final Path DESCRIPTOR = Path.of("target", "classes", "META-INF", "maven", "plugin.xml");

    /**
     * Every goal the descriptor declares.
     *
     * @return the goals
     */
    @SneakyThrows
    static List<MojoDescriptor> all() {
        try (Reader descriptor = Files.newBufferedReader(DESCRIPTOR)) {
            return new PluginDescriptorBuilder().build(descriptor).getMojos();
        }
    }
}
