FROM openjdk:21

WORKDIR /app

COPY target/resource-service-1.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]