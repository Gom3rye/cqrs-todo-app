pipeline {
    agent {
        kubernetes {
            namespace 'jenkins'
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
  - name: kubectl
    image: bitnami/kubectl:1.30.2
    command: ["sleep"]
    args: ["infinity"]
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

        stage('Build JARs') {
            when { expression { !env.skipRemainingStages } }
            steps {
                container('gradle') {
                    script {
                        if (env.CHANGED_SERVICES.contains('command-service')) {
                            sh '''
                                cd command-service
                                chmod +x gradlew
                                ./gradlew clean build -x test
                            '''
                        }
                        if (env.CHANGED_SERVICES.contains('query-service')) {
                            sh '''
                                cd query-service
                                chmod +x gradlew
                                ./gradlew clean build -x test
                            '''
                        }
                    }
                }
            }
        }

        stage('Build & Push Images (Kaniko Pod)') {
            when { expression { !env.skipRemainingStages } }
            steps {
                container('kubectl') {
                    script {
                        def services = env.CHANGED_SERVICES.split(',')
                        services.each { svc ->
                            echo "🛠 Building ${svc} with Kaniko pod"

                            def kanikoYaml = """
apiVersion: v1
kind: Pod
metadata:
  name: kaniko-${svc}-${BUILD_NUMBER}
  namespace: jenkins
spec:
  serviceAccountName: jenkins-agent
  restartPolicy: Never
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.23.2
    args:
      - "--context=git://github.com/Gom3rye/cqrs-todo-app.git#${env.GIT_COMMIT}:/workspace/${svc}"
      - "--dockerfile=/workspace/${svc}/Dockerfile"
      - "--destination=${DOCKERHUB_REPO}/${svc}:${IMAGE_TAG}"
      - "--destination=${DOCKERHUB_REPO}/${svc}:latest"
      - "--cache=true"
      - "--cache-ttl=24h"
    volumeMounts:
      - name: docker-config
        mountPath: /kaniko/.docker
        readOnly: true
  volumes:
  - name: docker-config
    secret:
      secretName: dockerhub-secret
"""

                            writeFile file: "kaniko-${svc}.yaml", text: kanikoYaml
                            sh """
                                kubectl delete pod kaniko-${svc}-${BUILD_NUMBER} -n jenkins --ignore-not-found
                                kubectl apply -f kaniko-${svc}.yaml
                                kubectl wait --for=condition=Ready=false pod/kaniko-${svc}-${BUILD_NUMBER} -n jenkins --timeout=600s || true
                                kubectl logs pod/kaniko-${svc}-${BUILD_NUMBER} -n jenkins --all-containers=true --tail=-1 || true
                                kubectl delete pod kaniko-${svc}-${BUILD_NUMBER} -n jenkins --ignore-not-found
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
            echo "✅ Smart CI/CD pipeline completed successfully!"
        }
        failure {
            echo "❌ Pipeline failed. Check logs above."
        }
    }
}

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
    def services = ['command-service', 'query-service', 'todo-frontend']
    def changed = []

    services.each { svc ->
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
