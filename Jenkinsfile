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
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    // IMAGE_TAG를 전역 변수로 설정
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
                        echo "ℹ️  No service code changes - skipping build"
                        currentBuild.result = 'SUCCESS'
                        currentBuild.description = "No changes"
                        env.SKIP_BUILD = 'true'
                    } else {
                        echo "🔍 Changed: ${env.CHANGED_SERVICES}"
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
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
                    def imageTag = env.IMAGE_TAG  // 로컬 변수로 캡처
                    def dockerRepo = env.DOCKERHUB_REPO
                    
                    def builds = [:]
                    
                    if (services.contains('command-service')) {
                        builds['Command Service'] = {
                            container('kaniko-command') {
                                sh """
                                    /kaniko/executor \\
                                      --context=\${WORKSPACE}/command-service \\
                                      --dockerfile=\${WORKSPACE}/command-service/Dockerfile \\
                                      --destination=${dockerRepo}/command-service:${imageTag} \\
                                      --destination=${dockerRepo}/command-service:latest \\
                                      --cache=true --cache-ttl=24h --cache-dir=/cache \\
                                      --cache-repo=${dockerRepo}/command-service-cache
                                """
                                echo "✅ command-service:${imageTag} pushed"
                            }
                        }
                    }
                    
                    if (services.contains('query-service')) {
                        builds['Query Service'] = {
                            container('kaniko-query') {
                                sh """
                                    /kaniko/executor \\
                                      --context=\${WORKSPACE}/query-service \\
                                      --dockerfile=\${WORKSPACE}/query-service/Dockerfile \\
                                      --destination=${dockerRepo}/query-service:${imageTag} \\
                                      --destination=${dockerRepo}/query-service:latest \\
                                      --cache=true --cache-ttl=24h --cache-dir=/cache \\
                                      --cache-repo=${dockerRepo}/query-service-cache
                                """
                                echo "✅ query-service:${imageTag} pushed"
                            }
                        }
                    }
                    
                    if (services.contains('todo-frontend')) {
                        builds['Frontend'] = {
                            container('kaniko-frontend') {
                                sh """
                                    /kaniko/executor \\
                                      --context=\${WORKSPACE}/todo-frontend \\
                                      --dockerfile=\${WORKSPACE}/todo-frontend/Dockerfile \\
                                      --destination=${dockerRepo}/todo-frontend:${imageTag} \\
                                      --destination=${dockerRepo}/todo-frontend:latest \\
                                      --cache=true --cache-ttl=24h --cache-dir=/cache \\
                                      --cache-repo=${dockerRepo}/todo-frontend-cache
                                """
                                echo "✅ todo-frontend:${imageTag} pushed"
                            }
                        }
                    }
                    
                    parallel builds
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
                        def imageTag = env.IMAGE_TAG
                        def dockerRepo = env.DOCKERHUB_REPO
                        def namespace = env.DEPLOY_NAMESPACE
                        
                        echo "🚀 Deploying to ${namespace}: ${services.join(', ')}"
                        
                        services.each { svc ->
                            def deploymentName = (svc == 'todo-frontend') ? 'frontend-deployment' : "${svc}-deployment"
                            def containerName = (svc == 'todo-frontend') ? 'frontend' : svc
                            
                            echo "📦 Updating ${deploymentName}..."
                            sh """
                                kubectl set image deployment/${deploymentName} \
                                  ${containerName}=${dockerRepo}/${svc}:${imageTag} \
                                  -n ${namespace}
                                
                                echo "⏳ Waiting for rollout..."
                                kubectl rollout status deployment/${deploymentName} \
                                  -n ${namespace} \
                                  --timeout=5m
                                
                                echo "✅ ${deploymentName} deployed"
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
                    echo "✅ No changes - pipeline skipped"
                } else {
                    echo """
✅ CI/CD Pipeline Completed!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 Image Tag: ${env.IMAGE_TAG}
🚀 Deployed: ${env.CHANGED_SERVICES}
🌐 Namespace: ${env.DEPLOY_NAMESPACE}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    """
                }
            }
        }
        failure {
            echo """
❌ Pipeline Failed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Check logs above for details.
Commit: ${env.GIT_COMMIT}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """
        }
        always {
            echo "🏁 Finished at ${new Date()}"
        }
    }
}

def detectChangedServices() {
    if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        echo "🆕 First build - deploying all services"
        return 'command-service,query-service,todo-frontend'
    }

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

    services.each { svc ->
        if (changedFiles.any { it.startsWith("${svc}/") }) {
            changedServices << svc
        }
    }

    if (changedServices.isEmpty() && changedFiles.any { it == 'Jenkinsfile' }) {
        echo "⚙️  Only Jenkinsfile changed - skipping"
        return ''
    }

    return changedServices.join(',')
}
