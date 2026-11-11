package eu.ciechanowiec.airness.spring;

/**
 * One error the application reported while a Spring test ran.
 *
 * @param source     the name of the logger that reported it, or the thread an uncaught exception ended
 * @param message    what was reported, with the exception it carried
 * @param declarable whether a test may declare the error expected, which only a logged error is
 */
record ServerError(String source, String message, boolean declarable) {

    // A logger named beneath another continues its name after a dot, the way a library names the loggers of its
    // components, or after a dollar sign, the way the runtime names a nested class.
    private static final String NAME_SEPARATORS = ".$";

    /**
     * Whether the logger of the given class reported this error, directly or through a logger named beneath it.
     *
     * @param logger the class a declaration names
     * @return true when this error was logged under that class's name
     */
    boolean loggedBy(Class<?> logger) {
        String name = logger.getName();
        boolean beneath = this.source.length() > name.length()
            && this.source.startsWith(name)
            && NAME_SEPARATORS.indexOf(this.source.charAt(name.length())) >= 0;
        return this.declarable && (this.source.equals(name) || beneath);
    }

    /**
     * The error as a failure message lists it.
     *
     * @return the source followed by the message
     */
    String described() {
        return "%s: %s".formatted(this.source, this.message);
    }
}
