# 1. Java 21 JRE 기반의 경량 이미지 사용
FROM eclipse-temurin:21-jre-jammy

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 고정된 이름의 JAR 파일 복사
COPY build/libs/app.jar /app/app.jar

# 4. 애플리케이션 실행
CMD ["java", "-jar", "/app/app.jar"]
