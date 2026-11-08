package eu.ciechanowiec.airness.governance;

/**
 * One classified property of one plugin, as {@link PluginProperties} reads it.
 *
 * @param plugin   the plugin's artifact identifier
 * @param property the user property its descriptor names
 * @param use      who owns the property
 */
record PluginProperty(String plugin, String property, PluginProperties.Use use) {
}
