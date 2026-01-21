FROM pgvector/pgvector:pg16

ARG APT_MIRROR
ARG APT_SECURITY_MIRROR

RUN SOURCES=""; \
    if [ -f /etc/apt/sources.list ]; then \
      SOURCES="/etc/apt/sources.list"; \
    elif [ -f /etc/apt/sources.list.d/debian.sources ]; then \
      SOURCES="/etc/apt/sources.list.d/debian.sources"; \
    fi; \
    if [ -n "$SOURCES" ] && [ -n "$APT_MIRROR" ]; then \
      sed -i "s|deb.debian.org|${APT_MIRROR}|g" "$SOURCES"; \
    fi; \
    if [ -n "$SOURCES" ] && [ -n "$APT_SECURITY_MIRROR" ]; then \
      sed -i "s|security.debian.org|${APT_SECURITY_MIRROR}|g" "$SOURCES"; \
    fi

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    build-essential \
    cmake \
    postgresql-server-dev-16 \
    ca-certificates \
  && rm -rf /var/lib/apt/lists/*

# Install pg_jieba from source.
RUN git clone --depth 1 https://github.com/jaiminpan/pg_jieba.git /tmp/pg_jieba \
  && cd /tmp/pg_jieba \
  && git submodule update --init --recursive \
  && mkdir build \
  && cd build \
  && PG_TYPE_INCLUDE=""; \
     for p in /usr/include/postgresql/16/server /usr/include/postgresql/server /usr/include/postgresql/15/server; do \
       if [ -d "$p" ]; then PG_TYPE_INCLUDE="$p"; break; fi; \
     done; \
     if [ -z "$PG_TYPE_INCLUDE" ]; then echo "PostgreSQL_TYPE_INCLUDE_DIR not found" && exit 1; fi; \
     cmake -DCMAKE_CXX_STANDARD=11 -DPostgreSQL_TYPE_INCLUDE_DIR="$PG_TYPE_INCLUDE" .. \
  && make \
  && make install \
  && rm -rf /tmp/pg_jieba

COPY initdb/ /docker-entrypoint-initdb.d/
