package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;

/**
 * Each nesting step of a YAML file is {@code indent_size} columns.
 *
 * <p>YAML reads its structure out of indentation, so a step of another width is not a matter of style: a
 * reader that assumes the declared width, as a hand-written one does, nests a key under the wrong parent. A
 * step is a line deeper than the line before it where that line opens a block, by ending in a key, in a bare
 * dash, or in either followed only by an anchor or a tag, and it is measured from where the opening line's
 * node starts. A sequence written at the column of its key is valid YAML and takes no step.
 *
 * <p>Lines whose indentation is not structure are passed over: blank lines and comments, the text of a
 * block scalar, the lines inside a flow collection that spans several, and the lines continuing a scalar,
 * which are deeper than a line that opened no block. A line that dedents is not measured, since the column
 * it returns to was measured where it was first reached. A document marker or a directive starts the reading
 * afresh.
 *
 * @param size      the declared {@code indent_size}
 * @param previous  the last line read as structure
 * @param flowDepth how many flow collections are open around the current line
 * @param deviation the first step of another width, once one has been found
 */
record YamlNesting(int size, YamlLine previous, int flowDepth, Optional<IndentationDeviation> deviation) {

    /**
     * The first nesting step of a width other than the declared one.
     *
     * @param lines the file's lines, in order
     * @param size  the declared {@code indent_size}
     * @return the first such step, or nothing when every step is that wide
     */
    static Optional<IndentationDeviation> first(List<String> lines, int size) {
        YamlNesting nesting = start(size);
        for (int index = 0; index < lines.size() && nesting.deviation().isEmpty(); index++) {
            nesting = nesting.read(lines.get(index), index + 1);
        }
        return nesting.deviation();
    }

    private static YamlNesting start(int size) {
        return new YamlNesting(size, YamlLine.START, 0, Optional.empty());
    }

    private YamlNesting read(String line, int number) {
        if (this.insideScalar(line)) {
            return this;
        }
        return this.flowDepth > 0 ? this.withinFlow(line) : this.significant(line, number);
    }

    private YamlNesting withinFlow(String line) {
        return new YamlNesting(
            this.size, this.previous, Math.max(0, this.flowDepth + YamlText.balance(line)), this.deviation
        );
    }

    private YamlNesting significant(String line, int number) {
        if (YamlText.insignificant(line)) {
            return this;
        }
        return YamlText.marker(line) ? start(this.size) : this.follow(YamlLine.of(line, number));
    }

    private boolean insideScalar(String line) {
        int threshold = this.previous.scalarThreshold();
        return threshold != YamlLine.NONE && (line.isBlank() || YamlText.indent(line) > threshold);
    }

    private YamlNesting follow(YamlLine current) {
        int step = current.indent() - this.previous.contentColumn();
        if (step > 0 && !this.previous.opensBlock()) {
            return this;
        }
        return new YamlNesting(this.size, current, current.flowDepth(), this.judged(current, step));
    }

    private Optional<IndentationDeviation> judged(YamlLine current, int step) {
        if (step <= 0 || step == this.size) {
            return Optional.empty();
        }
        return Optional.of(
            new IndentationDeviation(
                current.number(),
                "indent_size = %d, but line %d nests %d columns deeper than line %d".formatted(
                    this.size, current.number(), step, this.previous.number()
                )
            )
        );
    }
}
