package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A positional argument count or the names assigned by a fragment call.
 *
 * @param count  the number of supplied arguments
 * @param labels whether each slot is assigned a name and the static name when it can be read
 */
record FragmentArguments(int count, List<Label> labels) {

    FragmentArguments {
        labels = List.copyOf(labels);
    }

    boolean rejects(Collection<String> declared) {
        if (this.labels.stream().noneMatch(Label::assigned)) {
            return this.count != declared.size();
        }
        return !this.labels.stream().allMatch(Label::assigned) || this.namesReject(declared);
    }

    private boolean namesReject(Collection<String> declared) {
        List<String> known = this.labels.stream().flatMap(label -> label.name().stream()).toList();
        long missing = declared.stream().filter(name -> !known.contains(name)).count();
        long computed = this.labels.stream().filter(label -> label.name().isEmpty()).count();
        return missing > computed;
    }

    /**
     * One argument slot. A computed named label remains distinct from a positional argument.
     *
     * @param assigned whether the slot writes an assignment
     * @param name     the statically known label, absent for computed labels and positional slots
     */
    public record Label(boolean assigned, Optional<String> name) {
    }
}
