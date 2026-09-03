package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A role named in a security annotation is a string the engine compares with what a caller holds. One
 * nobody is granted denies or admits every caller, so each is read against the enum that declares them.
 */
class SpringRoleRulesTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));
    private static final String ROLE = "src/main/java/sample/Role.java";
    private static final String ROOMS = "src/main/java/sample/Rooms.java";
    private static final String ROOMS_TEST = "src/test/java/sample/RoomsTest.java";

    private static final String ENUM = """
        package sample;

        enum Role implements GrantedAuthority {

            ADMIN("configures"),
            SALES,
            @Deprecated VIEWER {
                @Override
                public String getAuthority() {
                    return name();
                }
            };

            private static final String DIVIDER = "ROLE_";

            private final String purpose;

            Role() {
                this("reads");
            }

            Role(String purpose) {
                this.purpose = purpose;
            }

            @Override
            public String getAuthority() {
                return DIVIDER + name();
            }
        }
        """;

    private static final String PLAIN_ENUM = """
        package sample;

        enum Role {
            ADMIN
        }
        """;

    private static String guarded(String annotation) {
        return """
            package sample;

            class Rooms {

                %s
                public void rename() {
                }
            }
            """.formatted(annotation);
    }

    private static List<String> offences(String name, String... files) {
        GitFixture fixture = new GitFixture(name);
        for (int index = 0; index < files.length; index += 2) {
            fixture = fixture.write(files[index], files[index + 1]);
        }
        Path root = fixture.root();
        return SpringRoleRules.undeclaredRoles(SpringTypes.over(root, JavaSources.under(root, ROOTS)));
    }

    @Test
    void acceptsARoleTheEnumDeclares() {
        List<String> offences = offences(
            "roles-declared", ROLE, ENUM, ROOMS, guarded("@PreAuthorize(\"hasRole('ADMIN')\")")
        );

        assertEquals(List.of(), offences, "ADMIN is a constant of the enum");
    }

    @Test
    void acceptsAnAuthorityCarryingThePrefix() {
        List<String> offences = offences(
            "roles-prefixed", ROLE, ENUM, ROOMS, guarded("@PreAuthorize(\"hasAuthority('ROLE_SALES')\")")
        );

        assertEquals(List.of(), offences, "the engine accepts the prefixed spelling of a declared role");
    }

    @Test
    void readsEveryRoleOfAListAndAConstantDeclaredWithABody() {
        List<String> offences = offences(
            "roles-listed", ROLE, ENUM, ROOMS,
            guarded("@PreAuthorize(\"hasAnyRole('ADMIN', 'SALES', 'VIEWER', 'AUDITOR')\")")
        );

        assertEquals(1, offences.size(), "three of the four are declared, the constant with a body included");
        assertTrue(offences.getFirst().contains("'AUDITOR'"), "the offence names the undeclared role");
        assertTrue(offences.getFirst().contains("line 5"), "the offence points at the annotation");
    }

    @Test
    void reportsARoleTheEnumDoesNotDeclare() {
        List<String> offences = offences(
            "roles-undeclared", ROLE, ENUM, ROOMS, guarded("@PreAuthorize(\"hasRole('ADMINISTRATOR')\")")
        );

        assertEquals(1, offences.size(), "nobody is ever granted ADMINISTRATOR");
        assertTrue(offences.getFirst().contains("no constant of Role"), "the offence names the enum read");
    }

    @Test
    void reportsARoleNamedWhereNoEnumDeclaresAny() {
        List<String> offences = offences(
            "roles-no-set", ROLE, PLAIN_ENUM, ROOMS, guarded("@PreAuthorize(\"hasRole('ADMIN')\")")
        );

        assertEquals(1, offences.size(), "an enum implementing nothing is not the declaration asked for");
        assertTrue(
            offences.getFirst().contains("declare the roles as an enum implementing GrantedAuthority"),
            "the offence says what declaration is missing"
        );
    }

    @Test
    void acceptsAnExpressionNamingNoRole() {
        List<String> offences = offences(
            "roles-none", ROOMS, guarded("@PreAuthorize(\"isAuthenticated() and #name != 'ADMIN'\")")
        );

        assertEquals(List.of(), offences, "a quoted value outside a role call is not a role");
    }

    @Test
    void readsTheAnnotationsThatListRolesWithoutAnExpression() {
        List<String> offences = offences(
            "roles-listed-plainly", ROLE, ENUM, ROOMS,
            guarded("@Secured({\"ROLE_ADMIN\", \"ROLE_GUEST\"})\n    @RolesAllowed(\"SALES\")")
        );

        assertEquals(1, offences.size(), "the two declared roles pass and the guest does not");
        assertTrue(offences.getFirst().contains("'ROLE_GUEST'"), "the offence names the listed role");
    }

    @Test
    void readsTheRolesATestGrantsItsCaller() {
        List<String> offences = offences(
            "roles-mocked", ROLE, ENUM, ROOMS_TEST,
            """
                package sample;

                class RoomsTest {

                    @WithMockUser(username = "ann", roles = {"ADMIN", "OWNER"}, authorities = "ROLE_SALES")
                    void renames() {
                    }
                }
                """
        );

        assertEquals(1, offences.size(), "a test granting a role the application never grants proves nothing");
        assertTrue(offences.getFirst().contains("'OWNER'"), "the offence names the granted role");
    }

    @Test
    void readsTheRoleSetFromProductionAlone() {
        List<String> offences = offences(
            "roles-test-enum", "src/test/java/sample/Role.java", ENUM, ROOMS,
            guarded("@PreAuthorize(\"hasRole('ADMIN')\")")
        );

        assertEquals(1, offences.size(), "an enum a test declares grants nothing to a production caller");
    }
}
