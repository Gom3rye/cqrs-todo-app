// Jenkinsfile (통합 CI/CD)
pipeline {
    // 이 파이프라인은 쿠버네티스 클러스터 내부의 임시 Pod에서 실행됩니다.
    agent {
        kubernetes {
            // Jenkinsfile을 실행할 임시 Pod(Agent)의 설계도
            yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent # 이전에 만든 권한을 가진 서비스 계정 사용
  containers:
  # Gradle 빌드를 위한 Java/Git 환경 컨테이너
  - name: gradle
    image: gradle:8.5.0-jdk21
    command: ["sleep"]
    args: ["infinity"]
  # kubectl 명령어를 실행하기 위한 컨테이너
  - name: kubectl
    image: bitnami/kubectl:latest
    command: ["sleep"]
    args: ["infinity"]
  # Docker 이미지를 빌드하고 푸시하기 위한 컨테이너
  - name: docker
    image: docker:20.10.17
    command: ["sleep"]
    args: ["infinity"]
    # Jenkins Pod에 연결된 Docker 소켓을 이 컨테이너에도 연결합니다.
    volumeMounts:
    - name: docker-sock
      mountPath: /var/run/docker.sock
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
'''
        }
    }

    // Jenkins에 등록된 Credential을 환경 변수로 안전하게 불러옵니다.
    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        GITHUB_CREDENTIALS = credentials('github-credentials')
        // GIT_COMMIT 환경 변수는 Jenkins가 자동으로 채워줍니다.
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"
    }

    stages {
        // --- CI (지속적 통합) 단계 ---
        stage('Build Backend JARs') {
            steps {
                // 'gradle' 컨테이너 안에서 아래 스크립트를 실행합니다.
                container('gradle') {
                    script {
                        sh "./gradlew clean build -x test"
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            // 3개의 이미지를 병렬로 빌드하고 푸시하여 시간을 절약합니다.
            parallel {
                stage('Command Service') {
                    steps {
                        // 'docker' 컨테이너 안에서 실행합니다.
                        container('docker') {
                            script {
                                // withCredentials 블록으로 Docker Hub 비밀번호를 안전하게 사용합니다.
                                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                                    sh "docker build -t kyla333/command-service:${IMAGE_TAG} ./command-service"
                                    sh "docker build -t kyla333/command-service:latest ./command-service"
                                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                                    sh "docker push kyla333/command-service --all-tags"
                                }
                            }
                        }
                    }
                }
                stage('Query Service') {
                    steps {
                        container('docker') {
                            script {
                                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                                    sh "docker build -t kyla333/query-service:${IMAGE_TAG} ./query-service"
                                    sh "docker build -t kyla333/query-service:latest ./query-service"
                                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                                    sh "docker push kyla333/query-service --all-tags"
                                }
                            }
                        }
                    }
                }
                stage('Frontend') {
                    steps {
                        container('docker') {
                            script {
                                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                                    sh "docker build -t kyla333/todo-frontend:${IMAGE_TAG} ./todo-frontend"
                                    sh "docker build -t kyla333/todo-frontend:latest ./todo-frontend"
                                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                                    sh "docker push kyla333/todo-frontend --all-tags"
                                }
                            }
                        }
                    }
                }
            }
            // 작업이 끝나면 항상 로그아웃합니다.
            post {
                always {
                    container('docker') {
                        sh "docker logout"
                    }
                }
            }
        }

        // --- CD (지속적 배포) 단계 ---
        stage('Deploy to Kubernetes') {
            steps {
                // 'kubectl' 컨테이너 안에서 실행합니다.
                container('kubectl') {
                    script {
                        // 변경된 서비스만 골라서 배포합니다.
                        def changedServices = findChangedServices()
                        if (changedServices) {
                            echo "Deploying changed services: ${changedServices}"
                            changedServices.each { service ->
                                echo "Updating deployment for ${service}..."
                                // 'todo-frontend'는 Deployment 이름이 다르므로 예외 처리
                                def deploymentName = (service == 'todo-frontend') ? 'frontend-deployment' : "${service}-deployment"
                                def containerName = (service == 'todo-frontend') ? 'frontend' : service
                                
                                sh "kubectl set image deployment/${deploymentName} ${containerName}=kyla333/${service}:${IMAGE_TAG}"
                                sh "kubectl rollout status deployment/${deploymentName}"
                            }
                        } else {
                            echo "No application services changed. Skipping deployment."
                        }
                    }
                }
            }
        }
    }
}

// 이 함수는 어떤 서비스의 코드가 변경되었는지 찾아내는 역할을 합니다.
def findChangedServices() {
    // Jenkins가 기본으로 제공하는 GIT_PREVIOUS_SUCCESSFUL_COMMIT 변수를 사용하여
    // 마지막 성공 빌드와 현재 빌드 사이의 변경사항을 확인합니다.
    def changedFiles = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}", returnStdout: true).trim().split('\n')
    def services = ['command-service', 'query-service', 'todo-frontend']
    def changedServices = []

    for (service in services) {
        if (changedFiles.any { it.startsWith(service + '/') }) {
            changedServices.add(service)
        }
    }
    return changedServices
}
