FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy source code
COPY common ./common
COPY ingestor-edgar ./ingestor-edgar

# Build the application
RUN ./gradlew :ingestor-edgar:build -x test

# Run the application
CMD ["java", "-jar", "ingestor-edgar/build/libs/ingestor-edgar-1.0.0.jar"]