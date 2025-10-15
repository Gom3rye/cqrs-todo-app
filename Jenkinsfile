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

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command: ["/busybox/sh"]
    args: ["-c", "cp /kaniko/.docker/config.json /kaniko-docker/config.json && sleep infinity"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
      readOnly: false    # ✅ 수정 포인트
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent
    - name: workspace-volume
      mountPath: /kaniko-docker     # ✅ 복제 경로 추가

  - name: kubectl
    image: dtzar/helm-kubectl:3.15.0
    command: ["sleep"]
    args: ["infinity"]
    volumeMounts:
    - name: workspace-volume
      mountPath: /home/jenkins/agent
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
                                  --docker-config=/kaniko-docker \
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
                                  --docker-config=/kaniko-docker \
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
                                  --docker-config=/kaniko-docker \
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
                    sh """
                        echo "🚀 Deploying to Kubernetes (namespace: ${DEPLOY_NAMESPACE})..."
                        kubectl set image deployment/command-deployment command-service=${DOCKERHUB_REPO}/command-service:${IMAGE_TAG} -n ${DEPLOY_NAMESPACE} && \
                        kubectl set image deployment/query-deployment query-service=${DOCKERHUB_REPO}/query-service:${IMAGE_TAG} -n ${DEPLOY_NAMESPACE} && \
                        kubectl set image deployment/frontend-deployment frontend=${DOCKERHUB_REPO}/todo-frontend:${IMAGE_TAG} -n ${DEPLOY_NAMESPACE} && \
                        kubectl rollout status deployment/command-deployment -n ${DEPLOY_NAMESPACE} --timeout=5m && \
                        kubectl rollout status deployment/query-deployment -n ${DEPLOY_NAMESPACE} --timeout=5m && \
                        kubectl rollout status deployment/frontend-deployment -n ${DEPLOY_NAMESPACE} --timeout=5m
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline completed successfully!"
        }
        failure {
            echo "❌ Pipeline failed. Check stage logs above."
        }
    }
}
