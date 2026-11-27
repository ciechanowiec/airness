package eu.ciechanowiec.airness.governance;

import java.util.regex.Pattern;

/**
 * An action in another GitHub repository, as a {@code uses} key names it: {@code owner/repo[/path]@ref}.
 *
 * <p>Every part has passed the reader's alphabet, so none of them holds a space, a {@code ..} segment or any
 * other character that would change the meaning of an address or of a file path it is placed in.
 *
 * @param owner      the owner of the repository that holds the action
 * @param repository the repository
 * @param path       the directory of the action within the repository, empty for its root
 * @param revision   the commit, tag or branch the action is pinned to, which GitHub calls its ref
 */
public record ActionReference(String owner, String repository, String path, String revision) implements ActionUse {

    // Git names a commit by forty lowercase hexadecimal digits. GitHub resolves nothing shorter as one in a uses key.
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    /**
     * Whether the ref names a commit, whose content never changes, rather than a tag or a branch, which can be
     * moved to another commit at any time.
     *
     * @return whether the action this reference reads is fixed for good
     */
    public boolean immutable() {
        return COMMIT.matcher(this.revision).matches();
    }

    /**
     * The repository as GitHub names it, which a finding uses to say whose release to pin.
     *
     * @return {@code owner/repo}
     */
    public String slug() {
        return this.owner + '/' + this.repository;
    }

    /**
     * The reference as a workflow writes it.
     *
     * @return {@code owner/repo[/path]@ref}
     */
    @Override
    public String toString() {
        String directory = this.path.isEmpty() ? "" : '/' + this.path;
        return this.slug() + directory + '@' + this.revision;
    }
}
