FROM eclipse-temurin:17-jre

#Create app directory
WORKDIR /app

#Copy the built JAR from target/
COPY target/*.jar ems.jar

#Expose application port
EXPOSE 8080

#Run the application
ENTRYPOINT ["java", "-jar", "ems.jar"]
