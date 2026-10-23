# Pinned toolchain for building and testing the transpiler (sources are bind-mounted, not copied).
# Every tool version is fixed here so GnuCOBOL output, compilers and JDK are reproducible.
# If Debian supersedes a pinned package, update the ARG and re-record fixtures if cobc changed.
FROM debian:trixie-slim@sha256:62bd423aeee912639ca8ee681c2b762cb3e25137e0a92b20be455898a9f034ad

ARG GNUCOBOL_VERSION=3.2-3
ARG OPENJDK_VERSION=21.0.12.1+1-1~deb13u1
ARG GXX_VERSION=4:14.2.0-1
ARG CMAKE_VERSION=3.31.6-2
ARG MAKE_VERSION=4.4.1-2
ARG PYTHON_VERSION=3.13

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates \
      gnucobol3=${GNUCOBOL_VERSION} \
      openjdk-21-jdk-headless=${OPENJDK_VERSION} \
      g++=${GXX_VERSION} \
      cmake=${CMAKE_VERSION} \
      make=${MAKE_VERSION} \
 && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.11.19 /uv /usr/local/bin/uv

# Caches and the virtualenv live outside the bind-mounted repo so host and container builds
# never share platform-specific artefacts. HOME is world-writable for `docker run --user`.
ENV HOME=/home/dev \
    UV_PYTHON_INSTALL_DIR=/opt/uv-python \
    UV_PROJECT_ENVIRONMENT=/home/dev/venv \
    UV_LINK_MODE=copy \
    GRADLE_USER_HOME=/home/dev/.gradle \
    TRANSPILER_BUILD_DIR=/work/build/docker
RUN mkdir -p /home/dev && chmod 0777 /home/dev \
 && uv python install ${PYTHON_VERSION} && chmod -R a+rX /opt/uv-python

WORKDIR /work
