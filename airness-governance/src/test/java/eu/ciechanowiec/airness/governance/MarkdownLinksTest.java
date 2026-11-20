package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every link of a Markdown document leads where it says, and says where it leads.
 */
class MarkdownLinksTest {

    private static final String LINKS = "lead nowhere";
    private static final String WORDING = "do not say what they show";
    private static final String DOC = "doc.md";
    private static final String GUIDE = "guide.md";
    private static final String GUIDE_TEXT = "# Guide\n\nThe guide.\n";

    @Test
    void namesALinkOrAnImageToAFileNobodyCommitted() {
        Path root = new GitFixture("markdown-missing")
            .write(GUIDE, GUIDE_TEXT)
            .write("docs/notes.md", "Notes.\n")
            .write(
                DOC, """
                    [Guide](guide.md), [notes](docs/notes.md), [from the root](/docs/notes.md), [the docs](docs),
                    [gone](gone.md), ![a chart](chart.png), [site](https://example.com), [mail](mailto:a@b.example),
                    [cdn](//cdn.example/x.js), [defined][missing-target] and [the root](/).

                    [missing-target]: missing.md
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:2: 'gone.md' names no committed file", "doc.md:3: 'missing.md' names no committed file",
                "doc.md:2: 'chart.png' names no committed file"
            ),
            MarkdownCheckTest.offences(root, LINKS),
            "a missing file is named by an inline link, an image and a reference link, and nothing else is"
        );
    }

    @Test
    void holdsALinkToTheCaseOfTheCommittedFile() {
        Path root = new GitFixture("markdown-case")
            .write(GUIDE, GUIDE_TEXT)
            .write(DOC, "Read [the guide](Guide.md).\n")
            .root();
        assertEquals(
            List.of("doc.md:1: 'Guide.md' names no committed file"), MarkdownCheckTest.offences(root, LINKS),
            "a case-insensitive file system finding the file does not make the link work in a checkout"
        );
    }

    @Test
    void namesALinkThatLeavesTheRepositoryOrHasNoDestination() {
        Path root = new GitFixture("markdown-outside")
            .write(DOC, "Read [the outside](../outside.md) and [nothing]().\n")
            .root();
        assertEquals(
            List.of("doc.md:1: '../outside.md' leads outside the repository", "doc.md:1: the link has no destination"),
            MarkdownCheckTest.offences(root, LINKS)
        );
    }

    @Test
    void readsAFragmentAgainstTheAnchorsOfItsTarget() {
        Path root = new GitFixture("markdown-fragments")
            .write(
                "other.md", """
                    # Other

                    ## Linux

                    ### Usage

                    ## macOS

                    ### Usage

                    ## Hooks & `req` Context

                    <a id="Custom"></a>
                    """
            ).write("my notes.md", "# Notes\n")
            .write("Main.java", "class Main {}\n")
            .write(
                DOC, """
                    # Doc

                    [first](other.md#usage), [second](other.md#usage-1), [code](other.md#hooks--req-context),
                    [declared](other.md#custom), [spaced](my%20notes.md#notes), [query](other.md?plain=1#other),
                    [line](Main.java#L10), [here](#doc), [none](other.md#missing) and [lost](#lost).
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:5: 'other.md#missing' names no heading of other.md",
                "doc.md:5: '#lost' names no heading of doc.md"
            ),
            MarkdownCheckTest.offences(root, LINKS),
            "a heading, its counted repeat, an HTML id and an encoded path resolve, and a fragment into code is"
                + " not read"
        );
    }

    @Test
    void namesAReferenceWithoutADefinitionAndADefinitionNobodyUses() {
        Path root = new GitFixture("markdown-references")
            .write(GUIDE, GUIDE_TEXT)
            .write(
                DOC, """
                    Read [the guide][used], [guide], [full text][absent], [collapsed][] and `items[0][1]`.

                    [used]: guide.md
                    [guide]: guide.md
                    [spare]: guide.md
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:1: the reference link 'absent' has no definition",
                "doc.md:1: the reference link 'collapsed' has no definition",
                "doc.md:5: the definition 'spare' is never used"
            ),
            MarkdownCheckTest.offences(root, LINKS),
            "full and collapsed references without a definition are named, a code span is not read, and a shortcut"
                + " counts as a use"
        );
    }

    @Test
    void namesVagueLinkTextAndAnImageWithoutAltText() {
        Path root = new GitFixture("markdown-wording")
            .write(GUIDE, GUIDE_TEXT)
            .write("badge.png", new byte[] {1})
            .write(
                DOC, """
                    See [here](guide.md), [Click here.](guide.md), [](guide.md), [the guide](guide.md),
                    ![](badge.png), ![a badge](badge.png) and [![a badge](badge.png)](guide.md).
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:1: the link text 'here' does not name its destination",
                "doc.md:1: the link text 'click here' does not name its destination", "doc.md:1: the link has no text",
                "doc.md:2: the image has no alt text"
            ),
            MarkdownCheckTest.offences(root, WORDING),
            "a link wrapping an image is named by the image's alt text"
        );
    }
}
