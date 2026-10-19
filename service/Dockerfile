FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/employee-soap-service.jar app.jar
ENV TZ=Africa/Ouagadougou
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
