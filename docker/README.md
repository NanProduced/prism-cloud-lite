# Docker 部署（本地跑服务，不再依赖 IDE）

本仓库是 Maven 多模块项目，包含：

- `gateway-service`（8082）
- `auth-service`（8081）
- `core-service`（8083）
- `device-service/device-boot`（8084）

你可以用 Docker 把服务打包成容器并后台运行。

## 1) 一键启动（推荐：复用现有中间件 compose）

先启动中间件（Postgres/Redis/RabbitMQ/Nacos 等）：

```bash
docker compose -f docker-compose.yml up -d
```

如需启用 `core-service` 的 AI Assistant（本地 vLLM + embeddings），再启动 AI 模型服务：

```bash
docker compose -f ai-stack/docker-compose.yml up -d
```

AI stack 现在包含 embeddings + rerank（Infinity）服务，容器内推荐配置：

- `ASSISTANT_RAG_EMBEDDING_BASE_URL=http://embeddings:7997`
- `ASSISTANT_RAG_RERANK_BASE_URL=http://rerank:7998`
- `ASSISTANT_RAG_RERANK_MODEL=BAAI/bge-reranker-v2-m3`

再启动应用服务（会自动 build 镜像）：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

说明：

- `gateway-service` 启动时会按 `issuer-uri` 拉取 OIDC discovery（`/.well-known/openid-configuration`），因此 `docker-compose.app.yml` 已为其配置了 `WAIT_FOR_URL=http://auth-service:8081/auth/actuator/health`，确保 auth-service 就绪后再启动。

查看状态：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml ps
```

## 1.1) AI 服务网络互通（常见报错）

如果你发现 `core-service` 日志里出现 `http://127.0.0.1:7997/embeddings` 连接被拒绝（`Connection refused`），通常原因是：

- 在容器里 `127.0.0.1` 指向容器自身，而 embeddings/LLM 在另一个容器中。

建议用一次命令把中间件 + AI + 应用放到同一个 compose project / network：

```bash
docker compose -f docker-compose.yml -f ai-stack/docker-compose.yml -f docker-compose.app.yml up -d --build
```

并确保 `.env` 中这两项是容器内可达的服务名（默认已改为）：

- `ASSISTANT_CHAT_LLM_BASE_URL=http://llm-vllm:8000`
- `ASSISTANT_RAG_EMBEDDING_BASE_URL=http://embeddings:7997`

## 1.2) 开启 Debug 日志（可选）

默认日志级别由各服务的 `application-*.yml` 决定。若你只想打开**业务包**（`nan.produced.prism.*`）的 Debug 日志、避免 Spring/框架日志刷屏，可叠加 `docker-compose.debug.yml`：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.debug.yml up -d --build
```

如果你同时启用了 `ai-stack`（vLLM + embeddings），用下面这个“一次性启动全部”的组合：

```bash
docker compose -f docker-compose.yml -f ai-stack/docker-compose.yml -f docker-compose.app.yml -f docker-compose.debug.yml up -d --build
```

关闭 Debug：启动命令里去掉 `-f docker-compose.debug.yml` 即可。

## 1.3) Postgres 镜像切换（pgvector + pg_jieba + HNSW）

RAG 混合检索需要 **pgvector(HNSW)** 与 **pg_jieba**。建议用自定义镜像替换默认的 `pgvector/pgvector:pg16`。

推荐方案：
1. 基于 `pgvector/pgvector:pg16` 自建镜像，安装 `pg_jieba` 扩展。
2. 在 initdb 脚本中创建扩展与分词配置（`CREATE EXTENSION vector; CREATE EXTENSION pg_jieba;`，并创建 `jieba_cfg`）。
3. 修改 `docker-compose.yml` 的 Postgres 镜像为自建镜像。

示例（仅示意，镜像名按你的实际构建结果替换）：

```yaml
  postgres:
    image: prism-postgres:pg16-vector-jieba
```

注意：
- pgvector 版本建议 **>= 0.5.x**（支持 HNSW）。
- 若已有数据卷，切换镜像前建议备份或按“可重建”策略处理。

构建命令（使用本仓库提供的 Dockerfile）：

```bash
docker build -t prism-postgres:pg16-vector-jieba docker/postgres/pgvector-jieba
```

如遇到 `apt-get` 网络问题（国内环境），可指定镜像源：

```bash
docker build \
  --build-arg APT_MIRROR=mirrors.tuna.tsinghua.edu.cn \
  --build-arg APT_SECURITY_MIRROR=mirrors.tuna.tsinghua.edu.cn \
  -t prism-postgres:pg16-vector-jieba docker/postgres/pgvector-jieba
```

一条命令同时更新 Postgres 容器并启动 AI stack（含 rerank）：

```bash
docker compose -f docker-compose.yml -f ai-stack/docker-compose.yml up -d --build
```

## 2) 常用环境变量（按需覆盖）

`docker-compose.app.yml` 已给了默认值/占位符，常见需要你在命令行或 `.env` 文件里提供：

- `PRISM_GATEWAY_CLIENT_SECRET`
- `PRISM_CONSOLE_CLIENT_SECRET`
- `PRISM_GOOGLE_CLIENT_ID`
- `PRISM_SMTP_HOST` / `PRISM_SMTP_USERNAME` / `PRISM_SMTP_PASSWORD` / `PRISM_SMTP_FROM`

如果你的 Postgres 里数据库名不是 `prism-core/prism-auth/prism-device`，改对应容器的 `SPRING_DATASOURCE_URL`。

### Maven 依赖下载失败（TLS / 网络抖动）

如果在 build 阶段出现类似错误：

- `Could not transfer artifact ... from/to central ... SSL peer shut down incorrectly`

可按需在 `.env` 或命令行提供以下变量，让 build 使用镜像源/重试：

- `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public`（推荐：包含 central + 常用补充仓库）
- `MAVEN_INSECURE_SSL=true`（不推荐，仅临时排障）

## 3) 停止/清理

停止（保留数据卷）：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

全清理（会删容器；数据卷取决于你的 volume 映射是否为宿主机目录）：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down --remove-orphans
```
