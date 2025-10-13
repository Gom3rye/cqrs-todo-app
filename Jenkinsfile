// cqrs-todo-app/Jenkinsfile (Kaniko 버전)

pipeline {
    // ✅ agent를 'kaniko' Pod Template으로 지정
    agent {
        label 'kaniko'
    }

    environment {
        REPO_NAME = "kyla333" // DockerHub 사용자 이름
        IMAGE_TAG = "build-${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out source code...'
                checkout scm
            }
        }

        stage('Build & Push Images') {
            parallel {
                stage('Command Service') {
                    steps {
                        script {
                            buildAndPushImage('command-service')
                        }
                    }
                }
                stage('Query Service') {
                    steps {
                        script {
                            buildAndPushImage('query-service')
                        }
                    }
                }
                stage('Frontend Service') {
                    steps {
                        script {
                            buildAndPushImage('todo-frontend')
                        }
                    }
                }
            }
        }

        // Deploy 단계
        stage('Deploy to Dev') {
            steps {
                // jnlp 컨테이너에서 kubectl 명령어 실행
                container('jnlp') {
                    script {
                        echo "Deploying to Development environment..."
                        withKubeconfig([credentialsId: 'kubeconfig']) {
                            dir('k8s') {
                                sh "kustomize edit set image kyla333/command-service=${REPO_NAME}/command-service:${IMAGE_TAG}"
                                sh "kustomize edit set image kyla333/query-service=${REPO_NAME}/query-service:${IMAGE_TAG}"
                                sh "kustomize edit set image kyla333/todo-frontend=${REPO_NAME}/todo-frontend:${IMAGE_TAG}"
                                sh "kustomize build overlays/dev | kubectl apply -f -"
                            }
                        }
                    }
                }
            }
        }

        stage('Approval Gate') {
            steps {
                input message: 'Deploy to Production?', submitter: 'admin'
            }
        }

        stage('Deploy to Prod') {
            steps {
                container('jnlp') {
                    script {
                        echo "Deploying to Production environment..."
                        withKubeconfig([credentialsId: 'kubeconfig']) {
                            dir('k8s') {
                                sh "kustomize edit set image kyla333/command-service=${REPO_NAME}/command-service:${IMAGE_TAG}"
                                sh "kustomize edit set image kyla333/query-service=${REPO_NAME}/query-service:${IMAGE_TAG}"
                                sh "kustomize edit set image kyla333/todo-frontend=${REPO_NAME}/todo-frontend:${IMAGE_TAG}"
                                sh "kustomize build overlays/prod | kubectl apply -f -"
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            echo 'Pipeline finished.'
            cleanWs()
        }
    }
}

// ✅ Kaniko를 사용하도록 수정한 빌드/푸시 함수
def buildAndPushImage(String serviceName) {
    // kaniko 컨테이너에서 빌드 명령어 실행
    container('kaniko') {
        dir(serviceName) {
            // 백엔드 프로젝트인 경우에만 gradle 빌드 실행
            if (serviceName.contains('service')) {
                // jnlp 컨테이너에서 gradle 빌드 실행
                container('jnlp') {
                    echo "Building ${serviceName} with Gradle..."
                    sh 'chmod +x ./gradlew'
                    sh './gradlew build'
                }
            }
            
            echo "Building Docker image for ${serviceName} with Kaniko..."
            def imageName = "${REPO_NAME}/${serviceName}:${IMAGE_TAG}"
            // Kaniko executor 실행
            sh "/kaniko/executor --dockerfile=`pwd`/Dockerfile --context=dir://`pwd` --destination=${imageName}"
        }
    }
}
