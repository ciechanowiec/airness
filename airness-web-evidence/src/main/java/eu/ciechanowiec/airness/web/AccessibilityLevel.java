package eu.ciechanowiec.airness.web;

/**
 * The level a project holds one of the two accessibility rules to whose enhanced level costs a design the most:
 * the size of a pointer target and the contrast of text.
 *
 * <p>Every project is held to the AA level of both, and the browser sweep measures at the level the project's
 * {@code airness.accessibilityTargetSize} and {@code airness.accessibilityContrast} settings name. A Java test
 * that audits a page asks at the same levels by passing them to {@link AxeAudit#script(AccessibilityLevel,
 * AccessibilityLevel)}.
 */
public enum AccessibilityLevel {

    /**
     * A pointer target of 24 CSS pixels, or a smaller one with the room of a 24 pixel circle around it, and
     * text at 4.5:1, or 3:1 where it is large.
     */
    AA,

    /**
     * A pointer target of 44 CSS pixels with no allowance for the room around it, and text at 7:1, or 4.5:1
     * where it is large.
     */
    AAA
}
