package eu.ciechanowiec.airness.governance;

import java.util.Optional;

/**
 * Where the metadata of an action in another repository is read from, at the ref a workflow pins it to.
 *
 * <p>The check never reaches a network itself, so each test hands it the metadata it judges, and the build
 * hands it a reader of GitHub.
 */
@FunctionalInterface
public interface ActionMetadataSource {

    /**
     * The action's {@code action.yml}, or its {@code action.yaml} when it has no {@code action.yml}.
     *
     * @param action the action and the ref it is pinned to
     * @return the metadata as text, or nothing when the ref holds neither file, because the repository, the ref or
     *         the path does not exist or cannot be read without a credential
     * @throws IllegalStateException when the place the metadata is read from could not answer, which fails the
     *                               build rather than passing or reporting the action
     */
    Optional<String> metadata(ActionReference action);
}
