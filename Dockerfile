FROM maven:3.9-eclipse-temurin-17 AS b
WORKDIR /a
COPY . .
RUN mvn -q package -DskipTests
FROM eclipse-temurin:17-jre
COPY --from=b /a/target/lostfound-1.0.jar /app.jar
CMD ["java","-Xmx350m","-XX:TieredStopAtLevel=1","-jar","/app.jar"]
