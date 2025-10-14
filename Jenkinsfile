pipeline {
    agent {
        kubernetes {
            namespace 'jenkins'
            yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent

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
  - name: workspace-volume
    emptyDir: {}

  containers:
  - name: gradle
    image: gradle:8.5.0-jdk21
    command: ["sleep"]
    args: ["infinity"]
    volumeMounts:
    - name: gradle-cache
      mountPath: /home/jenkins/.gradle
    - name: workspace-volume
      mountPath: /home/jenkins/agent
    resources:
      requests:
        cpu: "500m"
        memory: "1Gi"
      limits:
        cpu: "1000m"
        memory: "2Gi"

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent
    resources:
      requests:
        cpu: "500m"
        memory: "512Mi"
      limits:
        cpu: "1000m"
        memory: "1Gi"

  - name: kubectl
    image: lachlanevenson/k8s-kubectl:v1.30.0
    command: ["sleep"]
    args: ["infinity"]
    volumeMounts:
    - name: workspace-volume
      mountPath: /home/jenkins/agent
    resources:
      requests:
        cpu: "100m"
        memory: "128Mi"
      limits:
        cpu: "200m"
        memory: "256Mi"
'''
        }
    }

    environment {
        DOCKERHUB_REPO = "kyla333"
        IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        DEPLOY_NAMESPACE = "prod"
    }

    stages {
        stage('Build Backend JARs') {
            steps {
                container('gradle') {
                    script {
                        sh '''
                            echo "🚀 Building command-service..."
                            cd command-service
                            chmod +x gradlew
                            ./gradlew clean build -x test

                            echo "🚀 Building query-service..."
                            cd ../query-service
                            chmod +x gradlew
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
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/command-service \
                                --dockerfile=\${WORKSPACE}/command-service/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/command-service:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/command-service:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache
                            """
                        }
                    }
                }

                stage('Query Service') {
                    steps {
                        container('kaniko') {
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/query-service \
                                --dockerfile=\${WORKSPACE}/query-service/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/query-service:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/query-service:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache
                            """
                        }
                    }
                }

                stage('Frontend') {
                    steps {
                        container('kaniko') {
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/todo-frontend \
                                --dockerfile=\${WORKSPACE}/todo-frontend/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/todo-frontend:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/todo-frontend:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache
                            """
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
                            echo "🚀 Deploying changed services: ${changedServices.join(', ')}"
                            changedServices.each { service ->
                                def deploymentName = (service == 'todo-frontend') ? 'frontend-deployment' : "${service}-deployment"
                                def containerName = (service == 'todo-frontend') ? 'frontend' : service

                                sh """
                                    echo "🔄 Updating image for ${deploymentName}..."
                                    kubectl set image deployment/${deploymentName} ${containerName}=${DOCKERHUB_REPO}/${service}:${IMAGE_TAG} -n ${DEPLOY_NAMESPACE} && \
                                    kubectl rollout status deployment/${deploymentName} -n ${DEPLOY_NAMESPACE} --timeout=5m
                                """
                            }
                        } else {
                            echo "ℹ️ No application code changed. Skipping deployment."
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline completed successfully!"
            echo "📦 Docker Images tagged: ${IMAGE_TAG}"
        }
        failure {
            echo "❌ Pipeline failed. Check stage logs above."
        }
    }
}

// 🔍 함수: 어떤 서비스가 바뀌었는지 탐지
def findChangedServices() {
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "🆕 First build detected — deploying all services."
        return ['command-service', 'query-service', 'todo-frontend']
    }

    def changedFiles = sh(
        script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
        returnStdout: true
    ).trim().split('\n').findAll { it }

    def services = ['command-service', 'query-service', 'todo-frontend']
    def changedServices = services.findAll { service ->
        changedFiles.any { it.startsWith("${service}/") }
    }

    if (changedFiles.any { it == 'Jenkinsfile' }) {
        echo "⚙️ Jenkinsfile changed — deploying all services."
        return services
    }

    return changedServices
}
