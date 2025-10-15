pipeline {
    agent {
        kubernetes {
            namespace 'jenkins'
            yaml '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  restartPolicy: Never
  
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
  # Gradle 빌드용
  - name: gradle
    image: gradle:8.5.0-jdk21
    command: ["sleep"]
    args: ["infinity"]
    volumeMounts:
    - name: gradle-cache
      mountPath: /home/jenkins/.gradle
    - name: workspace-volume
      mountPath: /home/jenkins/agent

  # Kaniko - 각 서비스별로 독립 컨테이너
  - name: kaniko-command
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent

  - name: kaniko-query
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent

  - name: kaniko-frontend
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
    - name: kaniko-cache
      mountPath: /cache
    - name: workspace-volume
      mountPath: /home/jenkins/agent

  # kubectl
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

    options {
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        DOCKERHUB_REPO = 'kyla333'
        DEPLOY_NAMESPACE = 'prod'
        IMAGE_TAG = ''
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short=7 HEAD',
                        returnStdout: true
                    ).trim()
                    echo "📦 IMAGE_TAG: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    env.CHANGED_SERVICES = detectChangedServices()
                    if (!env.CHANGED_SERVICES) {
                        echo "ℹ️  No service code changes detected - skipping build & deploy"
                        currentBuild.result = 'SUCCESS'
                        currentBuild.description = "No changes to deploy"
                        env.SKIP_BUILD = 'true'
                    } else {
                        echo "🔍 Changed services: ${env.CHANGED_SERVICES}"
                        currentBuild.description = "Building: ${env.CHANGED_SERVICES}"
                    }
                }
            }
        }

        stage('Build Backend Services') {
            when {
                expression { 
                    env.SKIP_BUILD != 'true' && 
                    (env.CHANGED_SERVICES.contains('command-service') || 
                     env.CHANGED_SERVICES.contains('query-service'))
                }
            }
            steps {
                container('gradle') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        
                        services.each { svc ->
                            if (svc in ['command-service', 'query-service']) {
                                echo "🔨 Building ${svc}..."
                                sh """
                                    cd ${svc}
                                    chmod +x gradlew
                                    ./gradlew clean bootJar -x test --no-daemon
                                    echo "✅ ${svc} build completed"
                                    ls -lh build/libs/
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Build & Push Images') {
            when {
                expression { env.SKIP_BUILD != 'true' }
            }
            parallel {
                stage('Command Service Image') {
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
                                  --cache=true \
                                  --cache-ttl=24h \
                                  --cache-dir=/cache \
                                  --skip-unused-stages \
                                  --compressed-caching=false
                            """
                            echo "✅ command-service image pushed"
                        }
                    }
                }

                stage('Query Service Image') {
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
                                  --cache=true \
                                  --cache-ttl=24h \
                                  --cache-dir=/cache \
                                  --skip-unused-stages \
                                  --compressed-caching=false
                            """
                            echo "✅ query-service image pushed"
                        }
                    }
                }

                stage('Frontend Image') {
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
                                  --cache=true \
                                  --cache-ttl=24h \
                                  --cache-dir=/cache \
                                  --skip-unused-stages \
                                  --compressed-caching=false
                            """
                            echo "✅ todo-frontend image pushed"
                        }
                    }
                }
            }
        }

        stage('Deploy to Production') {
            when {
                expression { env.SKIP_BUILD != 'true' }
            }
            steps {
                container('kubectl') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        echo "🚀 Deploying to ${DEPLOY_NAMESPACE}: ${services.join(', ')}"
                        
                        services.each { svc ->
                            def deploymentName = (svc == 'todo-frontend') ? 'frontend-deployment' : "${svc}-deployment"
                            def containerName = (svc == 'todo-frontend') ? 'frontend' : svc
                            
                            echo "📦 Updating ${deploymentName}..."
                            sh """
                                kubectl set image deployment/${deploymentName} \
                                  ${containerName}=${DOCKERHUB_REPO}/${svc}:${IMAGE_TAG} \
                                  -n ${DEPLOY_NAMESPACE}
                                
                                echo "⏳ Waiting for rollout..."
                                kubectl rollout status deployment/${deploymentName} \
                                  -n ${DEPLOY_NAMESPACE} \
                                  --timeout=5m
                                
                                echo "✅ ${deploymentName} deployed successfully"
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
                if (env.SKIP_BUILD == 'true') {
                    echo "✅ No changes - pipeline completed without deployment"
                } else {
                    echo """
✅ CI/CD Pipeline Completed Successfully!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 Image Tag: ${env.IMAGE_TAG}
🚀 Deployed Services: ${env.CHANGED_SERVICES}
🌐 Namespace: ${DEPLOY_NAMESPACE}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    """
                }
            }
        }
        failure {
            echo """
❌ Pipeline Failed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Please check the logs above for details.
Commit: ${env.GIT_COMMIT}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """
        }
        always {
            echo "🏁 Pipeline finished at ${new Date()}"
        }
    }
}

// ==================== Helper Functions ====================

def detectChangedServices() {
    // 첫 빌드인 경우 모든 서비스 배포
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "🆕 First build - deploying all services"
        return 'command-service,query-service,todo-frontend'
    }

    // 변경된 파일 목록 가져오기
    def diff = sh(
        script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
        returnStdout: true
    ).trim()

    if (!diff) {
        return ''
    }

    def changedFiles = diff.split('\n').findAll { it?.trim() }
    def services = ['command-service', 'query-service', 'todo-frontend']
    def changedServices = []

    // 각 서비스별로 변경 확인
    services.each { svc ->
        if (changedFiles.any { it.startsWith("${svc}/") }) {
            changedServices << svc
        }
    }

    // Jenkinsfile만 변경된 경우
    if (changedServices.isEmpty() && changedFiles.any { it == 'Jenkinsfile' }) {
        echo "⚙️  Only Jenkinsfile changed - skipping deployment"
        return ''
    }

    // Jenkinsfile + 서비스 코드 변경
    if (changedFiles.any { it == 'Jenkinsfile' } && !changedServices.isEmpty()) {
        echo "⚙️  Jenkinsfile and service code changed - deploying: ${changedServices.join(', ')}"
    }

    return changedServices.join(',')
}
