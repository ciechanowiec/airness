package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.util.List;

/**
 * What judging one image came back with.
 *
 * @param system   the system findings as reported, harness accounts included
 * @param language the language findings, still open to the node project's allowlist
 * @param repairs  the repairs, each printed while a finding it answers is open
 */
record ImageVerdict(List<ReportAdvisory> system, List<OsvAdvisory> language, List<ImageRepair> repairs) {

    ImageVerdict {
        system = List.copyOf(system);
        language = List.copyOf(language);
        repairs = List.copyOf(repairs);
    }

    /**
     * The verdict of an image that holds no finding.
     *
     * @return a verdict with nothing in it
     */
    static ImageVerdict none() {
        return new ImageVerdict(List.of(), List.of(), List.of());
    }
}
