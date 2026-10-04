# Builds and runs the Kiwi Finance API. Used by docker-compose.yml to run the whole app locally.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts lombok.config ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version
COPY finance-engine finance-engine
COPY api api
RUN ./gradlew --no-daemon :api:bootJar -x test \
    && cp "$(ls api/build/libs/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 kiwi
COPY --from=build /workspace/app.jar app.jar
USER kiwi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
