ARG JAVA_VERSION=17

# Step 1: Use microsoft JDK image with maven3 to build the application
FROM mcr.microsoft.com/openjdk/jdk:17-mariner AS builder
RUN tdnf install maven3 -y
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

# Step 2: Use microsoft JDK image for the final image
FROM mcr.microsoft.com/openjdk/jdk:17-mariner
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
