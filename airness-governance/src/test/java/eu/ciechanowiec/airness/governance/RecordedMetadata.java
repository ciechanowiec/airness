package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata by reference as a workflow writes it, recording every action asked for.
 */
final class RecordedMetadata implements ActionMetadataSource {

    private final Map<String, String> metadata;
    private final List<ActionReference> asked;

    RecordedMetadata(Map<String, String> metadata) {
        this.metadata = Map.copyOf(metadata);
        this.asked = new ArrayList<>();
    }

    @Override
    public Optional<String> metadata(ActionReference action) {
        this.asked.add(action);
        return Optional.ofNullable(this.metadata.get(action.toString()));
    }

    List<ActionReference> asked() {
        return List.copyOf(this.asked);
    }
}
