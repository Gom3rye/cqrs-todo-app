// Jenkinsfile (최종 수정본)
pipeline {
    agent {
        kubernetes {
            namespace 'jenkins'
            yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  # ==================== 캐시 볼륨 추가 ====================
  volumes:
  - name: gradle-cache
    persistentVolumeClaim:
      claimName: gradle-cache-pvc
  - name: kaniko-cache
    persistentVolumeClaim:
      claimName: kaniko-cache-pvc
  - name: docker-config
    secret:
      secretName: dockerhub-secret
      items:
      - key: .dockerconfigjson
        path: config.json
  # =========================================================
  containers:
  - name: gradle
    image: gradle:8.5.0-jdk21
    command: ["sleep"]
    args: ["infinity"]
    # ==================== Gradle 캐시 마운트 ====================
    volumeMounts:
    - name: gradle-cache
      mountPath: /home/jenkins/.gradle
    # ==========================================================
    resources:
      requests:
        memory: "1Gi"
        cpu: "500m"
      limits:
        memory: "2Gi"
        cpu: "1000m"

  - name: kubectl
    image: bitnami/kubectl:latest
    command: ["sleep"]
    args: ["infinity"]
    resources:
      requests:
        memory: "128Mi"
        cpu: "100m"
      limits:
        memory: "256Mi"
        cpu: "200m"

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command: ["/busybox/cat"]
    tty: true
    # ==================== Kaniko 캐시 마운트 ====================
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
    - name: kaniko-cache
      mountPath: /cache
    # ==========================================================
    resources:
      requests:
        memory: "512Mi"
        cpu: "500m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
'''
        }
    }

    environment {
        IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        DOCKERHUB_REPO = "kyla333"
    }

    stages {
        stage('Build Backend JARs') {
            steps {
                container('gradle') {
                    script {
                        sh '''
                            echo "Building command-service..."
                            cd command-service
                            chmod +x gradlew
                            ./gradlew clean build -x test

                            echo "Building query-service..."
                            cd ../query-service
                            chmod +x gradlew
                            ./gradlew clean build -x test
                        '''
                    }
                }
            }
        }
        
        # ==================== parallel → stages (순차 실행) ====================
        stage('Build & Push Docker Images') {
            stages {
                stage('Command Service') {
                    steps {
                        container('kaniko') {
                            script {
                                sh """
                                    /kaniko/executor \
                                    --context=\${WORKSPACE}/command-service \
                                    --dockerfile=\${WORKSPACE}/command-service/Dockerfile \
                                    --destination=${DOCKERHUB_REPO}/command-service:${IMAGE_TAG} \
                                    --destination=${DOCKERHUB_REPO}/command-service:latest \
                                    --cache=true \
                                    --cache-ttl=24h \
                                    --cache-dir=/cache
                                """
                            }
                        }
                    }
                }

                stage('Query Service') {
                    steps {
                        container('kaniko') {
                            script {
                                sh """
                                    /kaniko/executor \
                                    --context=\${WORKSPACE}/query-service \
                                    --dockerfile=\${WORKSPACE}/query-service/Dockerfile \
                                    --destination=${DOCKERHUB_REPO}/query-service:${IMAGE_TAG} \
                                    --destination=${DOCKERHUB_REPO}/query-service:latest \
                                    --cache=true \
                                    --cache-ttl=24h \
                                    --cache-dir=/cache
                                """
                            }
                        }
                    }
                }

                stage('Frontend') {
                    steps {
                        container('kaniko') {
                            script {
                                sh """
                                    /kaniko/executor \
                                    --context=\${WORKSPACE}/todo-frontend \
                                    --dockerfile=\${WORKSPACE}/todo-frontend/Dockerfile \
                                    --destination=${DOCKERHUB_REPO}/todo-frontend:${IMAGE_TAG} \
                                    --destination=${DOCKERHUB_REPO}/todo-frontend:latest \
                                    --cache=true \
                                    --cache-ttl=24h \
                                    --cache-dir=/cache
                                """
                            }
                        }
                    }
                }
            }
        }
        # =======================================================================

        stage('Deploy to Kubernetes') {
            steps {
                container('kubectl') {
                    script {
                        def changedServices = findChangedServices()
                        if (changedServices) {
                            echo "Deploying changed services: ${changedServices}"
                            changedServices.each { service ->
                                echo "Updating deployment for ${service}..."
                                def deploymentName = (service == 'todo-frontend') ? 'frontend-deployment' : "${service}-deployment"
                                def containerName = (service == 'todo-frontend') ? 'frontend' : service

                                sh """
                                    kubectl set image deployment/${deploymentName} \
                                    ${containerName}=${DOCKERHUB_REPO}/${service}:${IMAGE_TAG}

                                    kubectl rollout status deployment/${deploymentName} --timeout=5m
                                """
                            }
                        } else {
                            echo "No application services changed. Skipping deployment."
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline completed successfully!"
            echo "📦 Images pushed with tag: ${IMAGE_TAG}"
        }
        failure {
            echo "❌ Pipeline failed. Check logs for details."
        }
    }
}

def findChangedServices() {
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "First build - deploying all services."
        return ['command-service', 'query-service', 'todo-frontend']
    }

    def changedFiles = sh(
        script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
        returnStdout: true
    ).trim().split('\n')

    def services = ['command-service', 'query-service', 'todo-frontend']
    def changedServices = []

    for (service in services) {
        if (changedFiles.any { it.startsWith(service + '/') }) {
            changedServices.add(service)
        }
    }

    if (changedFiles.any { it == 'Jenkinsfile' }) {
        echo "Jenkinsfile changed - deploying all services."
        return services
    }

    return changedServices
}
