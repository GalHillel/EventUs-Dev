pipeline {
  agent any

  environment {
    IMAGE = 'galhillel/eventus-api'
    NS    = 'eventus'
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 25, unit: 'MINUTES')
  }

  triggers {
    pollSCM('H/2 * * * *')
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          env.SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
          env.TAG = "${env.IMAGE}:${env.SHA}-${env.BUILD_NUMBER}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.SHA}"
        }
      }
    }

    stage('Build') {
      steps {
        sh "docker build --build-arg APP_VERSION=${SHA}-${BUILD_NUMBER} -t ${TAG} backend/event-us"
      }
    }

    stage('Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh """
            echo "\$P" | docker login -u "\$U" --password-stdin
            docker push ${TAG}
            docker logout
          """
        }
      }
    }

    stage('Deploy') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-jenkins', variable: 'KUBECONFIG')]) {
          sh """
            kubectl -n ${NS} set image deployment/eventus-api api=${TAG}
            kubectl -n ${NS} annotate deployment/eventus-api \
              kubernetes.io/change-cause="build ${BUILD_NUMBER} commit ${SHA}" --overwrite
            kubectl -n ${NS} rollout status deployment/eventus-api --timeout=240s
            kubectl -n ${NS} rollout history deployment/eventus-api
          """
        }
      }
    }

    stage('Smoke test') {
      steps {
        sh 'curl -sf --retry 12 --retry-delay 5 --retry-all-errors http://localhost/health/ready'
        sh 'curl -s http://localhost/chaos/status && echo'
      }
    }
  }

  post {
    failure {
      echo 'build failed, the cluster is still running the previous version'
    }
    always {
      sh "docker image rm ${TAG} 2>/dev/null || true"
      cleanWs()
    }
  }
}
