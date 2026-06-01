FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/target/flux-mcp-*.jar /app/app.jar

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "/app/app.jar"]