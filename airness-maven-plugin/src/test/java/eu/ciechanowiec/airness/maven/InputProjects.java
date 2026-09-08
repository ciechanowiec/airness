package eu.ciechanowiec.airness.maven;

import java.io.StringReader;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

@UtilityClass
final class InputProjects {

    static MavenProject project(Path root) {
        MavenProject project = new MavenProject();
        project.setFile(root.resolve("pom.xml").toFile());
        project.setArtifactId("sample");
        project.getModel().setBuild(new Build());
        project.getBuild().setDirectory(root.resolve("target").toString());
        project.addCompileSourceRoot(root.resolve("src/main/java").toString());
        project.addTestCompileSourceRoot(root.resolve("src/test/java").toString());
        project.addResource(resource(root.resolve("src/main/resources")));
        project.addTestResource(resource(root.resolve("src/test/resources")));
        return project;
    }

    static Resource resource(Path directory) {
        Resource resource = new Resource();
        resource.setDirectory(directory.toString());
        return resource;
    }

    static Plugin plugin(MavenProject project, String artifact, String configuration) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId(artifact);
        plugin.setConfiguration(xml(configuration));
        project.getBuild().addPlugin(plugin);
        return plugin;
    }

    @SneakyThrows
    static Xpp3Dom xml(String content) {
        return Xpp3DomBuilder.build(new StringReader(content));
    }
}
