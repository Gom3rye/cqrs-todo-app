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
  containers:
  - name: kubectl
    image: dtzar/helm-kubectl:3.15.0
    command: ["sh","-c"]
    args: ["trap : TERM INT; sleep infinity & wait"]
    volumeMounts:
    - name: workspace-volume
      mountPath: /home/jenkins/agent
      readOnly: false
  - name: jnlp
    image: jenkins/inbound-agent:3341.v0766d82b_dec0-1
    volumeMounts:
    - name: workspace-volume
      mountPath: /home/jenkins/agent
      readOnly: false
  volumes:
  - name: workspace-volume
    emptyDir: {}
'''
    }
  }

  options {
    timeout(time: 45, unit: 'MINUTES')
    skipDefaultCheckout(true)
  }

  environment {
    DOCKERHUB_REPO   = 'kyla333'
    DEPLOY_NAMESPACE = 'prod'
    JENKINS_NS       = 'jenkins'
    KANIKO_IMAGE     = 'gcr.io/kaniko-project/executor:v1.23.2'
    // 💡 동적 값은 여기 넣지 말 것!
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Init Vars') {
      steps {
        script {
          // 안전하게 현재 커밋 해시를 태그로 사용
          env.IMAGE_TAG = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
          echo "IMAGE_TAG resolved to: ${env.IMAGE_TAG}"
        }
      }
    }

    stage('Detect Changes (smart)') {
      steps {
        script {
          env.CHANGED_SERVICES = detectChangedServices()
          if (!env.CHANGED_SERVICES) {
            echo "ℹ️ 서비스 디렉터리 변경 없음 — build & deploy 스킵"
            currentBuild.description = "No service changes"
            env.SKIP_PIPE = 'true'
          } else {
            echo "🔍 Changed services: ${env.CHANGED_SERVICES}"
          }
        }
      }
    }

    stage('Build & Push Images (Kaniko Job per service)') {
      when { expression { env.SKIP_PIPE != 'true' } }
      steps {
        container('kubectl') {
          script {
            def services = env.CHANGED_SERVICES.split(',')
            services.each { svc ->
              def jobName = "kaniko-${svc}-${env.BUILD_NUMBER}"
              echo "🛠 Building & pushing ${svc} with Kaniko Job: ${jobName}"

              def jobYaml = """
apiVersion: batch/v1
kind: Job
metadata:
  name: ${jobName}
  namespace: ${JENKINS_NS}
spec:
  ttlSecondsAfterFinished: 90
  backoffLimit: 0
  template:
    spec:
      serviceAccountName: jenkins-agent
      restartPolicy: Never
      containers:
      - name: kaniko
        image: ${KANIKO_IMAGE}
        args:
          - "--context=git://github.com/Gom3rye/cqrs-todo-app.git#${env.GIT_COMMIT}"
          - "--context-sub-path=${svc}"
          - "--dockerfile=${svc}/Dockerfile"
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
              writeFile file: "kaniko-${svc}.yaml", text: jobYaml

              sh """
                set -euo pipefail
                kubectl delete job ${jobName} -n ${JENKINS_NS} --ignore-not-found
                kubectl apply -f kaniko-${svc}.yaml
                kubectl wait --for=condition=Complete job/${jobName} -n ${JENKINS_NS} --timeout=25m
                kubectl logs job/${jobName} -n ${JENKINS_NS} --all-containers=true --tail=-1 || true
                kubectl delete job ${jobName} -n ${JENKINS_NS} --ignore-not-found
              """
            }
          }
        }
      }
    }

    stage('Deploy to Kubernetes (prod)') {
      when { expression { env.SKIP_PIPE != 'true' } }
      steps {
        container('kubectl') {
          script {
            def services = env.CHANGED_SERVICES.split(',')
            echo "🚀 Deploying to ${DEPLOY_NAMESPACE}: ${services.join(', ')}"
            services.each { svc ->
              def depName = (svc == 'todo-frontend') ? 'frontend-deployment' : "${svc}-deployment"
              def containerName = (svc == 'todo-frontend') ? 'frontend' : svc
              sh """
                set -euo pipefail
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
      echo "✅ Smart CI/CD finished successfully. (tag=${env.IMAGE_TAG}, services=${env.CHANGED_SERVICES ?: 'none'})"
    }
    failure {
      echo "❌ Pipeline failed. Check logs above."
    }
    always {
      echo "📎 Commit: ${env.GIT_COMMIT}"
    }
  }
}

// === helper ===
def detectChangedServices() {
  if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
    echo "🆕 First successful build not found — build all services"
    return 'command-service,query-service,todo-frontend'
  }
  def diff = sh(
    script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
    returnStdout: true
  ).trim()
  if (!diff) return ''
  def files = diff.split('\\n').findAll { it?.trim() }
  def services = ['command-service','query-service','todo-frontend']
  def touched = services.findAll { svc -> files.any { it.startsWith("${svc}/") } }
  def onlyJf = touched.isEmpty() && files.every { it == 'Jenkinsfile' }
  return onlyJf ? '' : touched.join(',')
}

