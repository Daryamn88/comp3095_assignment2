FROM openjdk:21

WORKDIR /app

COPY target/course-service-1.0.0.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]