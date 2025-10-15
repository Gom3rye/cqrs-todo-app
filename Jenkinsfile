
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

  - name: kaniko-command
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
    env:
    - name: KANIKO_DIR
      value: /kaniko-command

  - name: kaniko-query
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
    env:
    - name: KANIKO_DIR
      value: /kaniko-query

  - name: kaniko-frontend
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
    env:
    - name: KANIKO_DIR
      value: /kaniko-frontend

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
                    env.BUILD_NEEDED = (env.CHANGED_SERVICES != '') ? 'true' : 'false'
                    
                    if (env.BUILD_NEEDED == 'true') {
                        echo "🔍 Changed services detected: ${env.CHANGED_SERVICES}"
                    } else {
                        echo "ℹ️  No application code changes detected"
                    }
                }
            }
        }

        stage('Build Backend JARs') {
            when {
                expression { 
                    env.BUILD_NEEDED == 'true' && 
                    (env.CHANGED_SERVICES.contains('command-service') || 
                     env.CHANGED_SERVICES.contains('query-service'))
                }
            }
            steps {
                container('gradle') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        
                        if (services.contains('command-service')) {
                            sh '''
                                echo "🚀 Building command-service..."
                                cd command-service
                                chmod +x gradlew
                                ./gradlew clean build -x test
                            '''
                        }
                        
                        if (services.contains('query-service')) {
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
            when {
                expression { env.BUILD_NEEDED == 'true' }
            }
            parallel {
                stage('Command Service') {
                    when {
                        expression { env.CHANGED_SERVICES.contains('command-service') }
                    }
                    steps {
                        container('kaniko-command') {
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/command-service \
                                --dockerfile=\${WORKSPACE}/command-service/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/command-service:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/command-service:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache \
                                --skip-unused-stages
                            """
                        }
                    }
                }

                stage('Query Service') {
                    when {
                        expression { env.CHANGED_SERVICES.contains('query-service') }
                    }
                    steps {
                        container('kaniko-query') {
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/query-service \
                                --dockerfile=\${WORKSPACE}/query-service/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/query-service:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/query-service:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache \
                                --skip-unused-stages
                            """
                        }
                    }
                }

                stage('Frontend') {
                    when {
                        expression { env.CHANGED_SERVICES.contains('todo-frontend') }
                    }
                    steps {
                        container('kaniko-frontend') {
                            sh """
                                /kaniko/executor \
                                --context=\${WORKSPACE}/todo-frontend \
                                --dockerfile=\${WORKSPACE}/todo-frontend/Dockerfile \
                                --destination=${DOCKERHUB_REPO}/todo-frontend:${IMAGE_TAG} \
                                --destination=${DOCKERHUB_REPO}/todo-frontend:latest \
                                --cache=true --cache-ttl=24h --cache-dir=/cache \
                                --skip-unused-stages
                            """
                        }
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                expression { env.BUILD_NEEDED == 'true' }
            }
            steps {
                container('kubectl') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        
                        echo "🚀 Deploying changed services: ${services.join(', ')}"
                        
                        services.each { service ->
                            def deploymentName = (service == 'todo-frontend') ? 'frontend-deployment' : "${service}-deployment"
                            def containerName = (service == 'todo-frontend') ? 'frontend' : service

                            sh """
                                echo "🔄 Updating image for ${deploymentName}..."
                                kubectl set image deployment/${deploymentName} \
                                    ${containerName}=${DOCKERHUB_REPO}/${service}:${IMAGE_TAG} \
                                    -n ${DEPLOY_NAMESPACE} && \
                                kubectl rollout status deployment/${deploymentName} \
                                    -n ${DEPLOY_NAMESPACE} --timeout=5m
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.BUILD_NEEDED == 'true') {
                    echo "✅ Pipeline completed successfully!"
                    echo "📦 Docker Images tagged: ${IMAGE_TAG}"
                    echo "🚀 Deployed services: ${env.CHANGED_SERVICES}"
                } else {
                    echo "✅ No changes detected - Pipeline skipped deployment"
                }
            }
        }
        failure {
            echo "❌ Pipeline failed. Check stage logs above."
        }
        always {
            echo "📊 Build Summary:"
            echo "   - Git Commit: ${env.GIT_COMMIT}"
            echo "   - Changed Services: ${env.CHANGED_SERVICES ?: 'None'}"
        }
    }
}

def detectChangedServices() {
    // 첫 빌드인 경우 모든 서비스 배포
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "🆕 First build detected — deploying all services"
        return 'command-service,query-service,todo-frontend'
    }

    def changedFiles = sh(
        script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
        returnStdout: true
    ).trim()

    if (!changedFiles) {
        echo "ℹ️  No file changes detected"
        return ''
    }

    def changedFilesList = changedFiles.split('\n').findAll { it }
    def services = ['command-service', 'query-service', 'todo-frontend']
    def changedServices = []

    services.each { service ->
        if (changedFilesList.any { it.startsWith("${service}/") }) {
            changedServices << service
        }
    }

    // Jenkinsfile이 변경되었지만 서비스 코드는 변경되지 않은 경우 → 배포 생략
    if (changedFilesList.any { it == 'Jenkinsfile' } && changedServices.isEmpty()) {
        echo "⚙️  Only Jenkinsfile changed — skipping deployment"
        return ''
    }

    // Jenkinsfile + 서비스 코드 변경 → 변경된 서비스만 배포
    if (changedFilesList.any { it == 'Jenkinsfile' } && !changedServices.isEmpty()) {
        echo "⚙️  Jenkinsfile + service code changed — deploying changed services only"
        return changedServices.join(',')
    }

    return changedServices.join(',')
}
