FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy source code
COPY common ./common
COPY api ./api

# Build the application
RUN ./gradlew :api:build -x test

# Run the application
EXPOSE 8080
CMD ["java", "-jar", "api/build/libs/api-1.0.0.jar"]