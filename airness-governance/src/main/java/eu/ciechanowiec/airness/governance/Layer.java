package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Optional;

/**
 * What a part of a repository contains, which decides the managed files and agent guidance it receives.
 *
 * <p>Selection reads layers rather than the parent chain, because the two answer different
 * questions. A Java module with a frontend inside it inherits the Java parent only, yet its frontend folder
 * needs the node files, and a monorepo root inherits the generic parent only, yet its {@code .gitignore}
 * needs a block from every stack beneath it.
 *
 * <p>The constants are declared from the most general to the most specific, and that order is the order
 * in which the managed blocks of one file are written. A constant names the layer it refines, if any, so a
 * stack's constant is the whole of its registration here.
 */
public enum Layer {

    /**
     * Present in every repository.
     */
    GENERIC(Optional.empty()),

    /**
     * Present where a module inherits {@code airness-parent-java}.
     */
    JAVA(Optional.empty()),

    /**
     * Present where a module declares a node project.
     */
    NODE(Optional.empty()),

    /**
     * Present where a module inherits {@code airness-parent-payload}, which is a node project as well.
     */
    PAYLOAD(Optional.of(NODE));

    private final Optional<Layer> refined;

    Layer(Optional<Layer> refined) {
        this.refined = refined;
    }

    /**
     * The name a manifest, a block marker and an asset directory use for this layer.
     *
     * @return the lower-case name
     */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this layer is a more specific form of another, and so may replace a file it owns.
     *
     * <p>A layer refines only what its constant names: a stack installed on a node project refines the
     * node layer. Nothing refines the generic layer, which shares its root files through blocks instead.
     *
     * @param other the layer that owns the file
     * @return whether this layer may override it
     */
    public boolean refines(Layer other) {
        return this.refined.filter(other::equals).isPresent();
    }

    /**
     * Reads the name a manifest uses.
     *
     * @param id the lower-case name
     * @return the layer
     * @throws IllegalArgumentException when no layer has that name
     */
    public static Layer of(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
