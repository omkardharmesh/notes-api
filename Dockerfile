# Stage 1: Build the JAR
# Uses a full JDK image with Gradle to compile the app
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run the JAR
# Uses a lightweight JRE image — no compiler, no Gradle, just enough to run Java
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
