FROM ghcr.io/graalvm/jdk-community:21
COPY target/app.jar /app.jar
# This is the port that your javalin application will listen on
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]