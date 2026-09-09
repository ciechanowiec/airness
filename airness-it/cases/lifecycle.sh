#!/usr/bin/env sh

expect_analysis_before_qodana() {
    for analysis_goal in 'checkstyle:[^ ]+:check' 'pmd:[^ ]+:check' 'pmd:[^ ]+:cpd-check'; do
        expect_before "$1" "lifecycle: $analysis_goal precedes Qodana in $1" \
            "--- $analysis_goal " '--- airness:[^ ]+:qodana [(]airness-qodana[)]'
    done
    expect_before "$1" "lifecycle: Checkstyle precedes PMD in $1" \
        '--- checkstyle:[^ ]+:check ' '--- pmd:[^ ]+:check '
    expect_before "$1" "lifecycle: PMD precedes CPD in $1" \
        '--- pmd:[^ ]+:check ' '--- pmd:[^ ]+:cpd-check '
}

expect_static_refusal() {
    run_maven "$2" analysis "$1" clean verify -Pextended
    expect_exit "$2" "lifecycle: $2 fails enforcement" 1
    expect_match "$2" "lifecycle: $2 reports its intended source violation" "$3"
    expect_no_match "$2" "lifecycle: $2 stops before Qodana starts" \
        'airness:[^ ]+:qodana [(]airness-qodana[)]'
}

run_analysis_lifecycle() {
    new_consumer lifecycle-checkstyle
    perl -0pi -e 's/\n}\n$/\n\n}\n/' "$consumer_directory/src/test/java/com/example/ExampleTest.java"
    expect_static_refusal "$consumer_directory" checkstyle_order \
        'Empty lines before a closing brace are not allowed'

    new_consumer lifecycle-pmd
    cat > "$consumer_directory/src/test/java/com/example/ExampleTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void readsTheProductionValue() {
        String value = Integer.toString(new Example().value());
        if ("1".equals(value)) {
            assertEquals(1, value.length());
        }
    }
}
JAVA
    prepare_maven setup_lifecycle_pmd setup "$consumer_directory" --quiet process-resources -Pformat
    expect_static_refusal "$consumer_directory" pmd_order 'Rule:AvoidLiteralsInIfCondition'

    new_consumer lifecycle-cpd
    cat > "$consumer_directory/src/test/java/com/example/ExampleTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void buildsTheFirstSequence() {
        Example example = new Example();
        int first = example.value();
        List<Integer> actual = List.of(first, first + 1, first + 2, first + 3, first + 4);
        assertEquals(List.of(1, 2, 3, 4, 5), actual);
        assertEquals(5, actual.size());
        assertEquals(1, actual.getFirst());
        assertEquals(5, actual.getLast());
        assertTrue(actual.contains(3));
    }

    @Test
    void buildsTheSecondSequence() {
        Example example = new Example();
        int first = example.value();
        List<Integer> actual = List.of(first, first + 1, first + 2, first + 3, first + 4);
        assertEquals(List.of(1, 2, 3, 4, 5), actual);
        assertEquals(5, actual.size());
        assertEquals(1, actual.getFirst());
        assertEquals(5, actual.getLast());
        assertTrue(actual.contains(3));
    }
}
JAVA
    prepare_maven setup_lifecycle_cpd setup "$consumer_directory" --quiet process-resources -Pformat
    expect_static_refusal "$consumer_directory" cpd_order 'has found [0-9]+ duplication'
}
