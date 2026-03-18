FROM ubuntu:24.04

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=eclipse-temurin:21 /opt/java/openjdk /opt/java/jdk21
ENV JAVA_HOME=/opt/java/jdk21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install monitoring tools required by ConfigurableMonitor:
#   mpstat, sar, iostat  -> sysstat
#   perf                 -> linux-tools-common + linux-tools-generic
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        sysstat \
        linux-tools-common \
        linux-tools-generic && \
    rm -rf /var/lib/apt/lists/*

# Verify java is available
RUN java -version
