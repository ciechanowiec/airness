package eu.ciechanowiec.airness.spring;

import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.context.MessageSource;

/**
 * A qualified standard resolver and the resources it can read.
 *
 * @param source its actual message source
 * @param prefix the classpath prefix
 * @param suffix the configured suffix
 * @param loader the application class loader
 */
record SpringMessageResolution(MessageSource source, String prefix, String suffix, ClassLoader loader) {

    List<SpringMessageResult> assess(List<SpringMessageInput> references) {
        List<SpringMessageInput> relevant = references.stream().filter(this::reaches).toList();
        SpringMessageLookup lookup = new SpringMessageLookup(this.source);
        List<SpringMessageResult> results = relevant.stream().map(reference -> check(lookup, reference))
            .flatMap(Optional::stream).toList();
        Stream<SpringMessageResult> coverage = Stream.of(
            new SpringMessageResult("assessed", Integer.toString(relevant.size()))
        );
        return Stream.concat(Stream.concat(coverage, results.stream()), outside(references.size() - relevant.size()))
            .toList();
    }

    private boolean reaches(SpringMessageInput reference) {
        return reference.resource().startsWith(this.prefix) && reference.resource().endsWith(this.suffix)
            && Optional.ofNullable(this.loader.getResource(reference.resource())).isPresent();
    }

    private static Optional<SpringMessageResult> check(SpringMessageLookup lookup, SpringMessageInput reference) {
        try {
            return lookup.missing(reference.key()) ? Optional.of(
                new SpringMessageResult("missing", reference.encoded())
            )
                : Optional.empty();
        } catch (IllegalArgumentException | MissingResourceException exception) {
            return Optional.of(
                new SpringMessageResult(
                    "error", reference.location() + ": message lookup raised "
                        + exception.getClass().getName()
                )
            );
        }
    }

    private static Stream<SpringMessageResult> outside(int count) {
        return count == 0 ? Stream.empty() : Stream.of(
            SpringMessageResult.unassessed(
                count + " reference(s) outside the active classpath template resolver"
            )
        );
    }
}
