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
