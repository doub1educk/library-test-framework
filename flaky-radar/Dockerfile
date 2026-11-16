FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 radar
COPY target/flaky-radar.jar app.jar
USER radar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
