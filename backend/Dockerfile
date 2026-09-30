FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM debian:bookworm-slim AS fonts
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-droid-fallback \
    && test -s /usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf \
    && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/warehouse-backend-1.0.0.jar app.jar
COPY --from=fonts /usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf /app/fonts/DroidSansFallbackFull.ttf
RUN mkdir -p /data/uploads
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
