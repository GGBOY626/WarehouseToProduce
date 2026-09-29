FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM debian:bookworm-slim AS fonts
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-wqy-zenhei python3-fonttools \
    && python3 -c "from fontTools.ttLib import TTCollection; collection=TTCollection('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'); collection.fonts[0].save('/tmp/WenQuanYiZenHei.ttf')" \
    && test -s /tmp/WenQuanYiZenHei.ttf

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/warehouse-backend-1.0.0.jar app.jar
COPY --from=fonts /tmp/WenQuanYiZenHei.ttf /app/fonts/WenQuanYiZenHei.ttf
RUN mkdir -p /data/uploads
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
