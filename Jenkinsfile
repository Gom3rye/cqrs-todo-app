// Jenkinsfile (Kaniko를 사용한 안전한 CI/CD)
pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  containers:
  - name: gradle
    image: gradle:8.5.0-jdk21
    command: ["sleep"]
    args: ["infinity"]
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
    resources:
      requests:
        memory: "512Mi"
        cpu: "500m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  
  volumes:
  - name: docker-config
    secret:
      secretName: dockerhub-secret
      items:
      - key: .dockerconfigjson
        path: config.json
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
                            ./gradlew clean build -x test
                            
                            echo "Building query-service..."
                            cd ../query-service
                            ./gradlew clean build -x test
                        '''
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            parallel {
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
                                    --cache-ttl=24h
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
                                    --cache-ttl=24h
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
                                    --cache-ttl=24h
                                """
                            }
                        }
                    }
                }
            }
        }

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
        always {
            echo "🧹 Cleaning up workspace..."
            cleanWs()
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
