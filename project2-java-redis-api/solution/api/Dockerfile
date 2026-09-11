# ============================================================
# Stage 1: build the JAR with Maven (this stage is ~600-700MB)
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Copy only the pom first so Docker can cache the dependency download
# layer separately from source-code changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests
# ============================================================
# Stage 2: runtime — JRE only, no Maven, no JDK compiler, no source
# ============================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
