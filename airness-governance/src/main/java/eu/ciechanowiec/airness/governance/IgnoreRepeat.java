package eu.ciechanowiec.airness.governance;

/**
 * One ignore line that repeats a declaration already applied, and the declaration it repeats.
 *
 * @param repeat      the line whose removal changes nothing
 * @param declaration the earlier line that already applies the same pattern
 * @param inherited   whether the declaration stands in a {@code .gitignore} of a directory above the repeat's
 */
record IgnoreRepeat(IgnorePattern repeat, IgnorePattern declaration, boolean inherited) {

    /**
     * The repeat as a finding names it: where it stands, what it repeats, why the repeat changes nothing, and the
     * repair.
     *
     * @return the rendered offence
     */
    String offence() {
        String reach = this.inherited
            ? ", an un-anchored pattern that already applies in every directory beneath it,"
            : "";
        return "%s: %s - repeats %s%s and no line between them reverses it; remove this line".formatted(
            this.repeat.file() + ':' + this.repeat.line(), this.repeat.text(), this.declaration.location(), reach
        );
    }
}
