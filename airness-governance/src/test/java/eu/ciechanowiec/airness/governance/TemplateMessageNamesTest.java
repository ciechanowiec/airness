package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateMessageNamesTest {

    @Test
    void readsBareQuotedAndParameterizedNames() {
        assertEquals(
            List.of("room.name", "room.code", "welcome"), TemplateMessageNames.in(
                "#{room.name} + #{'room.code'} + #{welcome(${person.name})}"
            )
        );
    }

    @Test
    void ignoresComputedNamesAndQuotedExamples() {
        assertEquals(
            List.of("real.key"), TemplateMessageNames.in(
                "'#{example}' + #{${computed}} + #{'prefix.' + suffix} + #{real.key}"
            )
        );
    }

    @Test
    void readsNestedMessagesWithoutExecutingArguments() {
        assertEquals(
            List.of("welcome", "unit"), TemplateMessageNames.in(
                "#{welcome(${person}, #{unit}, ') }')}"
            )
        );
    }

    @Test
    void ignoresUnclosedMessagesAndUnbalancedArguments() {
        assertTrue(TemplateMessageNames.in("#{unclosed").isEmpty());
        assertTrue(TemplateMessageNames.in("#{wrong())}").isEmpty());
        assertTrue(TemplateMessageNames.in("#{wrong('unterminated)}").isEmpty());
    }

    @Test
    void followsEscapedQuotesInExamples() {
        assertEquals(List.of("actual"), TemplateMessageNames.in("'It\\'s #{example}' + #{actual}"));
        assertTrue(TemplateMessageNames.in("'unclosed #{example}").isEmpty());
    }
}
