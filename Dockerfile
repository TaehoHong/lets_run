FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=build/libs/running-1.0.0.jar

ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_PORT=8080

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
