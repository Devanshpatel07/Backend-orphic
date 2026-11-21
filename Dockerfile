# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Download dependencies first (cached between builds when pom.xml is unchanged)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# maven.test.skip skips compiling AND running tests (faster, less memory)
RUN mvn -B -q clean package -Dmaven.test.skip=true

# ---------- Run stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV SPRING_PROFILE=prod
# Keep memory within Render's free-tier 512 MB limit
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

EXPOSE 8080
# Render provides $PORT; the app reads it via server.port=${PORT:8080}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
