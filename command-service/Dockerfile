# 1. 실행 환경(JRE)이 포함된 가벼운 베이스 이미지 선택
FROM eclipse-temurin:21-jre-jammy

# 2. JAR 파일이 위치할 디렉토리 지정
WORKDIR /app

# 3. 빌드된 JAR 파일을 컨테이너 안으로 복사하고 이름을 app.jar로 변경
# build/libs/ 폴더 안의 어떤 이름의 jar 파일이든 app.jar로 복사됩니다.
COPY build/libs/*.jar /app/
# 4. 컨테이너가 시작될 때 실행할 명령어
# jar 이름이 바뀌어도 실행 가능하도록 와일드카드 실행
CMD ["sh", "-c", "java -jar /app/*.jar"]


