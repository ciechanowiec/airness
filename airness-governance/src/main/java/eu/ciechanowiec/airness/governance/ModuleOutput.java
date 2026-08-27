package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;

/**
 * The two compiled output directories of one module, main and test.
 *
 * <p>Held together because every check that reads one reads the other to tell them apart. A class file
 * under both is production output that the tests also compile against, and a class file under the test
 * directory alone is test-only output. Passing the two separately invites a call site that supplies
 * them in the wrong order, which no compiler catches while both are a {@link Path}.
 *
 * @param main compiled production-output directory
 * @param test compiled test-output directory
 */
public record ModuleOutput(Path main, Path test) {
}
