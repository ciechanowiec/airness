package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.InstructionReferenceCheck;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Every backticked path and type name in the root instruction file resolves to something this repository
 * holds.
 *
 * <p>The sources read are every module's, not this module's, because the instruction file describes the
 * repository. A name it uses may be declared three modules away, and a check that read only one of them
 * would report that name as invented.
 */
@Mojo(name = "instruction-references", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class InstructionReferencesMojo extends RepositoryMojo {

    /**
     * The repository-relative name of the root instruction file, or {@code NONE}.
     *
     * <p>A project that has one and mistypes the name here gets a failure, because the check throws
     * rather than passing over a document it could not open. A project that has none says so, and the
     * difference between those two is the whole reason the second is spelled rather than left blank.
     */
    @Parameter(property = "airness.instruction.file", defaultValue = "AGENTS.md")
    private String instructionFile;

    /**
     * Repository-relative files and directories whose text names rules and inspections, comma-separated.
     *
     * <p>A rule name is a name the instructions may honestly use, so wherever the rules are configured is
     * a source of resolvable names alongside the Java sources. A project whose analysis configuration is
     * inherited from a jar has none of these, and the empty default is right for it.
     */
    @Parameter(property = "airness.configuration.paths")
    private String configurationPaths;

    @Override
    protected List<Findings> findings() {
        return Sentinel.declared(this.instructionFile).map(this::checked).orElseGet(this::none);
    }

    private List<Findings> checked(String instructions) {
        return new InstructionReferenceCheck(
            this.repositoryRoot(), Path.of(instructions), this.configuration(), this.reactorSourceRoots()
        ).findings();
    }

    private List<Findings> none() {
        this.getLog().info("This project declares no instruction file, so there is none to read");
        return List.of();
    }

    private List<Path> configuration() {
        return Sentinel.optional(this.configurationPaths).stream().map(Path::of).toList();
    }
}
