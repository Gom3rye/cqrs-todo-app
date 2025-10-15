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
  - name: kubectl
    image: registry.k8s.io/kubectl:v1.30.2
    command: ["sleep"]
    args: ["infinity"]
  - name: jnlp
    image: jenkins/inbound-agent:3341.v0766d82b_dec0-1
    args: ["$(JENKINS_SECRET)", "$(JENKINS_NAME)"]
'''
    }
  }

  environment {
    DOCKERHUB_REPO = "kyla333"
    IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
    DEPLOY_NAMESPACE = "prod"
  }

  stages {

    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Detect Changes (smart)') {
      steps {
        script {
          env.CHANGED_SERVICES = detectChangedServices()
          if (!env.CHANGED_SERVICES) {
            echo "ℹ️ No service dir changes — skipping build & deploy."
            currentBuild.description = "No changes"
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
              echo "🛠 Building ${svc} with Kaniko Job"
              def jobName = "kaniko-${svc}-${env.BUILD_NUMBER}"

              // Kaniko Job YAML (git context + sub-path + service Dockerfile)
              def jobYaml = """
apiVersion: batch/v1
kind: Job
metadata:
  name: ${jobName}
  namespace: jenkins
spec:
  ttlSecondsAfterFinished: 60
  backoffLimit: 0
  template:
    spec:
      serviceAccountName: jenkins-agent
      restartPolicy: Never
      containers:
      - name: kaniko
        image: gcr.io/kaniko-project/executor:v1.23.2
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
                # 혹시 남아있는 동일 이름 Job 제거
                kubectl delete job ${jobName} -n jenkins --ignore-not-found

                kubectl apply -f kaniko-${svc}.yaml

                # 성공 대기 (Complete)
                kubectl wait --for=condition=Complete job/${jobName} -n jenkins --timeout=20m

                # 로그 보기
                kubectl logs job/${jobName} -n jenkins --all-containers=true --tail=-1 || true

                # 정리(선택) — ttlSecondsAfterFinished 가 있어 자동 삭제되지만 즉시 지워도 됨
                kubectl delete job ${jobName} -n jenkins --ignore-not-found
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
            echo "🚀 Deploying: ${services.join(', ')}"
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
      echo "✅ Smart CI/CD finished successfully."
    }
    failure {
      echo "❌ Pipeline failed. Check stage logs above."
    }
  }
}

def detectChangedServices() {
  if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
    echo "🆕 First build — all services"
    return 'command-service,query-service,todo-frontend'
  }
  def changed = sh(
    script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
    returnStdout: true
  ).trim()
  if (!changed) return ''

  def files = changed.split('\\n').findAll { it }
  def svcList = ['command-service','query-service','todo-frontend']
  def touched = svcList.findAll { svc -> files.any { it.startsWith(\"${svc}/\") } }

  // Jenkinsfile만 바뀐 경우 → 스킵
  if (touched.isEmpty() && files.every { it == 'Jenkinsfile' }) return ''
  return touched.join(',')
}
