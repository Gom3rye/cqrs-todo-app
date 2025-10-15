# ---- build stage ----
FROM gradle:8.5.0-jdk21 AS builder
WORKDIR /workspace
COPY . .
# gradlew가 있다면 권한 부여 후 wrapper 사용해도 됩니다
# RUN chmod +x gradlew && ./gradlew clean bootJar -x test
RUN gradle clean bootJar -x test

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# 빌드 결과 복사 (Spring Boot fat jar)
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
