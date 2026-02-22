pipeline {
  agent {
    label 'java'
  }

  environment {
    IMAGE_NAME = "ems-springboot-webapp"
    TAG        = "${BUILD_NUMBER}"          
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
              -Dsonar.projectKey=EMS-Springboot-webapp-${DEPLOY_ENV}
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

    stage('Trivy Scan') {
      steps {
        sh '''
          docker exec trivy_server trivy image --server ${TRIVY_SERVER} \
          --severity CRITICAL --exit-code 1 ${DOCKER_NAMESPACE}/${IMAGE_NAME}:${TAG}
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

    stage('DB Provision') {
      steps {
          sh '''
              set -e
            
              echo "Applying MariaDB StatefulSet..."
              kubectl apply -n ${DEPLOY_ENV} -f K8s/mariadb.yaml

              echo "Waiting for MariaDB to be ready..."
              kubectl rollout status statefulset/mariadb \
                -n ${DEPLOY_ENV} --timeout=300s

              kubectl apply -n ${DEPLOY_ENV} -f K8s/mariadb-bootstrap-sql-cm.yaml

              kubectl delete job mariadb-bootstrap-job -n ${DEPLOY_ENV} --ignore-not-found
              kubectl apply -n ${DEPLOY_ENV} -f K8s/mariadb-bootstrap-job.yaml

              kubectl wait --for=condition=complete job/mariadb-bootstrap-job \
              -n ${DEPLOY_ENV} --timeout=5m

              kubectl apply -n ${DEPLOY_ENV} -f K8s/mariadb_metrics_exporter.yaml

              kubectl rollout status deployment/mariadb-exporter -n ${DEPLOY_ENV} --timeout=2m

              echo " DB Checks Completed "
            '''
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

        sed -e "s|ENV_NAME|${DEPLOY_ENV}|g" \
          K8s/prometheusSvcMonitors.yaml \
          | kubectl apply -f -

        kubectl rollout status deployment/ems-app -n ${DEPLOY_ENV}
        '''
      }
    }

  }
}
