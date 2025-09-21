FROM maven:3.9-eclipse-temurin-21-alpine  AS build

WORKDIR /app

COPY . /app

RUN mvn clean package

FROM openjdk:21-jdk-slim

WORKDIR /app

COPY --from=build /app/target/jaws-*.jar /app/jaws.jar

# resources
COPY --from=build /app/src/main/resources/application.properties /app/src/main/resources/
COPY --from=build /app/src/main/resources/sql               /app/src/main/resources/sql
COPY --from=build /app/src/main/resources/www               /app/www

EXPOSE 15000

LABEL maintainer="Rui Teixeira <ruiteixeira@mailbox.org>" \
      version="0.2-alpha" \
      description="Just Another Web Server"

ENV PORT=15000 \
    WWWPATH=/app/www/ \
    DBPATH=/app/src/main/resources/db.db \
    DBSCHEMAPATH=/app/src/main/resources/sql/db_schema_v1.sql

# Run the application
CMD ["java", "-jar", "/app/jaws.jar"]
