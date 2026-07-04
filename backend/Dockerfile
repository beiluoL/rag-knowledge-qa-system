# ============================================
# 后端 Dockerfile — 多阶段构建
# 阶段1: Maven 编译打包
# 阶段2: JRE 运行 JAR
# ============================================

# --- 阶段 1：编译 ---
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build
# 先复制 pom.xml，利用 Docker 缓存层（依赖不常变）
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# 复制源码并编译
COPY src ./src
RUN mvn package -DskipTests -B -q

# --- 阶段 2：运行 ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建上传文件目录
RUN mkdir -p /app/data/documents

# 从构建阶段复制 JAR
COPY --from=builder /build/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# JVM 参数（可通过环境变量 JAVA_OPTS 覆盖）
ENV JAVA_OPTS="-Xmx4g -Xms512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
