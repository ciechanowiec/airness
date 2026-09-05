package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * What Airness refuses by name, whatever a manifest declares as its licence.
 *
 * <p>The licence allowlist judges a Maven artifact by the identifier its own pom carries, and that is
 * the wrong question for four families. A driver is permissively licensed while the only server it can
 * reach is not: the MongoDB driver is Apache 2.0 and the MongoDB server is SSPL. A package downloads a
 * binary under terms its pom never mentions: the embedded MongoDB fetches the server itself. A container
 * image has no pom at all, so nothing judged it: a compose file could pull MinIO or Redis 8 through a
 * harness that refuses an AGPL jar. And the JDK running the build is judged by nothing, though Oracle
 * JDK and Oracle GraalVM are not open source.
 *
 * <p>Every entry names the reason and the replacement, because a refusal that stops a build without
 * saying what to use instead sends the reader to the same search this table already did. Where a
 * project turned non-open at a known release, the entry carries that floor and earlier releases are
 * left to the licence allowlist. No project setting widens or narrows the list: a rule a project can
 * edit is not a rule.
 *
 * <p>The list stops at software a Java project reaches. A SaaS client such as a Sentry or a cloud SDK is
 * not here, since the service it reaches is nobody's to self-host, and neither is a client that speaks
 * to an open server as readily as to a closed one, such as Jedis, Lettuce, Spring Vault, and the
 * low-level Elasticsearch REST client.
 */
@UtilityClass
final class BlocklistEntries {

    private static final String MONGODB
        = "the MongoDB server is SSPL, which is not an open-source licence, and this exists only to reach it";
    private static final String EMBEDDED_MONGODB = "this downloads the SSPL MongoDB server binary at test time";
    private static final String POSTGRES_STARTER
        = "PostgreSQL through spring-boot-starter-data-jpa and the postgres image";
    private static final String POSTGRES_IMAGE = "the postgres image";
    private static final String ANY = "*";
    private static final String TESTCONTAINERS = "org.testcontainers";
    private static final String VAADIN_GROUP = "com.vaadin";
    private static final String POSTGRES_MODULE = "org.testcontainers:postgresql";
    private static final String REDIS
        = "Redis 7.4 onward is RSALv2, SSPLv1 or AGPLv3, none of which is a permissive open-source licence";
    private static final String VALKEY = "valkey/valkey, which the Jedis and Lettuce clients speak to unchanged";
    private static final String ELASTIC
        = "Elastic binaries are Elastic License 2.0 or SSPL from 7.11, and this exists only to reach them";
    private static final String ELASTIC_APM
        = "the Elastic APM server is Elastic License 2.0 from 7.11, and this agent reports only to it";
    private static final String ELASTIC_FLOOR = "7.11.0";
    private static final String OPENSEARCH_IMAGE
        = "opensearchproject/opensearch and opensearchproject/opensearch-dashboards";
    private static final String OPENSEARCH_CLIENT
        = "org.opensearch.client:opensearch-java, or org.opensearch.client:spring-data-opensearch-starter";
    private static final String OPENSEARCH_MODULE = "org.opensearch:opensearch-testcontainers";
    private static final String OPENTELEMETRY = "io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter";
    private static final String MINIO
        = "MinIO is AGPL and its community edition is archived, with no images and no security fixes";
    private static final String OBJECT_STORAGE = "rustfs/rustfs or chrislusf/seaweedfs";
    private static final String OBJECT_STORAGE_MODULE = "a GenericContainer over rustfs/rustfs";
    private static final String LOCALSTACK
        = "the LocalStack community edition is archived, and the unified image needs an account token and is free "
            + "only for non-commercial use";
    private static final String LOCALSTACK_FORK = "a repackaging of the archived LocalStack community edition";
    private static final String AWS_EMULATORS = "rustfs/rustfs for S3 and motoserver/moto for other AWS APIs";
    private static final String AWS_MODULE = "a GenericContainer over motoserver/moto, or rustfs/rustfs for S3";
    private static final String COCKROACH
        = "CockroachDB 24.3 onward is under a proprietary licence with mandatory telemetry";
    private static final String HASHICORP
        = "HashiCorp relicensed to the Business Source License, which is not open source";
    private static final String HASHICORP_REPLACEMENT
        = "openbao/openbao for Vault, ghcr.io/opentofu/opentofu for Terraform";
    private static final String OPENBAO_MODULE = "a GenericContainer over openbao/openbao";
    private static final String CONSUL
        = "Consul is under the Business Source License from 1.16, which is not open source, and this exists only to "
            + "reach it";
    private static final String CONSUL_REPLACEMENT
        = "spring-cloud-starter-kubernetes-client or spring-cloud-starter-netflix-eureka-client";
    private static final String REDPANDA = "Redpanda is under the Business Source License, which is not open source";
    private static final String KAFKA_IMAGE = "apache/kafka or nats";
    private static final String KAFKA_MODULE = "org.testcontainers:kafka";
    private static final String TIMESCALE
        = "the default Timescale images carry Timescale License code, which is not open source";
    private static final String TIMESCALE_REPLACEMENT = "a tag ending in -oss, or the postgres image";
    private static final String OSS_TAG = "-oss(?:-|$)";
    private static final String SENTRY
        = "self-hosted Sentry is under the Functional Source License, which is not open source";
    private static final String SENTRY_REPLACEMENT
        = "glitchtip/glitchtip, or Sentry as a service through the MIT io.sentry SDKs";
    private static final String CONFLUENT
        = "Confluent Schema Registry, REST Proxy and ksqlDB are under the Confluent Community License, which is "
            + "not open source, and this exists only to reach them";
    private static final String CONFLUENT_REPLACEMENT = "apache/kafka with apicurio/apicurio-registry";
    private static final String APICURIO = "io.apicurio:apicurio-registry-serdes-avro-serde";
    private static final String COUCHBASE
        = "Couchbase Server is under the Business Source License, which is not open source, and this exists only to "
            + "reach it";
    private static final String CAMUNDA
        = "Camunda 8 is under the Camunda License, which allows no production use without a purchase, and this exists "
            + "only to reach it";
    private static final String CAMUNDA_REPLACEMENT = "org.flowable:flowable-spring-boot-starter, or Camunda 7 through "
        + "org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter";
    private static final String CAMUNDA_IMAGE = "flowable/flowable-rest, or camunda/camunda-bpm-platform";
    private static final String PROPRIETARY_DATABASE
        = "a proprietary database server, and this exists only to reach it";
    private static final String PROPRIETARY_BROKER = "a proprietary message broker, and this exists only to reach it";
    private static final String ARTEMIS_CLIENT = "org.apache.activemq:artemis-jakarta-client";
    private static final String ARTEMIS_MODULE = "org.testcontainers:activemq";
    private static final String ARTEMIS_IMAGE = "apache/activemq-artemis";
    private static final String GRAFANA_BUNDLE = "this pulls grafana/otel-lgtm, which bundles AGPL Grafana";
    private static final String GRAFANA_MODULES
        = "GenericContainers over prom/prometheus, jaegertracing/jaeger and victoriametrics/victoria-logs";
    private static final String ITEXT
        = "iText 5 onward is AGPL with a paid exemption, and a permissively licensed PDF library exists";
    private static final String GHOSTSCRIPT = "Ghostscript is AGPL, and this is a thin wrapper around it";
    private static final String GHOSTSCRIPT_PACKAGE = "Ghostscript is AGPL";
    private static final String MUPDF = "MuPDF is AGPL";
    private static final String PDF = "org.apache.pdfbox:pdfbox or com.github.librepdf:openpdf";
    private static final String DOCUMENTS = "org.apache.poi:poi and org.apache.pdfbox:pdfbox";
    private static final String GATLING
        = "the Gatling Highcharts module is under the Gatling Highcharts Component License, which is not open source, "
            + "and Gatling writes its reports through it";
    private static final String JMETER = "org.apache.jmeter:ApacheJMeter_core";
    private static final String VAADIN_BUNDLE
        = "the vaadin bundle carries every commercial Vaadin component, each under the Vaadin Commercial License";
    private static final String VAADIN = "a commercial Vaadin component, under the Vaadin Commercial License";
    private static final String VAADIN_CORE = "com.vaadin:vaadin-core";
    private static final String DEVELOCITY
        = "Develocity is under the Gradle Terms of Use, which are not open source, and this extension exists only to "
            + "reach it";
    private static final String NO_EXTENSION = "nothing; Maven's own build is what Airness verifies";
    private static final String LIQUIBASE = "Liquibase Pro is under an end-user licence agreement";
    private static final String LIQUIBASE_CORE = "org.liquibase:liquibase-core";
    private static final String ORACLE_JDK
        = "Oracle JDK is under the Oracle No-Fee Terms and Conditions, which is not an open-source licence";
    private static final String ORACLE_GRAALVM
        = "Oracle GraalVM is under the GraalVM Free Terms and Conditions, which is not an open-source licence";
    private static final String GRAALVM_IMAGE
        = "ghcr.io/graalvm/native-image-community or ghcr.io/graalvm/jdk-community";
    private static final String BITNAMI
        = "the Bitnami catalogue was deleted in 2025, so a tag here cannot be pulled reproducibly";
    private static final String BITNAMI_LEGACY = "the Bitnami legacy images are frozen with unfixed vulnerabilities";
    private static final String BITNAMI_SECURE
        = "the Bitnami secure images publish only a latest tag, so a pull cannot be pinned";
    private static final String UPSTREAM = "the upstream official image of the same service";
    private static final String CHROME
        = "this image ships Google Chrome or Microsoft Edge, which are proprietary binaries";
    private static final String CHROMIUM
        = "selenium/standalone-chromium, the same engine and driver, or selenium/standalone-firefox for a "
            + "second engine, which no session can size below 500 pixels";
    private static final String NEO4J_ENTERPRISE = "Neo4j Enterprise Edition is proprietary";
    private static final String NEO4J_COMMUNITY = "a community tag of the neo4j image";
    private static final String SONARQUBE
        = "the SonarQube Developer, Enterprise and Data Center editions are proprietary";
    private static final String SONARQUBE_COMMUNITY = "the community build tag of the sonarqube image";
    private static final String AGPL_STORE = "AGPL, and a permissively licensed equivalent exists";
    private static final String LGTM = "this bundle carries Grafana, Loki, Tempo and Mimir, each of which is "
        + AGPL_STORE;
    private static final String LGTM_REPLACEMENT
        = "prom/prometheus, jaegertracing/jaeger, victoriametrics/victoria-logs and persesdev/perses";
    private static final String PROMETHEUS = "prom/prometheus or victoriametrics/victoria-metrics";
    private static final String VICTORIA_LOGS = "victoriametrics/victoria-logs";
    private static final String NOTHING = "nothing; a Java project has no place for it";

    /**
     * The image repositories Airness refuses, in the order a lookup tries them.
     */
    private static final List<BlockedImage> IMAGES = List.of(
        BlockedImage.of("mongo", MONGODB, POSTGRES_IMAGE),
        BlockedImage.of("mongodb/*", MONGODB, POSTGRES_IMAGE),
        BlockedImage.of("mongo-express", MONGODB, POSTGRES_IMAGE),
        BlockedImage.of("percona/percona-server-mongodb", MONGODB, POSTGRES_IMAGE),
        BlockedImage.of("redis", REDIS, VALKEY).from("7.4"),
        BlockedImage.of("redis/*", REDIS, VALKEY),
        BlockedImage.of("redislabs/*", REDIS, VALKEY),
        BlockedImage.of("elasticsearch", ELASTIC, OPENSEARCH_IMAGE).from("7.11"),
        BlockedImage.of("kibana", ELASTIC, OPENSEARCH_IMAGE).from("7.11"),
        BlockedImage.of("logstash", ELASTIC, OPENSEARCH_IMAGE).from("7.11"),
        BlockedImage.of("docker.elastic.co/*", ELASTIC, OPENSEARCH_IMAGE).from("7.11"),
        BlockedImage.of("elastic/*", ELASTIC, OPENSEARCH_IMAGE).from("7.11"),
        BlockedImage.of("minio/*", MINIO, OBJECT_STORAGE),
        BlockedImage.of("quay.io/minio/*", MINIO, OBJECT_STORAGE),
        BlockedImage.of("pgsty/minio", MINIO, OBJECT_STORAGE),
        BlockedImage.of("localstack/*", LOCALSTACK, AWS_EMULATORS),
        BlockedImage.of("gresau/localstack-persist", LOCALSTACK_FORK, AWS_EMULATORS),
        BlockedImage.of("cockroachdb/cockroach", COCKROACH, POSTGRES_IMAGE).from("24.3"),
        BlockedImage.of("hashicorp/vault", HASHICORP, HASHICORP_REPLACEMENT).from("1.15"),
        BlockedImage.of("hashicorp/consul", HASHICORP, HASHICORP_REPLACEMENT).from("1.16"),
        BlockedImage.of("hashicorp/nomad", HASHICORP, HASHICORP_REPLACEMENT).from("1.6"),
        BlockedImage.of("hashicorp/terraform", HASHICORP, HASHICORP_REPLACEMENT).from("1.6"),
        BlockedImage.of("hashicorp/packer", HASHICORP, HASHICORP_REPLACEMENT).from("1.10"),
        BlockedImage.of("redpandadata/*", REDPANDA, KAFKA_IMAGE),
        BlockedImage.of("vectorized/*", REDPANDA, KAFKA_IMAGE),
        BlockedImage.of("timescale/timescaledb", TIMESCALE, TIMESCALE_REPLACEMENT).allowing(OSS_TAG),
        BlockedImage.of("timescale/timescaledb-ha", TIMESCALE, TIMESCALE_REPLACEMENT).allowing(OSS_TAG),
        BlockedImage.of("getsentry/*", SENTRY, SENTRY_REPLACEMENT).from("23.11"),
        BlockedImage.of("mcr.microsoft.com/mssql/*", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("mcr.microsoft.com/cosmosdb/*", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("container-registry.oracle.com/database/*", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("gvenzl/oracle-*", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("ibmcom/db2", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("icr.io/db2_community/*", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("saplabs/hanaexpress", PROPRIETARY_DATABASE, POSTGRES_IMAGE),
        BlockedImage.of("couchbase/*", COUCHBASE, POSTGRES_IMAGE),
        BlockedImage.of("container-registry.oracle.com/java/*", ORACLE_JDK, "eclipse-temurin"),
        BlockedImage.of("container-registry.oracle.com/graalvm/*", ORACLE_GRAALVM, GRAALVM_IMAGE),
        BlockedImage.of("azul/prime*", "Azul Platform Prime is proprietary", "azul/zulu-openjdk"),
        BlockedImage.of("confluentinc/cp-schema-registry", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-kafka-rest", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-ksqldb-*", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-kafka-mqtt", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-server*", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-enterprise-*", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("confluentinc/cp-control-center", CONFLUENT, CONFLUENT_REPLACEMENT),
        BlockedImage.of("camunda/zeebe", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/camunda", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/operate", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/tasklist", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/identity", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/optimize", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/connectors*", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/web-modeler*", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("camunda/console*", CAMUNDA, CAMUNDA_IMAGE),
        BlockedImage.of("icr.io/ibm-messaging/*", PROPRIETARY_BROKER, ARTEMIS_IMAGE),
        BlockedImage.of("ibmcom/mq", PROPRIETARY_BROKER, ARTEMIS_IMAGE),
        BlockedImage.of("solace/*", PROPRIETARY_BROKER, ARTEMIS_IMAGE),
        BlockedImage.of("splunk/*", "Splunk Enterprise is proprietary", VICTORIA_LOGS),
        BlockedImage.of("kong/kong-gateway", "Kong Gateway Enterprise is proprietary", "the kong image"),
        BlockedImage.of(
            "portainer/portainer-ee", "Portainer Business Edition is proprietary", "portainer/portainer-ce"
        ),
        BlockedImage.of("selenium/standalone-chrome*", CHROME, CHROMIUM),
        BlockedImage.of("selenium/node-chrome*", CHROME, CHROMIUM),
        BlockedImage.of("selenium/standalone-edge*", CHROME, CHROMIUM),
        BlockedImage.of("selenium/node-edge*", CHROME, CHROMIUM),
        BlockedImage.of("neo4j", NEO4J_ENTERPRISE, NEO4J_COMMUNITY).refusing("-enterprise"),
        BlockedImage.of("sonarqube", SONARQUBE, SONARQUBE_COMMUNITY)
            .refusing("-(?:developer|enterprise|datacenter)"),
        BlockedImage.of("bitnami/*", BITNAMI, UPSTREAM),
        BlockedImage.of("bitnamilegacy/*", BITNAMI_LEGACY, UPSTREAM),
        BlockedImage.of("bitnamisecure/*", BITNAMI_SECURE, UPSTREAM),
        BlockedImage.of("dxflrs/garage", "Garage is " + AGPL_STORE, OBJECT_STORAGE),
        BlockedImage.of("citusdata/citus", "Citus is " + AGPL_STORE, POSTGRES_IMAGE),
        BlockedImage.of("grafana/grafana", "Grafana is " + AGPL_STORE, "persesdev/perses"),
        BlockedImage.of("grafana/loki", "Loki is " + AGPL_STORE, VICTORIA_LOGS),
        BlockedImage.of("grafana/tempo", "Tempo is " + AGPL_STORE, "jaegertracing/jaeger"),
        BlockedImage.of("grafana/mimir", "Mimir is " + AGPL_STORE, PROMETHEUS),
        BlockedImage.of("grafana/pyroscope", "Pyroscope is " + AGPL_STORE, PROMETHEUS),
        BlockedImage.of("grafana/otel-lgtm", LGTM, LGTM_REPLACEMENT),
        BlockedImage.of("grafana/k6", "k6 is " + AGPL_STORE, "apache/jmeter"),
        BlockedImage.of("plausible/*", "Plausible is " + AGPL_STORE, "ghcr.io/umami-software/umami"),
        BlockedImage.of("metabase/metabase", "Metabase is AGPL", NOTHING)
    );

    /**
     * The Maven coordinates Airness refuses, in the order a lookup tries them.
     */
    private static final List<BlockedCoordinate> COORDINATES = List.of(
        BlockedCoordinate.of("org.mongodb", ANY, MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of("org.mongodb.*", ANY, MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of("org.mongojack", ANY, MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of("dev.morphia.morphia", ANY, MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of(
            "org.springframework.boot", "spring-boot-starter-data-mongodb*", MONGODB, POSTGRES_STARTER
        ),
        BlockedCoordinate.of("org.springframework.data", "spring-data-mongodb", MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of(
            "org.springframework.session", "spring-session-data-mongodb", MONGODB, POSTGRES_STARTER
        ),
        BlockedCoordinate.of("de.flapdoodle.embed", ANY, EMBEDDED_MONGODB, POSTGRES_STARTER),
        BlockedCoordinate.of(TESTCONTAINERS, "mongodb", MONGODB, POSTGRES_MODULE),
        BlockedCoordinate.of("co.elastic.clients", ANY, ELASTIC, OPENSEARCH_CLIENT),
        BlockedCoordinate.of(
            "org.springframework.boot", "spring-boot-starter-data-elasticsearch", ELASTIC, OPENSEARCH_CLIENT
        ),
        BlockedCoordinate.of("org.springframework.data", "spring-data-elasticsearch", ELASTIC, OPENSEARCH_CLIENT),
        elastic("org.elasticsearch", ANY),
        elastic("org.elasticsearch.client", "elasticsearch-rest-high-level-client"),
        elastic("org.elasticsearch.plugin", ANY),
        BlockedCoordinate.of(TESTCONTAINERS, "elasticsearch", ELASTIC, OPENSEARCH_MODULE),
        BlockedCoordinate.of("co.elastic.apm", ANY, ELASTIC_APM, OPENTELEMETRY),
        BlockedCoordinate.of("com.oracle.database.*", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.oracle.ojdbc", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.microsoft.sqlserver", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("net.sourceforge.jtds", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.ibm.db2", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.ibm.informix", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.sap.cloud.db.jdbc", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of("com.teradata.jdbc", ANY, PROPRIETARY_DATABASE, POSTGRES_STARTER),
        BlockedCoordinate.of(TESTCONTAINERS, "oracle-xe", PROPRIETARY_DATABASE, POSTGRES_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "oracle-free", PROPRIETARY_DATABASE, POSTGRES_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "mssqlserver", PROPRIETARY_DATABASE, POSTGRES_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "db2", PROPRIETARY_DATABASE, POSTGRES_MODULE),
        BlockedCoordinate.of("com.couchbase.client", ANY, COUCHBASE, POSTGRES_STARTER),
        BlockedCoordinate.of(
            "org.springframework.boot", "spring-boot-starter-data-couchbase*", COUCHBASE, POSTGRES_STARTER
        ),
        BlockedCoordinate.of("org.springframework.data", "spring-data-couchbase", COUCHBASE, POSTGRES_STARTER),
        BlockedCoordinate.of(TESTCONTAINERS, "couchbase", COUCHBASE, POSTGRES_MODULE),
        BlockedCoordinate.of("org.springframework.cloud", "spring-cloud-starter-consul*", CONSUL, CONSUL_REPLACEMENT),
        BlockedCoordinate.of("org.springframework.cloud", "spring-cloud-consul*", CONSUL, CONSUL_REPLACEMENT),
        BlockedCoordinate.of("com.ecwid.consul", ANY, CONSUL, CONSUL_REPLACEMENT),
        BlockedCoordinate.of("com.orbitz.consul", ANY, CONSUL, CONSUL_REPLACEMENT),
        BlockedCoordinate.of(TESTCONTAINERS, "consul", CONSUL, CONSUL_REPLACEMENT),
        BlockedCoordinate.of(TESTCONTAINERS, "vault", HASHICORP, OPENBAO_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "redpanda", REDPANDA, KAFKA_MODULE),
        BlockedCoordinate.of("io.confluent", ANY, CONFLUENT, APICURIO),
        BlockedCoordinate.of("io.camunda", ANY, CAMUNDA, CAMUNDA_REPLACEMENT),
        BlockedCoordinate.of("io.camunda.*", ANY, CAMUNDA, CAMUNDA_REPLACEMENT),
        BlockedCoordinate.of(TESTCONTAINERS, "localstack", LOCALSTACK, AWS_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "minio", MINIO, OBJECT_STORAGE_MODULE),
        BlockedCoordinate.of(TESTCONTAINERS, "grafana", GRAFANA_BUNDLE, GRAFANA_MODULES),
        BlockedCoordinate.of(TESTCONTAINERS, "solace", PROPRIETARY_BROKER, ARTEMIS_MODULE),
        BlockedCoordinate.of("com.solacesystems", ANY, PROPRIETARY_BROKER, ARTEMIS_CLIENT),
        BlockedCoordinate.of("com.solace.*", ANY, PROPRIETARY_BROKER, ARTEMIS_CLIENT),
        BlockedCoordinate.of("com.ibm.mq", ANY, PROPRIETARY_BROKER, ARTEMIS_CLIENT),
        BlockedCoordinate.of("com.itextpdf", ANY, ITEXT, PDF),
        BlockedCoordinate.of("com.itextpdf.*", ANY, ITEXT, PDF),
        BlockedCoordinate.of("org.ghost4j", ANY, GHOSTSCRIPT, PDF),
        BlockedCoordinate.of("com.artifex.mupdf", ANY, MUPDF, PDF),
        BlockedCoordinate.of("com.aspose", ANY, "Aspose is proprietary", DOCUMENTS),
        BlockedCoordinate.of("com.e-iceblue", ANY, "Spire is proprietary", DOCUMENTS),
        BlockedCoordinate.of("com.groupdocs", ANY, "GroupDocs is proprietary", DOCUMENTS),
        BlockedCoordinate.of("io.gatling.highcharts", ANY, GATLING, JMETER),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin", VAADIN_BUNDLE, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-charts*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-board*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-crud*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-grid-pro*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-map*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-rich-text-editor*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-spreadsheet*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-cookie-consent*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-dashboard*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "collaboration-engine*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of(VAADIN_GROUP, "vaadin-copilot*", VAADIN, VAADIN_CORE),
        BlockedCoordinate.of("com.gradle", ANY, DEVELOCITY, NO_EXTENSION),
        BlockedCoordinate.of("org.liquibase", "liquibase-commercial", LIQUIBASE, LIQUIBASE_CORE),
        BlockedCoordinate.of("org.liquibase.pro", ANY, LIQUIBASE, LIQUIBASE_CORE),
        BlockedCoordinate.of(
            "com.redgate.flyway", ANY, "Flyway Teams and Enterprise are proprietary", "org.flywaydb:flyway-core"
        ),
        BlockedCoordinate.of(
            "com.hazelcast", "hazelcast-enterprise*", "Hazelcast Enterprise is proprietary", "com.hazelcast:hazelcast"
        )
    );

    /**
     * The system packages a Dockerfile may not install.
     */
    private static final List<BlockedName> SYSTEM_PACKAGES = List.of(
        new BlockedName("ghostscript", GHOSTSCRIPT_PACKAGE, PDF),
        new BlockedName("mupdf", MUPDF, PDF),
        new BlockedName("mupdf-tools", MUPDF, PDF),
        new BlockedName("mongodb-org", MONGODB, POSTGRES_IMAGE),
        new BlockedName("mongodb-org-server", MONGODB, POSTGRES_IMAGE),
        new BlockedName("mongodb-mongosh", MONGODB, POSTGRES_IMAGE),
        new BlockedName("mongodb-database-tools", MONGODB, POSTGRES_IMAGE)
    );

    /**
     * The JDK distributions a workflow may not install through setup-java or setup-graalvm.
     */
    private static final List<BlockedName> DISTRIBUTIONS = List.of(
        new BlockedName("oracle", ORACLE_JDK, "temurin"),
        new BlockedName("graalvm", ORACLE_GRAALVM, "graalvm-community")
    );

    /**
     * The JDK vendors a .sdkmanrc may not select.
     */
    private static final List<BlockedName> SDKMAN_VENDORS = List.of(
        new BlockedName("oracle", ORACLE_JDK, "the -tem suffix"),
        new BlockedName("graal", ORACLE_GRAALVM, "the -graalce suffix")
    );

    static Optional<BlockedImage> image(String repository) {
        return IMAGES.stream().filter(entry -> entry.matches(repository)).findFirst();
    }

    static Optional<BlockedCoordinate> coordinate(String groupId, String artifactId) {
        return COORDINATES.stream().filter(entry -> entry.matches(groupId, artifactId)).findFirst();
    }

    static Optional<BlockedName> systemPackage(String name) {
        return named(SYSTEM_PACKAGES, name);
    }

    static Optional<BlockedName> distribution(String name) {
        return named(DISTRIBUTIONS, name);
    }

    static Optional<BlockedName> sdkmanVendor(String name) {
        return named(SDKMAN_VENDORS, name);
    }

    static List<BlockedImage> images() {
        return IMAGES;
    }

    static List<BlockedCoordinate> coordinates() {
        return COORDINATES;
    }

    // Every Elastic coordinate turned at one release, so the floor is written here once rather than
    // beside each of the three rows that carry it.
    private static BlockedCoordinate elastic(String group, String artifact) {
        return new BlockedCoordinate(group, artifact, Optional.of(ELASTIC_FLOOR), ELASTIC, OPENSEARCH_CLIENT);
    }

    private static Optional<BlockedName> named(List<BlockedName> entries, String name) {
        return entries.stream().filter(entry -> entry.name().equals(name)).findFirst();
    }
}
