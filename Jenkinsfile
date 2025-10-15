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
    # 사이드카를 안정적으로 살아있게 유지
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
    timestamps()
  }

  environment {
    DOCKERHUB_REPO   = 'kyla333'      // dockerhub repo (namespace)
    IMAGE_TAG        = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
    DEPLOY_NAMESPACE = 'prod'         // 배포 네임스페이스
    KANIKO_IMAGE     = 'gcr.io/kaniko-project/executor:v1.23.2'
    JENKINS_NS       = 'jenkins'      // Kaniko Job은 jenkins 네임스페이스에서 실행
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
            echo "ℹ️ 서비스 디렉터리 변경 없음 — build & deploy 스킵"
            currentBuild.description = "No service changes"
            env.SKIP_PIPE = 'true'
          } else {
            echo "🔍 Changed services: ${env.CHANGED_SERVICES}"
          }
        }
      }
    }

    // Kaniko는 Dockerfile 안에서 gradle로 빌드하므로, 별도의 JAR 빌드는 생략 (속도/단순성)
    // 원하면 여기서 gradle 빌드 검증 stage를 추가해도 됨.

    stage('Build & Push Images (Kaniko Job per service)') {
      when { expression { env.SKIP_PIPE != 'true' } }
      steps {
        container('kubectl') {
          script {
            def services = env.CHANGED_SERVICES.split(',')
            services.each { svc ->
              def jobName = "kaniko-${svc}-${env.BUILD_NUMBER}"
              echo "🛠 Building & pushing ${svc} with Kaniko Job: ${jobName}"

              // Kaniko Job (git context + sub-path + svc/Dockerfile)
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

              // 적용 & 대기 & 로그 & 정리 (견고성 위해 간단한 재시도 포함)
              sh """
                set -euo pipefail

                kubectl delete job ${jobName} -n ${JENKINS_NS} --ignore-not-found

                # apply (간헐 실패 대비 3회 재시도)
                n=0; until [ \$n -ge 3 ]; do
                  kubectl apply -f kaniko-${svc}.yaml && break
                  n=\$((n+1)); echo "apply retry \$n"; sleep 3
                done

                # 완료 대기
                kubectl wait --for=condition=Complete job/${jobName} -n ${JENKINS_NS} --timeout=25m

                # 빌드 로그 출력(디버깅 도움)
                kubectl logs job/${jobName} -n ${JENKINS_NS} --all-containers=true --tail=-1 || true

                # Job 정리(자동 ttl 있지만 즉시 정리)
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
              // 배포/컨테이너 이름은 클러스터 실제 리소스명과 일치해야 함
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
      echo "✅ Smart CI/CD finished successfully. (tag=${IMAGE_TAG}, services=${env.CHANGED_SERVICES ?: 'none'})"
    }
    failure {
      echo "❌ Pipeline failed. Check logs above."
    }
    always {
      echo "📎 Commit: ${env.GIT_COMMIT}"
    }
  }
}

/**
 * 변경된 서비스만 빌드/배포 (smart)
 * - 첫 성공 이전: 전체 서비스
 * - 이후: 각 서비스 디렉터리(command-service/query-service/todo-frontend) 하위 파일 변경만 해당
 * - Jenkinsfile 단독 변경은 스킵
 */
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

  // Jenkinsfile만 변경 → 스킵
  def onlyJf = touched.isEmpty() && files.every { it == 'Jenkinsfile' }
  return onlyJf ? '' : touched.join(',')
}

