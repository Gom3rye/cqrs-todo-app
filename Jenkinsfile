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
    image: gcr.io/kaniko-project/executor:v1.23.2
    command: ["sleep"]
    args: ["infinity"]
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
      readOnly: true
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent

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

        stage('Detect Changes') {
            steps {
                script {
                    env.CHANGED_SERVICES = detectChangedServices()
                    if (!env.CHANGED_SERVICES) {
                        echo "ℹ️ No service changes detected — skipping build & deploy."
                        currentBuild.result = 'SUCCESS'
                        skipRemainingStages = true
                    } else {
                        echo "🔍 Changed services: ${env.CHANGED_SERVICES}"
                    }
                }
            }
        }

        stage('Build Backend JARs') {
            when { expression { !env.skipRemainingStages && env.CHANGED_SERVICES.contains('command-service') || env.CHANGED_SERVICES.contains('query-service') } }
            steps {
                container('gradle') {
                    script {
                        if (env.CHANGED_SERVICES.contains('command-service')) {
                            sh '''
                                echo "🚀 Building command-service..."
                                cd command-service
                                chmod +x gradlew
                                ./gradlew clean build -x test
                            '''
                        }
                        if (env.CHANGED_SERVICES.contains('query-service')) {
                            sh '''
                                echo "🚀 Building query-service..."
                                cd query-service
                                chmod +x gradlew
                                ./gradlew clean build -x test
                            '''
                        }
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            when { expression { !env.skipRemainingStages } }
            parallel {
                stage('Command Service') {
                    when { expression { env.CHANGED_SERVICES.contains('command-service') } }
                    steps {
                        container('kaniko') {
                            sh """
                                /kaniko/executor \
                                  --context=${WORKSPACE}/command-service \
                                  --dockerfile=${WORKSPACE}/command-service/Dockerfile \
                                  --destination=${DOCKERHUB_REPO}/command-service:${IMAGE_TAG} \
                                  --destination=${DOCKERHUB_REPO}/command-service:latest \
                                  --cache=true --cache-dir=/cache --cache-ttl=24h
                            """
                        }
                    }
                }

                stage('Query Service') {
                    when { expression { env.CHANGED_SERVICES.contains('query-service') } }
                    steps {
                        container('kaniko') {
                            sh """
                                /kaniko/executor \
                                  --context=${WORKSPACE}/query-service \
                                  --dockerfile=${WORKSPACE}/query-service/Dockerfile \
                                  --destination=${DOCKERHUB_REPO}/query-service:${IMAGE_TAG} \
                                  --destination=${DOCKERHUB_REPO}/query-service:latest \
                                  --cache=true --cache-dir=/cache --cache-ttl=24h
                            """
                        }
                    }
                }

                stage('Frontend') {
                    when { expression { env.CHANGED_SERVICES.contains('todo-frontend') } }
                    steps {
                        container('kaniko') {
                            sh """
                                /kaniko/executor \
                                  --context=${WORKSPACE}/todo-frontend \
                                  --dockerfile=${WORKSPACE}/todo-frontend/Dockerfile \
                                  --destination=${DOCKERHUB_REPO}/todo-frontend:${IMAGE_TAG} \
                                  --destination=${DOCKERHUB_REPO}/todo-frontend:latest \
                                  --cache=true --cache-dir=/cache --cache-ttl=24h
                            """
                        }
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { expression { !env.skipRemainingStages } }
            steps {
                container('kubectl') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        echo "🚀 Deploying changed services: ${services.join(', ')}"

                        services.each { svc ->
                            def depName = (svc == 'todo-frontend') ? 'frontend-deployment' : "${svc}-deployment"
                            def containerName = (svc == 'todo-frontend') ? 'frontend' : svc
                            sh """
                                kubectl set image deployment/${depName} ${containerName}=${DOCKERHUB_REPO}/${svc}:${IMAGE_TAG} -n ${DEPLOY_NAMESPACE}
                                kubectl rollout status deployment/${depName} -n ${DEPLOY_NAMESPACE} --timeout=5m
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Smart CI/CD completed successfully!"
        }
        failure {
            echo "❌ Pipeline failed. Check logs above."
        }
    }
}

/** Smart CI/CD Git diff 로직 */
def detectChangedServices() {
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "🆕 First build detected — deploying all services"
        return 'command-service,query-service,todo-frontend'
    }

    def changedFiles = sh(
        script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
        returnStdout: true
    ).trim()

    if (!changedFiles) {
        echo "ℹ️ No file changes detected"
        return ''
    }

    def files = changedFiles.split('\n')
    def targets = ['command-service', 'query-service', 'todo-frontend']
    def changed = []

    targets.each { svc ->
        if (files.any { it.startsWith("${svc}/") }) {
            changed << svc
        }
    }

    if (files.every { it == 'Jenkinsfile' }) {
        echo "⚙️ Only Jenkinsfile changed — skipping deployment"
        return ''
    }

    return changed.join(',')
}
