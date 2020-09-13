FROM openjdk:8
ADD target/ijudi-api-0.0.1-SNAPSHOT.jar ijudi.jar
EXPOSE 80
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "ijudi.jar"]