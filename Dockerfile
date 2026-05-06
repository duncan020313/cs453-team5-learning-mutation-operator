# syntax=docker/dockerfile:1
FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG USERNAME=user
ARG USER_UID=1000
ARG USER_GID=1000
ARG NODE_MAJOR=20
ARG GRADLE_VERSION=8.10.2
ARG INSTALL_GUMTREE=true
ARG GUMTREE_VERSION=v4.0.0-beta6

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=America/Los_Angeles \
    JAVA11_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
    JAVA17_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
    MAVEN_OPTS="-Xmx4g" \
    GRADLE_OPTS="-Dorg.gradle.daemon=false -Xmx4g" \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PATH="/opt/gradle/current/bin:${PATH}"

# Base OS/dev tools + Java/Python/Perl toolchains.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      apt-transport-https \
      autoconf \
      automake \
      bash-completion \
      bc \
      build-essential \
      ca-certificates \
      ccache \
      clang \
      cmake \
      cpanminus \
      curl \
      file \
      gdb \
      git \
      git-lfs \
      gnupg \
      graphviz \
      jq \
      less \
      libarchive-tools \
      libdbd-csv-perl \
      libdbd-sqlite3-perl \
      libdbi-perl \
      libdatetime-perl \
      libjson-parse-perl \
      libjson-perl \
      liblist-moreutils-perl \
      libmodule-pluggable-perl \
      libperl-critic-perl \
      libssl-dev \
      libstring-interpolate-perl \
      libstring-shellquote-perl \
      libtool \
      liburi-perl \
      libwww-perl \
      libxml-simple-perl \
      locales \
      lsb-release \
      maven \
      nano \
      netcat-openbsd \
      ninja-build \
      openjdk-11-jdk \
      openjdk-17-jdk \
      openssh-client \
      parallel \
      patch \
      perl \
      pkg-config \
      procps \
      python3 \
      python3-dev \
      python3-pip \
      python3-setuptools \
      python3-venv \
      ripgrep \
      rsync \
      shellcheck \
      software-properties-common \
      subversion \
      sudo \
      time \
      tmux \
      tree \
      tzdata \
      unzip \
      vim \
      wget \
      xz-utils \
      zip \
      zlib1g-dev && \
    locale-gen en_US.UTF-8 && \
    ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo ${TZ} > /etc/timezone && \
    git lfs install --system && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Node.js 20.x from NodeSource
RUN mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
      | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
      > /etc/apt/sources.list.d/nodesource.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /root/.npm

# Recent Gradle CLI. Project-level ./gradlew still takes precedence when available.
RUN set -eux; \
    gradle_zip="gradle-${GRADLE_VERSION}-bin.zip"; \
    for gradle_url in \
      "https://downloads.gradle.org/distributions/${gradle_zip}" \
      "https://services.gradle.org/distributions/${gradle_zip}"; do \
      if curl -4 --fail --location --show-error --silent \
        --retry 5 --retry-delay 5 --retry-all-errors --connect-timeout 30 \
        "${gradle_url}" -o /tmp/gradle.zip; then \
        break; \
      fi; \
      rm -f /tmp/gradle.zip; \
    done; \
    test -s /tmp/gradle.zip; \
    mkdir -p /opt/gradle && \
    unzip -q /tmp/gradle.zip -d /opt/gradle && \
    ln -sfn "/opt/gradle/gradle-${GRADLE_VERSION}" /opt/gradle/current && \
    ln -sfn /opt/gradle/current/bin/gradle /usr/local/bin/gradle && \
    rm -f /tmp/gradle.zip

# Python packages for data processing, AST/pattern mining, experiments, and notebooks.
RUN python3 -m pip install --upgrade pip setuptools wheel && \
    python3 -m pip install \
      beautifulsoup4 \
      black \
      click \
      hypothesis \
      ipython \
      javalang \
      joblib \
      junitparser \
      lxml \
      matplotlib \
      mypy \
      networkx \
      notebook \
      numpy \
      pandas \
      pre-commit \
      pyarrow \
      pydantic \
      pytest \
      pytest-cov \
      pytest-xdist \
      pyyaml \
      rich \
      ruff \
      scikit-learn \
      scipy \
      seaborn \
      tabulate \
      tqdm \
      tree_sitter \
      tree_sitter_java \
      uv \
      z3-solver

RUN update-alternatives --set java  ${JAVA11_HOME}/bin/java && \
    update-alternatives --set javac ${JAVA11_HOME}/bin/javac && \
    printf '%s\n' \
      '#!/usr/bin/env bash' \
      'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64' \
      'export PATH="$JAVA_HOME/bin:$PATH"' \
      'exec "$@"' \
      > /usr/local/bin/with-java11 && \
    printf '%s\n' \
      '#!/usr/bin/env bash' \
      'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' \
      'export PATH="$JAVA_HOME/bin:$PATH"' \
      'exec "$@"' \
      > /usr/local/bin/with-java17 && \
    chmod +x /usr/local/bin/with-java11 /usr/local/bin/with-java17

# GumTree: Java 17 is required by current GumTree, so build/run it through a Java-17 wrapper.
RUN if [[ "${INSTALL_GUMTREE}" == "true" ]]; then \
      git clone --depth 1 --branch "${GUMTREE_VERSION}" https://github.com/GumTreeDiff/gumtree.git /opt/gumtree-src || \
      git clone --depth 1 https://github.com/GumTreeDiff/gumtree.git /opt/gumtree-src; \
      cd /opt/gumtree-src; \
      git submodule update --init --recursive; \
      JAVA_HOME=${JAVA17_HOME} PATH=${JAVA17_HOME}/bin:${PATH} ./gradlew --no-daemon build -x test; \
      mkdir -p /opt/gumtree; \
      unzip -q dist/build/distributions/gumtree-*.zip -d /opt/gumtree; \
      GUMTREE_DIR="$(find /opt/gumtree -maxdepth 1 -mindepth 1 -type d | head -n 1)"; \
      ln -sfn "${GUMTREE_DIR}" /opt/gumtree/current; \
      printf '%s\n' \
        '#!/usr/bin/env bash' \
        'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' \
        'export PATH="$JAVA_HOME/bin:$PATH"' \
        'exec /opt/gumtree/current/bin/gumtree "$@"' \
        > /usr/local/bin/gumtree; \
      chmod +x /usr/local/bin/gumtree; \
      rm -rf /root/.gradle/caches/modules-2/files-2.1 /tmp/*; \
    fi

# Workspace layout.
RUN mkdir -p /workspace /workspace/data /workspace/output && \
    groupadd --gid ${USER_GID} ${USERNAME} && \
    useradd --uid ${USER_UID} --gid ${USER_GID} -m -s /bin/bash ${USERNAME} && \
    usermod -aG sudo,root ${USERNAME} && \
    echo "${USERNAME} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${USERNAME} && \
    chmod 0440 /etc/sudoers.d/${USERNAME} && \
    chown -R ${USERNAME}:${USERNAME} /workspace /home/${USERNAME} /opt/gradle && \
    if [[ -d /opt/gumtree-src ]]; then chown -R ${USERNAME}:${USERNAME} /opt/gumtree-src /opt/gumtree; fi

USER ${USERNAME}
WORKDIR /workspace
ENV PATH="/home/${USERNAME}/.local/bin:/opt/gradle/current/bin:${PATH}"

CMD ["/bin/bash"]
