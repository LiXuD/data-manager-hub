# syntax=docker/dockerfile:1.9
ARG MAVEN_IMAGE=docker.io/library/maven:3.9.15-eclipse-temurin-21@sha256:d6e32a254897415a445654a3c43c30fbc731a4d946cf5c66d1cb9184141c20c1

FROM ${MAVEN_IMAGE} AS maven-deps
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -N dependency:go-offline \
    && mvn -B -ntp -N -DskipTests liquibase:help \
    && mkdir -p /tmp/maven-repo \
    && cp -a /root/.m2/repository /tmp/maven-repo/

FROM ${MAVEN_IMAGE} AS runtime
WORKDIR /workspace
RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl jq openssl postgresql-client \
    && rm -rf /var/lib/apt/lists/*
COPY --from=maven-deps /tmp/maven-repo/repository /opt/maven-repo
COPY pom.xml migrate-db.sh publish-nacos-config.sh ./
COPY sql ./sql
COPY nacos-config ./nacos-config
COPY ci ./ci
COPY data-platform-common-contract data-platform-common-contract
COPY data-platform-common-web data-platform-common-web
COPY data-platform-common-persistence data-platform-common-persistence
COPY data-platform-common-runtime data-platform-common-runtime
COPY data-platform-plugin-spi data-platform-plugin-spi
COPY data-platform-plugin-testkit data-platform-plugin-testkit
RUN chmod 0555 ci/scripts/*.sh ci/scripts/*.py migrate-db.sh publish-nacos-config.sh \
    && useradd --uid 10001 --create-home --shell /usr/sbin/nologin dmh \
    && chown -R 10001:10001 /workspace
# dbops executes Liquibase through Maven inside a read-only-root Job.  The
# builder prefetches the Maven/Liquibase graph and runtime uses offline mode;
# the local repository is copied into the Job's ephemeral /tmp volume so the
# image layer and /home/dmh remain immutable.
ENV MAVEN_CONFIG=/tmp/maven \
    MAVEN_REPO_SEED=/opt/maven-repo \
    MAVEN_OFFLINE=true \
    MAVEN_OPTS="-Dmaven.repo.local=/tmp/maven-repo"
USER 10001:10001
ENTRYPOINT ["bash", "ci/scripts/dbops-entrypoint.sh"]
