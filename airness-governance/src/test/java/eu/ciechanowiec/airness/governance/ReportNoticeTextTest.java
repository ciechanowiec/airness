package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportNoticeTextTest {

    @Test
    void removesFixedWidthBreaksInsideAParagraphOnly() {
        String shipped = """
            > Permission is hereby granted, free of charge, to any
            > person obtaining a copy of this software, without non-
            > exclusive   restriction.

            PREAMBLE
            The licenses for most software are designed
            to take away your freedom.

            Copyright 2026 Example Authors
            Copyright (c) 2025 Other Authors
            License: MIT
            1. Definitions follow.
            """;
        assertEquals(
            """
                Permission is hereby granted, free of charge, to any person obtaining a copy of this software, \
                without non-exclusive restriction.

                PREAMBLE
                The licenses for most software are designed to take away your freedom.

                Copyright 2026 Example Authors
                Copyright (c) 2025 Other Authors
                License: MIT
                1. Definitions follow.""",
            ReportNoticeText.reflow(shipped.replace("\n", "\r\n"))
        );
    }

    @Test
    void drawsHeadingsAndRulesAndSetsAddressesFlushLeft() {
        String html = ReportNoticeText.render(
            ReportNoticeText.reflow(
                """
                    ## first-library
                    ---
                    Plain words <b>stay</b> text.

                    See https://example.com/licenses/plain-text-license for details.

                    Justified prose.
                    """
            )
        );
        assertTrue(html.contains("<div class=\"license-entry\"><p class=\"license-heading\">first-library</p><hr>"));
        assertTrue(html.contains("<p>Plain words &lt;b&gt;stay&lt;/b&gt; text.</p></div>"));
        assertTrue(html.contains("<p class=\"ragged\">See https://example.com/licenses/plain-text-license"));
        assertTrue(html.contains("<p>Justified prose.</p>"));
    }

    @Test
    void keepsALineBreakAfterAFieldOrARule() {
        assertEquals("Name: example\nnext line", ReportNoticeText.reflow("Name: example\nnext line"));
        assertEquals("***\nnext line", ReportNoticeText.reflow("***\nnext line"));
        assertEquals("## Title\nnext line", ReportNoticeText.reflow("## Title\nnext line"));
    }
}
