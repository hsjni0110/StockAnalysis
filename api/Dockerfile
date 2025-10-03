FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy all source code directories
COPY common ./common
COPY api ./api
COPY ingestor-edgar ./ingestor-edgar

# Build the application
RUN ./gradlew :api:build -x test

# Run the application
EXPOSE 8080
CMD ["java", "-jar", "api/build/libs/api-1.0.0.jar"]