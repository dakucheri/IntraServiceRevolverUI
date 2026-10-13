FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-jar", "app.jar"]