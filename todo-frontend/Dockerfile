# --- 1단계: React 앱 빌드 ---
# node 18 버전을 빌드 환경으로 사용
FROM node:18-alpine as builder

# 작업 디렉토리 설정
WORKDIR /app

# package.json과 package-lock.json 복사
COPY package*.json ./

# 의존성 설치
RUN npm install

# 소스 코드 전체 복사
COPY . .

# React 앱 빌드
RUN npm run build

# --- 2단계: Nginx로 서비스 ---
# 경량 웹서버인 Nginx 이미지를 사용
FROM nginx:1.25-alpine

# builder 단계에서 빌드된 결과물(정적 파일)을 Nginx의 기본 웹 루트 폴더로 복사
COPY --from=builder /app/build /usr/share/nginx/html

# Nginx 설정을 커스텀 설정으로 교체
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 80번 포트를 외부에 노출
EXPOSE 80

# 컨테이너 시작 시 Nginx 실행
CMD ["nginx", "-g", "daemon off;"]