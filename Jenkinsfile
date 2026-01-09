pipeline {
  agent {
    label 'java'
  }

  environment {
    IMAGE_NAME = "ems-springboot-webapp"
    TAG        = "${BUILD_NUMBER}"
    DEPLOY_ENV = ""          
  }

  stages {

    stage('Resolve Environment') {
      steps {
        script {
          if (env.BRANCH_NAME == 'dev') {
            env.DEPLOY_ENV = 'dev'
          } else if (env.BRANCH_NAME == 'main') {
            env.DEPLOY_ENV = 'prod'
          } else {
            env.DEPLOY_ENV = 'dev' 
          }
          echo "Branch: ${env.BRANCH_NAME}, Deploy Env: ${env.DEPLOY_ENV}"
        }
      }
    }

    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Compile') {
      steps {
        sh 'chmod +x mvnw'
        sh './mvnw clean compile'
      }
    }

    stage('Test & Sonar') {
      steps {
        withSonarQubeEnv('sonarqube') {
          sh '''
            ./mvnw verify sonar:sonar \
              -Dspring.profiles.active=ci \
              -Dsonar.projectKey=EMS-Springboot-webapp
          '''
        }
      }
    }

    stage('Quality Gate') {
      steps {
        timeout(time: 5, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('Package') {
      steps {
        sh './mvnw package -DskipTests'
      }
    }

    stage('Docker Build') {
      steps {
        sh '''
          docker build \
            -t ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${IMAGE_NAME}:${TAG} .
        '''
      }
    }

    stage('Docker Push') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: 'dockerhub-PAT',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PAT'
          )
        ]) {
          sh '''
            echo "$DOCKER_PAT" | docker login "$DOCKER_REGISTRY" \
              -u "$DOCKER_USER" --password-stdin

            docker push \
              "$DOCKER_REGISTRY/$DOCKER_NAMESPACE/$IMAGE_NAME:$TAG"

            docker logout "$DOCKER_REGISTRY"
          '''
        }
      }
    }

    stage('Deploy') {
      when {
        branch 'main'
      }
      steps {
        sh '''
        IMAGE="${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${IMAGE_NAME}:${TAG}"

		    kubectl apply -n ${DEPLOY_ENV} -f K8s/configmap.yaml

          sed -e "s|IMAGE_NAME|$IMAGE|g" \
          -e "s|ENV_NAME|${DEPLOY_ENV}|g" \
          K8s/app.yaml \
          | kubectl apply -n ${DEPLOY_ENV} -f -

        kubectl rollout status deployment/ems-app -n ${DEPLOY_ENV}
        '''
      }
    }

  }
}
