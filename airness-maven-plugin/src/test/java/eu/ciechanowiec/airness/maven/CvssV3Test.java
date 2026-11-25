package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A CVSS 3 base vector rates an advisory by the specification's base equations, and anything else rates nothing.
 */
class CvssV3Test {

    private static final double CRITICAL_SCORE = 9.8;
    private static final double MAXIMUM_SCORE = 10.0;
    private static final double HIGH_SCORE = 7.5;
    private static final double MEDIUM_SCORE = 5.5;
    private static final double CHANGED_SCORE = 6.4;
    private static final double LOW_SCORE = 3.1;
    private static final double NO_SCORE = 0.0;

    @Test
    void scoresBaseVectorsAsTheSpecificationDoes() {
        assertEquals(Optional.of(CRITICAL_SCORE), CvssV3.score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(Optional.of(MAXIMUM_SCORE), CvssV3.score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"));
        assertEquals(Optional.of(HIGH_SCORE), CvssV3.score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N"));
        assertEquals(Optional.of(MEDIUM_SCORE), CvssV3.score("CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N"));
        assertEquals(Optional.of(CHANGED_SCORE), CvssV3.score("CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:C/C:L/I:L/A:N"));
        assertEquals(Optional.of(LOW_SCORE), CvssV3.score("CVSS:3.0/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N"));
        assertEquals(Optional.of(NO_SCORE), CvssV3.score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"));
    }

    @Test
    void ratesTheScoreOnTheQualitativeScale() {
        assertEquals(Optional.of("CRITICAL"), CvssV3.rating("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(Optional.of("HIGH"), CvssV3.rating("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N"));
        assertEquals(Optional.of("MEDIUM"), CvssV3.rating("CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N"));
        assertEquals(Optional.of("LOW"), CvssV3.rating("CVSS:3.0/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N"));
        assertEquals(Optional.of("NONE"), CvssV3.rating("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"));
    }

    @Test
    void ratesNothingForAVectorThatIsNoCompleteCvss3BaseVector() {
        assertEquals(Optional.empty(), CvssV3.rating("CVSS:3.1/AV:N"));
        assertEquals(
            Optional.empty(), CvssV3.rating("CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N")
        );
        assertEquals(Optional.empty(), CvssV3.rating("CVSS:3.1/AV:X/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(Optional.empty(), CvssV3.rating("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:X/C:H/I:H/A:H"));
        assertEquals(Optional.empty(), CvssV3.rating("AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
    }
}
