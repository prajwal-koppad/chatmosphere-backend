FROM amd64/openjdk:21-jdk-slim
WORKDIR /app
COPY target/chatmosphere-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]







