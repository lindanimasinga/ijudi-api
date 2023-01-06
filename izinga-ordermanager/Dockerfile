FROM openjdk:17-alpine
ADD ../target/izinga-ordermanager-1.2.0.jar ijudi.jar
EXPOSE 80
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "--add-opens", "java.base/java.util=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "ijudi.jar"]