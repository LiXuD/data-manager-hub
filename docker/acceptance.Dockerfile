# syntax=docker/dockerfile:1.9
ARG MAVEN_IMAGE=docker.io/library/maven:3.9.15-eclipse-temurin-21@sha256:d6e32a254897415a445654a3c43c30fbc731a4d946cf5c66d1cb9184141c20c1

FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY data-platform-test ./data-platform-test
COPY data-platform-common-contract data-platform-common-contract
COPY data-platform-common-web data-platform-common-web
COPY data-platform-common-persistence data-platform-common-persistence
COPY data-platform-common-runtime data-platform-common-runtime
COPY data-platform-plugin-spi data-platform-plugin-spi
COPY data-platform-plugin-testkit data-platform-plugin-testkit
COPY data-platform-masterdata data-platform-masterdata
COPY data-platform-access data-platform-access
COPY data-platform-billing data-platform-billing
COPY data-platform-identity data-platform-identity
COPY data-platform-governance data-platform-governance
COPY data-platform-gateway data-platform-gateway
COPY data-platform-sdk data-platform-sdk
# dependency:go-offline does not resolve Surefire's dynamically selected
# provider/launcher. Seed both provider versions used by this reactor and the
# JUnit Platform version managed by the Spring Boot BOM before copying the
# repository into the offline runtime layer.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -N dependency:get \
      -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.4 \
      -Dtransitive=true \
    && mvn -B -ntp -N dependency:get \
      -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.2.5 \
      -Dtransitive=true \
    && mvn -B -ntp -N dependency:get \
      -Dartifact=org.junit.platform:junit-platform-launcher:1.12.2 \
      -Dtransitive=true \
    && mvn -B -ntp -pl data-platform-test/data-platform-test-service -am \
      -Dintegration.tests=true -DskipTests dependency:go-offline \
    && mvn -B -ntp -pl data-platform-test/data-platform-test-service -am \
      -Dintegration.tests=true -DskipTests dependency:resolve-plugins \
    && \
    mvn -B -ntp -pl data-platform-test/data-platform-test-service -am \
      -Dintegration.tests=true -Dacceptance.skip.unit.tests=true -DskipITs=true verify \
    && mkdir -p /tmp/maven-repo \
    && cp -a /root/.m2/repository /tmp/maven-repo/ \
    && find /tmp/maven-repo/repository -name _remote.repositories -delete \
    && find /tmp/maven-repo/repository -name '*.lastUpdated' -delete

FROM ${MAVEN_IMAGE} AS runtime
WORKDIR /workspace
COPY --from=builder /workspace /workspace
COPY --from=builder /tmp/maven-repo/repository /opt/maven-repo
RUN useradd --uid 10001 --create-home --shell /usr/sbin/nologin dmh \
    && chown -R 10001:10001 /workspace /opt/maven-repo
# The acceptance Job runs Maven's integration-test lifecycle at runtime.  It
# is intentionally supplied with a writable ephemeral root by
# create-private-job.sh. Dependencies are prefetched into the image so the
# release Job does not need Maven egress or a mutable home directory.
ENV MAVEN_CONFIG=/tmp/maven \
    MAVEN_OPTS="-Dmaven.repo.local=/opt/maven-repo"
USER 10001:10001
ENTRYPOINT ["mvn", "-B", "-ntp", "-o", "-pl", "data-platform-test/data-platform-test-service", "-am", "-Dintegration.tests=true", "-Dacceptance.skip.unit.tests=true", "verify"]
