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
            echo "ℹ️ No relevant service changes — skipping build & deploy."
            currentBuild.result = 'SUCCESS'
            env.SKIP_PIPE = 'true'
          } else {
            echo "🔍 Changed services: ${env.CHANGED_SERVICES}"
          }
        }
      }
    }

    // (선택) 백엔드 로컬 빌드가 필요 없으므로 이 스테이지는 꺼도 됩니다.
    stage('Optional: Quick Lint/Build Check') {
      when { expression { env.SKIP_PIPE != 'true' } }
      steps {
        container('gradle') {
          sh '''
            echo "Quick check skip or add your lint/unit-tests here if you want"
          '''
        }
      }
    }

    stage('Build & Push Images (Kaniko as Job)') {
      when { expression { env.SKIP_PIPE != 'true' } }
      steps {
        container('kubectl') {
          script {
            def services = env.CHANGED_SERVICES.split(',')
            services.each { svc ->
              echo "🛠 Building ${svc} with Kaniko Job"

              // Kaniko Job(배치) 생성: Git context + sub-path + Dockerfile 지정
              def jobYaml = """
apiVersion: batch/v1
kind: Job
metadata:
  name: kaniko-${svc}-${BUILD_NUMBER}
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

              writeFile file: "kaniko-job-${svc}.yaml", text: jobYaml

              sh """
                # 이전 잔여 Job 제거
                kubectl delete job kaniko-${svc}-${BUILD_NUMBER} -n jenkins --ignore-not-found

                # Job 생성
                kubectl apply -f kaniko-job-${svc}.yaml

                # 완료 대기 (Complete)
                kubectl wait --for=condition=Complete job/kaniko-${svc}-${BUILD_NUMBER} -n jenkins --timeout=15m

                # 로그 확인
                kubectl logs job/kaniko-${svc}-${BUILD_NUMBER} -n jenkins --all-containers=true --tail=-1 || true

                # 정리(선택: ttlSecondsAfterFinished로도 자동 삭제됨)
                kubectl delete job kaniko-${svc}-${BUILD_NUMBER} -n jenkins --ignore-not-found
              """
            }
          }
        }
      }
    }

    stage('Deploy to Kubernetes') {
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
      echo "❌ Pipeline failed. Check logs above."
    }
  }
}

def detectChangedServices() {
  if (!env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
    echo "🆕 First build detected — deploying all services"
    return 'command-service,query-service,todo-frontend'
  }
  def changed = sh(
    script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}",
    returnStdout: true
  ).trim()
  if (!changed) return ''

  def files = changed.split('\n').findAll { it }
  def svcList = ['command-service','query-service','todo-frontend']
  def touched = svcList.findAll { svc -> files.any { it.startsWith("${svc}/") } }

  // Jenkinsfile만 바뀐 경우 스킵
  if (touched.isEmpty() && files.every { it == 'Jenkinsfile' }) return ''
  return touched.join(',')
}
