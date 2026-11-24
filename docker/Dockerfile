# SmartJP, the website, as a container: a Java 25 runtime (Azul Zulu) and the application jar. This is the only way the website is shipped.
# The desktop app has its own installer; this image never opens a window.
#
# The jar is built outside (the release workflow builds it once and every download wraps that same file), this only wraps it:
#   mvn -B clean package -DskipTests
#   docker build -f docker/Dockerfile -t smartjp .                           # picks up target/smartjp-*.jar
#   docker build -f docker/Dockerfile --build-arg JAR=path/to/smartjp.jar -t smartjp .
FROM azul/zulu-openjdk:25-jre

ARG JAR=target/smartjp-*.jar
ARG VERSION=dev

LABEL org.opencontainers.image.title="smartjp" \
      org.opencontainers.image.description="SmartJP: learn Japanese, kana first. The website, for you to host yourself." \
      org.opencontainers.image.source="https://github.com/B077AS/smartjp" \
      org.opencontainers.image.version="${VERSION}"

# tini passes SIGTERM on to the JVM (so `docker stop` ends the application cleanly, with its shutdown hooks) and reaps stray processes.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tini \
 && rm -rf /var/lib/apt/lists/*

# Not root. /data is the one place the application writes (the database, the logs).
RUN useradd --uid 10001 --create-home --shell /usr/sbin/nologin smartjp \
 && mkdir /data \
 && chown smartjp:smartjp /data

COPY ${JAR} /app/smartjp.jar

USER smartjp
WORKDIR /data
# The database (smartjp.db) and the logs (logs/) live in the volume, so replacing the image never touches them.
# SERVER_ADDRESS=0.0.0.0: inside a container the application must listen on every interface or the published port cannot reach it. Who can
# reach it from OUTSIDE is decided by how the port is published (docker-compose.yml publishes it on 127.0.0.1 only).
ENV SMARTJP_DATA_DIR=/data \
    SERVER_ADDRESS=0.0.0.0
VOLUME /data

EXPOSE 8081

# Healthy once the web server answers on its port. (The image has bash but no curl: bash can open a TCP connection by itself.)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081"]

ENTRYPOINT ["tini", "--", "java", "-jar", "/app/smartjp.jar"]
