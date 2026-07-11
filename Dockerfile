FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S finrag && adduser -S finrag -G finrag
COPY --from=build --chown=finrag:finrag /app/build/libs/*.jar app.jar
USER finrag
EXPOSE 8080
# 512MB no free tier do Render: o default container-aware da JVM daria só 25% (128MB) de heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
