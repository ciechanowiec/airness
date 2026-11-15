package eu.ciechanowiec.airness.governance;

import java.util.List;

/**
 * One line with content of a compose file, with the keys of the mappings it is nested in.
 *
 * @param line   the one-based line
 * @param indent how deep its text starts, after the dash of a list item
 * @param path   the keys of the mappings it is nested in, outermost first
 * @param key    its key, empty for a list item that holds a scalar
 * @param value  its scalar, empty when it opens a mapping
 * @param item   whether it opens an item of a block list
 */
record ComposeLine(int line, int indent, List<String> path, String key, String value, boolean item) {

    private static final int SERVICE_DEPTH = 2;
    private static final int SERVICE_LIST_DEPTH = 3;

    ComposeLine {
        path = List.copyOf(path);
    }

    ComposeLine within(List<String> ancestors) {
        return new ComposeLine(this.line, this.indent, ancestors, this.key, this.value, this.item);
    }

    /**
     * Whether the line sits in the services mapping.
     *
     * @return whether its outermost key is {@code services}
     */
    boolean underServices() {
        return !this.path.isEmpty() && ComposeFile.SERVICES.equals(this.path.getFirst());
    }

    /**
     * Whether the line is a key of one service, such as its {@code ports} or {@code privileged}.
     *
     * @return whether it is nested in a service and nothing deeper
     */
    boolean atService() {
        return this.underServices() && this.path.size() == SERVICE_DEPTH;
    }

    /**
     * Whether the line belongs to the value of one key of a service, such as an item of its {@code ports}.
     *
     * @param serviceKey the key of the service
     * @return whether it is nested directly under that key of a service
     */
    boolean under(String serviceKey) {
        return this.underServices() && this.path.size() == SERVICE_LIST_DEPTH
            && serviceKey.equals(this.path.getLast());
    }

    /**
     * The service the line belongs to.
     *
     * @return the name of the service, or its key when the line names a service
     */
    String service() {
        return this.path.size() > 1 ? this.path.get(1) : this.key;
    }
}
