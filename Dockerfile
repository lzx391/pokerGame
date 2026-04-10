# --- 前端：Vue 构建产物放入 Spring Boot static ---
FROM node:20-alpine AS frontend
WORKDIR /build
COPY front/dp_game/package.json front/dp_game/package-lock.json ./
RUN npm ci
COPY front/dp_game/ ./
RUN npm run build

# --- 后端：打包可执行 jar（内含 static）---
FROM maven:3.9.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=frontend /build/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

# --- 运行 ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r spring && useradd -r -g spring spring \
  && apt-get update \
  && apt-get install -y --no-install-recommends gosu \
  && rm -rf /var/lib/apt/lists/*
COPY --from=backend /app/target/*.jar app.jar
COPY docker/docker-entrypoint-app.sh /docker-entrypoint-app.sh
# Windows 检出常为 CRLF；shebang 若含 \r 会报 exec … no such file or directory
RUN sed -i 's/\r$//' /docker-entrypoint-app.sh \
  && chown spring:spring app.jar && chmod +x /docker-entrypoint-app.sh
EXPOSE 8088
ENTRYPOINT ["/docker-entrypoint-app.sh"]
