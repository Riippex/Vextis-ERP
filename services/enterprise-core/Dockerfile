FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/services/enterprise-core
COPY services/enterprise-core/gradlew services/enterprise-core/gradlew.bat ./
COPY services/enterprise-core/settings.gradle.kts services/enterprise-core/build.gradle.kts ./
COPY services/enterprise-core/gradle ./gradle
COPY services/enterprise-core/src ./src
COPY contracts /workspace/contracts
COPY LICENSE NOTICE /workspace/
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 vextis
WORKDIR /app
COPY --from=build /workspace/services/enterprise-core/build/libs/enterprise-core-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
