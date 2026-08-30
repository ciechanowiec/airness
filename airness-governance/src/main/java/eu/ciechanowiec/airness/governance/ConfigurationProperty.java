package eu.ciechanowiec.airness.governance;

/**
 * One entry of the configuration metadata a dependency publishes about itself.
 *
 * <p>Spring Boot writes {@code META-INF/spring-configuration-metadata.json} into every jar that
 * contributes settings, naming each key it binds, the type it binds into, and whether it has been
 * deprecated. That file is the supplier's own statement about its own keys, which is what makes it the
 * right thing to judge a project's configuration against: the alternative is a list kept here, which
 * would be a second opinion about somebody else's artifact and would age the moment they released.
 *
 * <p>The record is built by the Maven plugin, which is the layer that can reach a resolved artifact, and
 * read here, which is the layer that holds the rules.
 *
 * @param name        the key, in the kebab-case spelling the metadata writes it in
 * @param type        the fully qualified type the key binds into, empty when the metadata omits it
 * @param group       whether this entry names a prefix rather than a key that can be set on its own
 * @param deprecation what the supplier says about withdrawing the key
 */
public record ConfigurationProperty(String name, String type, boolean group, Deprecation deprecation) {

    /**
     * What a supplier says about a key it is withdrawing.
     *
     * <p>An absent statement and an empty one are different. A metadata entry carrying no deprecation at
     * all is a key in good standing, while one carrying an empty deprecation object is deprecated at the
     * level the specification defaults to. The two are told apart by {@link #level()}, which is empty
     * only in the first case, so the reader that builds this must supply the default rather than leave
     * the field as the file left it.
     *
     * @param level       {@code error} when the key is no longer bound, {@code warning} when it still is,
     *                    and empty when the key is not deprecated
     * @param replacement the key to write instead, empty when the supplier names none
     * @param reason      why the key was withdrawn, empty when the supplier gives none
     * @param since       the release that withdrew it, empty when the supplier names none
     */
    public record Deprecation(String level, String replacement, String reason, String since) {

        private static final String UNBOUND = "error";

        /**
         * Whether the supplier has said anything about withdrawing this key.
         *
         * @return whether the key is deprecated at any level
         */
        public boolean deprecated() {
            return !this.level().isEmpty();
        }

        /**
         * Whether the container has stopped reading the key rather than merely advised against it.
         *
         * @return whether the key is no longer bound
         */
        public boolean unbound() {
            return UNBOUND.equals(this.level());
        }
    }
}
