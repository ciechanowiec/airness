# The application archive is built on the host by `mvn clean verify` and only unpacked here: a Maven
# stage inside the image would run the whole harness again without the Git history it reads.
#
# The first stage splits the archive along its layers, so a change to this project alone rewrites the
# last and smallest of them rather than the whole image.
FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048 AS layers
WORKDIR /layers
COPY target/starter-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
RUN addgroup -S -g 10001 starter && adduser -S -u 10001 -G starter starter
WORKDIR /application
COPY --from=layers --chown=10001:10001 /layers/extracted/dependencies/ ./
COPY --from=layers --chown=10001:10001 /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=10001:10001 /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=10001:10001 /layers/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["wget", "--quiet", "--spider", "http://127.0.0.1:8080/actuator/health"]
ENTRYPOINT ["java", "-jar", "application.jar"]
