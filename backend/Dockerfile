# ============================================
# 后端 Dockerfile — 运行镜像
# 需先在本地执行: mvn package -DskipTests
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app
RUN mkdir -p /app/data/documents

# 复制本地已构建好的 JAR
COPY target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xmx4g -Xms512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
