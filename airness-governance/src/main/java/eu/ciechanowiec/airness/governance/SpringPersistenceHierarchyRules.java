package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Requires the schema decisions that only a complete local entity hierarchy reveals.
 *
 * <p>A source-local rule can validate a table or discriminator annotation once it is written, but it
 * cannot know whether an entity owns a table or shares the root table with its parent. This rule resolves
 * local parents before asking for those annotations and passes over an unresolved external entity parent,
 * where the source cannot prove ownership.
 */
@UtilityClass
final class SpringPersistenceHierarchyRules {

    private static final Pattern ENTITY = Pattern.compile("@Entity\\b");
    private static final Pattern TABLE = Pattern.compile("@Table\\b");
    private static final Pattern INHERITANCE = Pattern.compile("@Inheritance\\s*\\(([^)]*)\\)");
    private static final Pattern SINGLE = Pattern.compile("\\bSINGLE_TABLE\\b");
    private static final Pattern JOINED = Pattern.compile("\\bJOINED\\b");
    private static final Pattern TABLE_PER_CLASS = Pattern.compile("\\bTABLE_PER_CLASS\\b");
    private static final Pattern DISCRIMINATOR_COLUMN = Pattern.compile("@DiscriminatorColumn\\b");
    private static final Pattern DISCRIMINATOR_VALUE = Pattern.compile("@DiscriminatorValue\\b");
    private static final Pattern PRIMARY_KEY_JOIN = Pattern.compile("@PrimaryKeyJoinColumn\\b");
    private static final Pattern EXTENDS = Pattern.compile(
        "\\b(?:class|record)\\s+\\w+\\s+extends\\s+([\\w.$]+)"
    );
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final String DOT = ".";

    /**
     * Local entity hierarchy mappings that still rely on provider defaults.
     *
     * @param types module sources already read
     * @return one offence per missing hierarchy declaration
     */
    static List<String> implicitMappings(SpringTypes types) {
        Map<String, SpringTypes.Declared> declared = types.all().stream()
            .collect(Collectors.toMap(SpringPersistenceHierarchyRules::qualified, type -> type));
        List<EntityType> entities = types.all().stream()
            .filter(type -> ENTITY.matcher(type.code()).find())
            .map(type -> entity(type, declared))
            .toList();
        Map<String, EntityType> indexed = entities.stream()
            .collect(Collectors.toMap(EntityType::qualified, type -> type, (first, _) -> first, LinkedHashMap::new));
        List<String> offences = new ArrayList<>();
        entities.stream()
            .filter(type -> ownsTable(type, declared, indexed))
            .filter(type -> !TABLE.matcher(type.source().code()).find())
            .map(type -> offence(type.source(), "a root entity with an inherited Java base names no @Table"))
            .forEach(offences::add);
        entities.stream()
            .filter(type -> !children(type, entities).isEmpty())
            .forEach(root -> hierarchy(root, children(root, entities), offences));
        return List.copyOf(offences);
    }

    private static void hierarchy(EntityType root, List<EntityType> children, Collection<String> offences) {
        Optional<String> declared = INHERITANCE.matcher(root.source().code()).results()
            .findFirst()
            .map(found -> found.group(1));
        if (declared.isEmpty()) {
            offences.add(offence(root.source(), "an entity hierarchy declares no @Inheritance strategy"));
            return;
        }
        String strategy = declared.orElseThrow();
        if (SINGLE.matcher(strategy).find()) {
            require(root, DISCRIMINATOR_COLUMN, "a SINGLE_TABLE root names no discriminator column", offences);
            children.forEach(
                child -> require(
                    child, DISCRIMINATOR_VALUE, "a SINGLE_TABLE subtype names no discriminator value", offences
                )
            );
        } else if (JOINED.matcher(strategy).find()) {
            children.forEach(child -> joined(child, offences));
        } else if (TABLE_PER_CLASS.matcher(strategy).find()) {
            children.forEach(
                child -> require(child, TABLE, "a TABLE_PER_CLASS subtype names no table", offences)
            );
        }
    }

    private static void joined(EntityType child, Collection<String> offences) {
        require(child, TABLE, "a JOINED subtype names no table", offences);
        require(child, PRIMARY_KEY_JOIN, "a JOINED subtype names no primary-key join", offences);
    }

    private static void require(
        EntityType type, Pattern annotation, String problem, Collection<String> offences
    ) {
        if (!annotation.matcher(type.source().code()).find()) {
            offences.add(offence(type.source(), problem));
        }
    }

    private static boolean ownsTable(
        EntityType type,
        Map<String, SpringTypes.Declared> declared,
        Map<String, EntityType> entities
    ) {
        if (type.parent().isEmpty()) {
            return false;
        }
        String parent = type.parent().orElseThrow();
        return declared.containsKey(parent) && !entities.containsKey(parent);
    }

    private static List<EntityType> children(EntityType root, Collection<EntityType> entities) {
        return entities.stream().filter(descendantOf(root, entities)).toList();
    }

    private static Predicate<EntityType> descendantOf(
        EntityType root, Collection<EntityType> entities
    ) {
        Map<String, EntityType> indexed = entities.stream()
            .collect(Collectors.toMap(EntityType::qualified, type -> type));
        return candidate -> descendant(candidate.parent(), root.qualified(), indexed);
    }

    private static boolean descendant(
        Optional<String> parent, String root, Map<String, EntityType> indexed
    ) {
        return parent.filter(
            name -> root.equals(name) || Optional.ofNullable(indexed.get(name))
                .map(EntityType::parent)
                .filter(next -> descendant(next, root, indexed))
                .isPresent()
        ).isPresent();
    }

    private static EntityType entity(
        SpringTypes.Declared source, Map<String, SpringTypes.Declared> declared
    ) {
        Optional<String> parent = EXTENDS.matcher(source.code()).results()
            .findFirst()
            .map(found -> resolved(source, found.group(1), declared.keySet()));
        return new EntityType(source, qualified(source), parent);
    }

    private static String resolved(
        SpringTypes.Declared source, String written, Collection<String> declared
    ) {
        if (written.contains(DOT)) {
            return written;
        }
        return IMPORT.matcher(source.code()).results()
            .map(found -> found.group(1))
            .filter(name -> name.endsWith('.' + written))
            .findFirst()
            .orElseGet(
                () -> declared.stream()
                    .filter(name -> name.equals(packageName(source) + written))
                    .findFirst()
                    .orElse(packageName(source) + written)
            );
    }

    private static String qualified(SpringTypes.Declared source) {
        return packageName(source) + source.name();
    }

    private static String packageName(SpringTypes.Declared source) {
        return PACKAGE.matcher(source.code()).results()
            .findFirst()
            .map(found -> found.group(1) + '.')
            .orElse("");
    }

    private static String offence(SpringTypes.Declared source, String problem) {
        return source.source() + ": " + problem;
    }

    private record EntityType(
        SpringTypes.Declared source, String qualified, Optional<String> parent
    ) {
    }
}
