# syntax=docker/dockerfile:1.9
ARG MAVEN_IMAGE=docker.io/library/maven:3.9.15-eclipse-temurin-21@sha256:d6e32a254897415a445654a3c43c30fbc731a4d946cf5c66d1cb9184141c20c1
ARG JAVA_IMAGE=docker.io/library/eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699

FROM ${MAVEN_IMAGE} AS builder
ARG MODULE
WORKDIR /workspace
COPY pom.xml .
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
COPY data-platform-test data-platform-test
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl "${MODULE}" -am package -DskipTests -DskipTests=true \
    && find "${MODULE}/target" -maxdepth 1 -type f -name '*.jar' \
       ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} /tmp/app.jar \; \
    && test -s /tmp/app.jar

FROM ${JAVA_IMAGE} AS runtime
ARG APP_VERSION=unknown
ENV APP_VERSION=${APP_VERSION} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
WORKDIR /app
COPY docker/java-entrypoint.sh /usr/local/bin/java-entrypoint
COPY --from=builder /tmp/app.jar /app/app.jar
RUN chmod 0555 /usr/local/bin/java-entrypoint \
    && addgroup -S dmh \
    && adduser -S -G dmh -u 10001 dmh \
    && chown -R dmh:dmh /app
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/java-entrypoint"]
