package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The secret scanner's configuration, read for the ways it can be turned off.
 *
 * <p>This file is seeded once and then belongs to the project, which is the right arrangement for the
 * one exception a project legitimately needs: an invalid credential committed on purpose as a fixture.
 * It is the wrong arrangement for everything else in it. A project that owns the file also owns
 * {@code useDefault}, and one line setting that to false leaves the scan running, finding nothing, and
 * passing. The standard says outright that a check the repository can turn off is not a rule.
 *
 * <p>So the file stays the project's and its shape does not. An exception names the rule it applies to
 * and the exact value it excuses, which is what an invalid fixture can always say about itself and what
 * a broad pattern never can. A path, a commit or a stopword excuses a whole file, a whole change or a
 * whole class of words, and none of those is one fixture.
 *
 * <p>Exactness is judged by how gitleaks matches, not by how the value reads. It searches an exception's
 * regex anywhere in its target, so a literal with no metacharacter still excuses every finding that
 * contains it: {@code PRIVATE KEY-----} would excuse every private key in the history. An exact value is
 * therefore anchored as {@code ^value$}, matched against the secret alone, and scoped to at least one
 * named rule, because gitleaks applies an exception with an empty {@code targetRules} to every rule.
 *
 * <p>An exact value is written as a literal string, {@code '''^value$'''}. Gitleaks decodes the escapes of
 * a double-quoted string before it compiles the regex, so {@code "^\\w$"} reaches it as {@code ^\w$}, a
 * class, while this reader would see an escaped backslash beside a letter. Judging what gitleaks compiles
 * would mean decoding every TOML escape here, so a double-quoted regex is refused instead, and a literal
 * string reads the same to both.
 *
 * <p>A line this reader cannot account for is reported rather than skipped. The grammar is a handful of
 * keys wide, so reading it needs no library, but a reader that ignored what it did not recognise would
 * pass a key it had never heard of in silence, and that silence is the thing being removed.
 * {@link EntryFileCheck} makes the same trade about {@code AGENTS.md}. So each table accepts only the keys
 * it is read for, a table this reader does not know is reported, and so is an inline table: written as
 * {@code allowlists = [{ ... }]} it is the same exception as the table form, and a reader that took it
 * for one opaque value would let it excuse whatever it names.
 */
public final class SecretScanConfiguration {

    private static final String TABLE_NAME = "[A-Za-z_][A-Za-z0-9_.-]*";
    /*
     * A table header or an array-of-tables header, spelled as two alternatives so that the brackets
     * have to balance. One alternative with an optional bracket at each end accepts [[name] and
     * [name]] as well, because the two are independent, and a header nobody can read is exactly the
     * line this class exists to refuse rather than wave through.
     */
    private static final Pattern TABLE = Pattern.compile(
        "^(?:\\[(" + TABLE_NAME + ")]|\\[\\[(" + TABLE_NAME + ")]])$"
    );
    private static final Pattern ENTRY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_-]*)\\s*=\\s*(.+)$");
    private static final String EXTEND = "extend";
    private static final String ALLOWLISTS = "allowlists";
    private static final String TARGET_RULES = "targetRules";
    private static final String REGEX_TARGET = "regexTarget";
    private static final String SECRET_TARGET = "secret";
    private static final String DESCRIPTION = "description";
    private static final String REGEXES = "regexes";
    private static final List<String> WHOLE_SWEEPS = List.of("paths", "commits", "stopwords");
    // The keys each table is read for. A sweep is known wherever it appears, because broad() names it.
    private static final Map<String, Set<String>> KNOWN_KEYS = Map.of(
        "", Set.of("title"),
        EXTEND, Set.of("useDefault", "disabledRules"),
        ALLOWLISTS, Set.of(DESCRIPTION, TARGET_RULES, REGEX_TARGET, REGEXES)
    );
    // Tables unscoped() refuses outright, whose keys therefore need no reading of their own.
    private static final Set<String> REFUSED_TABLES = Set.of("allowlist", "rules");
    private static final char INLINE_TABLE = '{';

    private final List<Statement> statements;
    private final List<String> unreadable;

    /**
     * Reads one configuration.
     *
     * @param content the decoded text of the configuration file
     */
    public SecretScanConfiguration(String content) {
        List<Statement> read = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        joined(content).forEach(line -> classify(line, read, rejected));
        read.stream().flatMap(statement -> unknown(statement).stream()).forEach(rejected::add);
        this.statements = List.copyOf(read);
        this.unreadable = List.copyOf(rejected);
    }

    /**
     * The verdicts over this configuration.
     *
     * @return one verdict per rule, each clean when the rule is satisfied
     */
    public List<Findings> findings() {
        return List.of(
            new Findings("The secret scan does not run the scanner's own rule set", this.defaults()),
            new Findings("The secret scan holds an exception that names no rule", this.unscoped()),
            new Findings("The secret scan holds an exception wider than one exact value", this.broad()),
            new Findings("The secret scan configuration holds a line the harness cannot read", this.unreadable)
        );
    }

    private List<String> defaults() {
        List<String> problems = new ArrayList<>();
        boolean shared = this.statements.stream()
            .anyMatch(statement -> statement.is(EXTEND, "useDefault") && "true".equals(statement.value()));
        Stream.of("[extend] useDefault must be declared true, so the shared rule set still runs")
            .filter(_ -> !shared)
            .forEach(problems::add);
        this.statements.stream()
            .filter(statement -> statement.is(EXTEND, "disabledRules"))
            .map(statement -> statement.at("disabledRules switches off a rule of the shared set"))
            .forEach(problems::add);
        return List.copyOf(problems);
    }

    private List<String> unscoped() {
        List<String> problems = new ArrayList<>();
        this.tables("allowlist")
            .map(statement -> statement.at("use [[allowlists]], whose entries scope to named rules"))
            .forEach(problems::add);
        this.tables("rules")
            .map(statement -> statement.at("a project rule can shadow one the shared set declares"))
            .forEach(problems::add);
        this.exemptions().stream()
            .filter(entry -> entry.lacks(TARGET_RULES) || entry.lacks(DESCRIPTION))
            .map(
                entry -> "line " + entry.number() + ": an exception needs both " + TARGET_RULES
                    + " and " + DESCRIPTION + ", so it says which rule it excuses and why"
            ).forEach(problems::add);
        this.statements.stream()
            .filter(statement -> statement.is(ALLOWLISTS, TARGET_RULES))
            .filter(statement -> SecretScanValue.strings(statement.value()).stream().allMatch(String::isBlank))
            .map(
                statement -> statement.at(
                    TARGET_RULES + " names no rule, and gitleaks applies such an exception to every rule"
                )
            ).forEach(problems::add);
        return List.copyOf(problems);
    }

    private List<String> broad() {
        List<String> problems = new ArrayList<>();
        this.statements.stream()
            .filter(statement -> WHOLE_SWEEPS.contains(statement.key()))
            .map(
                statement -> statement.at(
                    statement.key()
                        + " excuses a whole file, change or class of words rather than one value"
                )
            ).forEach(problems::add);
        this.statements.stream()
            .filter(statement -> statement.is(ALLOWLISTS, REGEX_TARGET))
            .filter(statement -> !List.of(SECRET_TARGET).equals(SecretScanValue.strings(statement.value())))
            .map(
                statement -> statement.at(
                    REGEX_TARGET + " = " + statement.value()
                        + " matches the value against more than the secret; omit it or declare \"secret\""
                )
            ).forEach(problems::add);
        this.statements.stream()
            .filter(statement -> statement.is(ALLOWLISTS, REGEXES))
            .filter(statement -> SecretScanValue.doubleQuoted(statement.value()))
            .map(
                statement -> statement.at(
                    REGEXES + " holds a double-quoted string, whose escapes gitleaks decodes before compiling"
                        + " the regex; write each value as a literal '''^value$''' string"
                )
            ).forEach(problems::add);
        this.statements.stream()
            .filter(statement -> statement.is(ALLOWLISTS, REGEXES))
            .flatMap(
                statement -> SecretScanValue.literals(statement.value()).stream()
                    .flatMap(
                        value -> SecretScanValue.inexactness(value).stream()
                            .map(reason -> statement.at('"' + value + "\" " + reason))
                    )
            ).forEach(problems::add);
        return List.copyOf(problems);
    }

    private Stream<Statement> tables(String name) {
        return this.statements.stream().filter(statement -> statement.isTable(name)).distinct();
    }

    private List<Exemption> exemptions() {
        Collection<Exemption> found = new ArrayList<>();
        this.statements.stream()
            .filter(statement -> ALLOWLISTS.equals(statement.table()))
            .forEach(statement -> collect(found, statement));
        return List.copyOf(found);
    }

    private static void collect(Collection<Exemption> found, Statement statement) {
        boolean header = statement.key().isEmpty();
        found.addAll(header ? List.of(new Exemption(statement.number(), new ArrayList<>())) : List.of());
        found.stream().skip(Math.max(0, found.size() - 1)).forEach(entry -> entry.add(statement.key()));
    }

    private static void classify(Line line, List<Statement> read, List<String> rejected) {
        statementOf(line, read)
            .ifPresentOrElse(read::add, () -> rejected.add("line " + line.number() + ": " + line.text()));
    }

    private static Optional<Statement> statementOf(Line line, SequencedCollection<Statement> read) {
        Matcher table = TABLE.matcher(line.text());
        return table.matches()
            ? Optional.of(new Statement(line.number(), tableName(table), "", ""))
            : entryOf(line, read);
    }

    // Whichever of the two header forms matched carries the name, and the other group is null.
    private static String tableName(MatchResult table) {
        return Optional.ofNullable(table.group(1)).orElseGet(() -> table.group(2));
    }

    /*
     * A value holding a brace outside its strings is an inline table, which carries keys of its own that
     * this reader would otherwise take for one opaque value, so the line is reported as unreadable.
     */
    private static Optional<Statement> entryOf(Line line, SequencedCollection<Statement> read) {
        Matcher entry = ENTRY.matcher(line.text());
        return entry.matches() && SecretScanValue.outsideStrings(entry.group(2)).indexOf(INLINE_TABLE) < 0
            ? Optional.of(new Statement(line.number(), enclosing(read), entry.group(1), entry.group(2).strip()))
            : Optional.empty();
    }

    private static String enclosing(SequencedCollection<Statement> read) {
        return read.reversed().stream()
            .filter(statement -> statement.key().isEmpty())
            .map(Statement::table)
            .findFirst()
            .orElse("");
    }

    /*
     * A header must name a table whose keys are listed or one unscoped() refuses. A key is judged only in a
     * table whose keys are listed, because a refused or unknown table has been reported once already.
     */
    private static Optional<String> unknown(Statement statement) {
        String table = statement.table();
        String key = statement.key();
        boolean listed = KNOWN_KEYS.containsKey(table);
        boolean header = key.isEmpty();
        boolean known = header
            ? listed || REFUSED_TABLES.contains(table)
            : !listed || WHOLE_SWEEPS.contains(key) || KNOWN_KEYS.getOrDefault(table, Set.of()).contains(key);
        String detail = header
            ? "[" + table + "] is not a table this reader knows"
            : key + " is not a key this reader knows in " + described(table);
        return known ? Optional.empty() : Optional.of(statement.at(detail));
    }

    private static String described(String table) {
        return table.isEmpty() ? "the top level" : "[" + table + "]";
    }

    /**
     * The meaningful lines, with a value spanning several lines joined into the line it began on. An
     * array of exact values is written one to a line, and reading each of those lines on its own would
     * make every one of them a line the reader could not account for.
     */
    private static List<Line> joined(String content) {
        List<Line> lines = new ArrayList<>();
        String[] raw = content.split("\n", -1);
        IntStream.range(0, raw.length)
            .forEach(index -> append(lines, index + 1, raw[index].strip()));
        return List.copyOf(lines);
    }

    private static void append(List<Line> lines, int number, String text) {
        boolean meaningful = carriesMeaning(text);
        boolean continuing = continuation(lines);
        lines.addAll(meaningful && !continuing ? List.of(new Line(number, text)) : List.of());
        lines.stream()
            .skip(Math.max(0, lines.size() - 1))
            .filter(_ -> continuing)
            .forEach(last -> lines.set(lines.size() - 1, last.plus(text)));
    }

    private static boolean carriesMeaning(String text) {
        return !text.isEmpty() && !text.startsWith("#");
    }

    private static boolean continuation(SequencedCollection<Line> lines) {
        return !lines.isEmpty() && unbalanced(lines.getLast().text());
    }

    private static boolean unbalanced(CharSequence text) {
        return text.chars().filter(character -> character == '[').count()
            > text.chars().filter(character -> character == ']').count();
    }

    private record Statement(int number, String table, String key, String value) {

        boolean is(String table, String key) {
            return table.equals(this.table) && key.equals(this.key);
        }

        boolean isTable(String name) {
            return name.equals(this.table) && this.key.isEmpty();
        }

        String at(String detail) {
            return "line " + this.number + ": " + detail;
        }
    }

    private record Exemption(int number, List<String> keys) {

        void add(String key) {
            this.keys.addAll(key.isEmpty() ? List.of() : List.of(key));
        }

        boolean lacks(String key) {
            return !this.keys.contains(key);
        }
    }

    private record Line(int number, String text) {

        Line plus(String more) {
            return new Line(this.number, this.text + ' ' + more);
        }
    }
}
