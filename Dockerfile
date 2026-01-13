# ===============================
# 1) Build stage
# ===============================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy file pom.xml để cache dependency
COPY pom.xml .

# Cache thư viện để build lần sau nhanh hơn
RUN mvn dependency:go-offline -B

# Copy toàn bộ source
COPY src ./src

# Build ra file JAR
RUN mvn clean package -DskipTests

# ===============================
# 2) Run stage
# ===============================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy JAR từ stage build
COPY --from=build /app/target/*.jar app.jar

# Cổng Spring Boot chạy theo Render sẽ map vào 10000
EXPOSE 8080

# Bắt buộc cho Render (chạy lệnh CMD)
ENTRYPOINT ["java", "-jar", "app.jar"]