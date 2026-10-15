# ============================================================
# GEO-SaaS Backend 多架构 Dockerfile
# 支持 AMD64(x86) / ARM64(飞腾/鲲鹏/树莓派)
# 构建命令：
#   AMD64: docker build -t geo-saa-backend:latest -f docker/Dockerfile .
#   ARM64: docker buildx build --platform linux/arm64 -t geo-saa-backend:arm64 -f docker/Dockerfile . --load
#   多架构: docker buildx build --platform linux/amd64,linux/arm64 -t geo-saa-backend:latest -f docker/Dockerfile . --push
# ============================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml 缓存依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# 复制源码并构建
COPY src ./src
# 默认 MySQL 构建；信创数据库使用 -Pdm 或 -Pkingbase
ARG DB_PROFILE=mysql
RUN mvn clean package -DskipTests -B -P${DB_PROFILE}

# ---------- 运行阶段 ----------
# 使用 Eclipse Temurin JDK 17（支持多架构，信创环境可替换为毕昇JDK/龙芯JDK）
FROM eclipse-temurin:17-jre

# 元数据
LABEL maintainer="GEO-SaaS Team"
LABEL description="GEO-SaaS Backend Service"
LABEL version="2.0.0"

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/target/geo-saa-backend.jar app.jar

# 复制数据库迁移脚本（用于容器内初始化）
COPY --from=builder /build/src/main/resources/db /app/db

# 创建非 root 用户（安全最佳实践）
RUN groupadd -r geosaa && useradd -r -g geosaa -d /app -s /sbin/nologin geosaa && \
    chown -R geosaa:geosaa /app
USER geosaa

# 暴露端口
EXPOSE 8080

# JVM 参数优化（容器环境自适应）
# -XX:+UseContainerSupport: 启用容器资源感知
# -XX:MaxRAMPercentage: 最大堆内存占容器内存百分比
# -XX:+ExitOnOutOfMemoryError: OOM 时退出（便于容器重启）
# -Dfile.encoding=UTF-8: 中文编码
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
