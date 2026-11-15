package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Dockerfile writes a credential into its image when a build argument or an environment variable is named as one,
 * whichever form the instruction is written in.
 */
class DockerfileCredentialsTest {

    @Test
    void namesEveryCredentialABuildArgumentOrAnEnvironmentVariableDeclares() {
        String dockerfile = """
            # ARG COMMENTED_TOKEN
            FROM node:26.8.2-alpine
            ARG NPM_TOKEN
            arg GITHUB_TOKEN=unset
            ARG FIRST_SECRET SECOND=plain THIRD_KEY
            ARG NEXT_PUBLIC_MAPS_KEY
            ENV API_TOKEN=abc PORT=3000 \\
                SIGNING_KEY="a b"
            ENV DB_PASSWORD value with blanks
            env NODE_ENV=production
            ENV MONKEY_BUSINESS=false
            RUN <<EOF
            ENV HEREDOC_TOKEN=inside
            EOF
            """;
        assertEquals(
            List.of(
                new Located<>(3, "ARG NPM_TOKEN"),
                new Located<>(4, "ARG GITHUB_TOKEN"),
                new Located<>(5, "ARG FIRST_SECRET"),
                new Located<>(5, "ARG THIRD_KEY"),
                new Located<>(7, "ENV API_TOKEN"),
                new Located<>(7, "ENV SIGNING_KEY"),
                new Located<>(9, "ENV DB_PASSWORD")
            ),
            DockerfileCredentials.declared(dockerfile)
        );
    }

    @Test
    void passesADockerfileThatDeclaresNoCredential() {
        assertEquals(
            List.of(),
            DockerfileCredentials.declared("FROM eclipse-temurin:25-jre-alpine\nARG VERSION\nENV PORT=8080\n")
        );
    }
}
