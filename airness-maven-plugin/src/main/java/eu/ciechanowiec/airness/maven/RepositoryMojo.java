package eu.ciechanowiec.airness.maven;

/**
 * A goal that reads the repository rather than the module, and therefore runs once however many modules
 * the reactor holds.
 *
 * <p>Without this, a five-module build would read the same working tree five times and print the same
 * findings five times. The repetition is not merely noise: a reader who has seen a finding four times
 * stops reading the fifth, and the one that differs is the one they miss.
 */
public abstract class RepositoryMojo extends GovernanceMojo {

    @Override
    protected final boolean applies() {
        return this.session().getTopLevelProject().equals(this.project());
    }
}
