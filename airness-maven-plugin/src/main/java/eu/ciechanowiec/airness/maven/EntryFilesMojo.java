package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EntryFileCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Every file an agent tool reads under a name of its own points at the one instruction file and states
 * nothing besides.
 */
@Mojo(name = "entry-files", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class EntryFilesMojo extends RepositoryMojo {

    /**
     * The repository-relative name of the root instruction file, or {@code NONE}.
     */
    @Parameter(property = "airness.instruction.file", defaultValue = "AGENTS.md")
    private String instructionFile;

    /**
     * The entry files this project ships, comma-separated, or {@code NONE}.
     *
     * <p>There is no default. An empty list would pass by checking nothing, and would read in a build
     * log exactly as a project with three correct entry files reads. Stating {@code NONE} makes having
     * none a decision on the record rather than an oversight nobody can see.
     */
    @Parameter(property = "airness.entry.files")
    private String entryFiles;

    @Override
    protected List<Findings> findings() {
        return this.declared()
            .map(names -> new EntryFileCheck(this.repositoryRoot(), this.instructionFile, names).findings())
            .orElseGet(List::of);
    }

    private Optional<List<String>> declared() {
        Optional<List<String>> names = Sentinel.declaredList(this.entryFiles)
            .filter(entries -> Sentinel.declared(this.instructionFile).isPresent());
        this.announce(names.isEmpty());
        return names;
    }

    private void announce(boolean nothingToRead) {
        if (nothingToRead) {
            this.getLog().info("This project declares no entry file, so there is none to read");
        }
    }
}
