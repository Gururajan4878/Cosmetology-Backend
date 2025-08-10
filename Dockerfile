FROM gcr.io/distroless/java17
WORKDIR /app
COPY cosmetology-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]