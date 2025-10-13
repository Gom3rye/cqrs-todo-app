// Jenkinsfile (최종 버전)
properties([
    parameters([
        string(name: 'BRANCH', defaultValue: 'main', description: 'Which branch to build?')
    ])
])
pipeline {
    agent {
        kubernetes {
            cloud 'kubernetes'
            serviceAccount 'jenkins'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: gradle
    image: gradle:8.4.0-jdk21
    command: ["sleep"]
    args: ["99d"]
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    imagePullPolicy: Always
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
      - name: kaniko-secret
        mountPath: /kaniko/.docker
  volumes:
    - name: kaniko-secret
      secret:
        secretName: dockerhub-secret
        items:
          - key: .dockerconfigjson
            path: config.json
"""
        }
    }
    environment {
        DOCKERHUB_ID          = 'kyla333'
        COMMAND_SERVICE_IMG = "${DOCKERHUB_ID}/command-service"
        QUERY_SERVICE_IMG   = "${DOCKERHUB_ID}/query-service"
        FRONTEND_IMG        = "${DOCKERHUB_ID}/todo-frontend"
    }
    stages {
        stage('Checkout') {
            steps {
                git branch: params.BRANCH, url: 'https://github.com/Gom3rye/cqrs-todo-app.git'
                script {
                    env.GIT_COMMIT_SHORT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                }
            }
        }
        stage('Detect Changes') {
            steps {
                script {
                    env.BUILD_COMMAND = "false"
                    env.BUILD_QUERY   = "false"
                    env.BUILD_FRONTEND= "false"
                    def changedFiles = sh(returnStdout: true, script: "git diff --name-only HEAD~1 HEAD").trim()
                    if (changedFiles.contains('command-service/')) { env.BUILD_COMMAND = "true" }
                    if (changedFiles.contains('query-service/')) { env.BUILD_QUERY = "true" }
                    if (changedFiles.contains('todo-frontend/')) { env.BUILD_FRONTEND = "true" }
                }
            }
        }
        stage('Build & Deploy Changed Services') {
            parallel {
                stage('Command Service') {
                    when { expression { env.BUILD_COMMAND == 'true' } }
                    steps {
                        script {
                            def imageName = "${env.COMMAND_SERVICE_IMG}:${env.GIT_COMMIT_SHORT}"
                            container('gradle') { sh './gradlew :command-service:bootJar' }
                            container('kaniko') { sh "/kaniko/executor --context `pwd`/command-service --dockerfile `pwd`/command-service/Dockerfile --destination ${imageName}" }
                            sh "kubectl set image deployment/command-service-deployment command-service=${imageName}"
                            sh "kubectl rollout status deployment/command-service-deployment"
                        }
                    }
                }
                stage('Query Service') {
                    when { expression { env.BUILD_QUERY == 'true' } }
                    steps {
                        script {
                            def imageName = "${env.QUERY_SERVICE_IMG}:${env.GIT_COMMIT_SHORT}"
                            container('gradle') { sh './gradlew :query-service:bootJar' }
                            container('kaniko') { sh "/kaniko/executor --context `pwd`/query-service --dockerfile `pwd`/query-service/Dockerfile --destination ${imageName}" }
                            sh "kubectl set image deployment/query-service-deployment query-service=${imageName}"
                            sh "kubectl rollout status deployment/query-service-deployment"
                        }
                    }
                }
                stage('Frontend') {
                    when { expression { env.BUILD_FRONTEND == 'true' } }
                    steps {
                        script {
                            def imageName = "${env.FRONTEND_IMG}:${env.GIT_COMMIT_SHORT}"
                            container('kaniko') { sh "/kaniko/executor --context `pwd`/todo-frontend --dockerfile `pwd`/todo-frontend/Dockerfile --destination ${imageName}" }
                            sh "kubectl set image deployment/frontend-deployment frontend=${imageName}"
                            sh "kubectl rollout status deployment/frontend-deployment"
                        }
                    }
                }
            }
        }
    }
}
