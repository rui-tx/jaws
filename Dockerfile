FROM maven:3.9-eclipse-temurin-17-alpine  AS build

WORKDIR /app

COPY . /app

RUN mvn clean package

FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=build /app/target/jaws-*.jar /app/jaws.jar

EXPOSE 15000

LABEL maintainer="Rui Teixeira <rui.teixeira@minderacodeacademy.com>" \
      version="0.1.2-alpha" \
      description="Just Another Web Server"

ENV WWWPATH=/app/www

# Run the application
CMD ["java", "-jar", "/app/jaws.jar"]