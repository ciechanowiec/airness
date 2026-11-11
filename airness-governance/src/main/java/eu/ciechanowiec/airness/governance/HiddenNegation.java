package eu.ciechanowiec.airness.governance;

/**
 * One {@code .gitignore} negation that git never applies, and the exclusion of a directory above its path that
 * hides it.
 *
 * @param negation  the negation whose removal changes nothing
 * @param exclusion the pattern that excludes the directory
 * @param directory the excluded directory, relative to the working tree root
 */
record HiddenNegation(IgnorePattern negation, IgnorePattern exclusion, String directory) {

    /**
     * The negation as a finding names it: where it stands, what hides it, and the repair, which depends on whether
     * the project can change the exclusion.
     *
     * @return the rendered offence
     */
    String offence() {
        String repair = this.exclusion.managed()
            ? "remove this line"
            : "exclude the directory's contents (%s/*) instead of the directory, or remove this line".formatted(
                this.directory
            );
        return "%s: %s - %s excludes the directory %s, and git never looks inside an excluded directory; %s"
            .formatted(
                this.negation.file() + ':' + this.negation.line(), this.negation.text(), this.exclusion.location(),
                this.directory, repair
            );
    }
}
