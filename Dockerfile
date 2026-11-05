FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
ENV TZ=Europe/Lisbon
WORKDIR /app
COPY --from=build /build/target/gestao-lar-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
