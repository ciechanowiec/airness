package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Payload stack's registration in this module: {@link Layer#PAYLOAD}, the one layer that refines
 * another, so a Payload project receives the Payload body of a file the node layer also ships.
 *
 * <p>Kept apart from the generic tests, which hold no stack name, so that removing the stack removes this file
 * with it and leaves the generic coverage whole.
 */
class PayloadRegistrationTest {

    @TempDir
    private Path directory;

    @Test
    void replacesTheNodeBodyOnlyWherePayloadIsPresent() {
        AssetCatalogue catalogue = new AssetFixture(this.directory, ".editorconfig\tPINNED\n")
            .manifest(Layer.NODE, "eslint.config.mjs\tPINNED\tNODE_PROJECT\n")
            .manifest(Layer.PAYLOAD, "eslint.config.mjs\tPINNED\tNODE_PROJECT\tOVERRIDES node\n")
            .catalogue();
        assertEquals(
            List.of(Layer.NODE),
            catalogue.select(AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)).stream().map(ManagedAsset::owner)
                .toList(),
            "a plain node project keeps the node body"
        );
        assertEquals(
            List.of(Layer.PAYLOAD),
            catalogue.select(AssetLocation.NODE_PROJECT, Set.of(Layer.NODE, Layer.PAYLOAD)).stream()
                .map(ManagedAsset::owner)
                .toList(),
            "a Payload project gets exactly one body, the Payload one"
        );
    }

    @Test
    void letsPayloadRefineNodeAlone() {
        assertTrue(Layer.PAYLOAD.refines(Layer.NODE), "a Payload project is a node project");
        assertEquals(
            List.of(),
            Stream.of(Layer.values())
                .filter(layer -> layer != Layer.NODE)
                .filter(Layer.PAYLOAD::refines)
                .toList(),
            "Payload replaces node files only, never generic or Java ones"
        );
        assertEquals(
            List.of(),
            Stream.of(Layer.values()).filter(layer -> layer.refines(Layer.GENERIC)).toList(),
            "nothing replaces a generic file, which root blocks share instead"
        );
    }
}
