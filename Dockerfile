FROM openjdk:8
ADD target/ijudi-0.0.1-SNAPSHOT.jar ijudi-0.0.1-SNAPSHOT.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=dev", "WFS-API-0.0.1-SNAPSHOT.jar"]