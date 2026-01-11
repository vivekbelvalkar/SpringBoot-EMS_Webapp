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

    stage('DB Provision') {
      steps {
          sh '''
              set -e
            
              # Check if mariadb is present or not
              if kubectl get statefulset mariadb -n ${DEPLOY_ENV} >/dev/null 2>&1; then
                echo "MariaDB StatefulSet already exists"
              else
                echo "MariaDB not found. Applying mariadb.yaml..."
                kubectl apply -n ${DEPLOY_ENV} -f K8s/mariadb.yaml
              fi

              echo "Waiting for MariaDB to be ready..."
              kubectl rollout status statefulset/mariadb \
                -n ${DEPLOY_ENV} --timeout=300s

              # get MariaDB pod name
              DB_POD=$(kubectl get pods -n ${DEPLOY_ENV} \
                -l app=mariadb \
                -o jsonpath="{.items[0].metadata.name}")

              echo "MariaDB Pod: $DB_POD"

              # Get DB credentials for login
              DB_USER=$(kubectl get secret mariadb-secret -n ${DEPLOY_ENV} \
                -o jsonpath="{.data.user}" | base64 -d)

              DB_PASSWORD=$(kubectl get secret mariadb-secret -n ${DEPLOY_ENV} \
                -o jsonpath="{.data.password}" | base64 -d)

              # Check if database exists
              DB_EXISTS=$(kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
                mysql -u$DB_USER -p$DB_PASSWORD \
                -e "SHOW DATABASES LIKE 'demo';" \
                | grep demo | wc -l)

              if [ "$DB_EXISTS" -eq "0" ]; then
                echo "Database demo not found. Creating..."
                kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
                  mysql -u$DB_USER -p$DB_PASSWORD \
                  -e "CREATE DATABASE demo;"
                echo "Database demo created"
              else
                echo "Database demo already exists"
              fi

              # Check if required tables exists
              TABLE_EXISTS=$(kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
                mysql -u$DB_USER -p$DB_PASSWORD demo \
                -e "SELECT COUNT(*) FROM information_schema.tables \
                    WHERE table_schema='demo' AND table_name IN ('employee','members','roles');" \
                | tail -n 1)

              # Load dump if tables missing
              if [ "$TABLE_EXISTS" -ne "3" ]; then
                echo "Tables not found. Loading populateDB.sql..."
                kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
                  mysql -u$DB_USER -p$DB_PASSWORD demo < SQLs/populateDB.sql
                echo "Database initialized from dump"
              else
                echo "Tables already exist. Skipping DB initialization"
              fi

              # check if DB metrics exporter user exists
	          DB_METRICS_USERNAME=$(kubectl get secret mariadb-secret -n ${DEPLOY_ENV} \
              -o jsonpath="{.data.metrics_username}" | base64 -d)
	          DB_METRICS_USER_PASS=$(kubectl get secret mariadb-secret -n ${DEPLOY_ENV} \
              -o jsonpath="{.data.metrics_password}" | base64 -d)
		
	          DB_METRICS_USER_EXISTS=$(kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
                mysql -u$DB_USER -p$DB_PASSWORD \
                -e "SELECT user FROM mysql.user where user='$DB_METRICS_USERNAME';" \
                | grep $DB_METRICS_USERNAME | wc -l)
		
	          if [ "$DB_METRICS_USER_EXISTS" -eq "0" ]; then
              echo "DB metrics user not found. Executing mariadb_metrics_exporter.sql..."
		          sed -e "s|matrics_username|$DB_METRICS_USERNAME|g" \
			        -e "s|metrics_userpass|$DB_METRICS_USER_PASS|g" \
			        SQLs/mariadb_metrics_exporter.sql \
              | kubectl exec -n ${DEPLOY_ENV} $DB_POD -- \
              mysql -u$DB_USER -p$DB_PASSWORD
              echo "DB metrics user created..."
            else
              echo "DB metrics user already exist. Skipping creation..."
            fi

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

        kubectl rollout status deployment/ems-app -n ${DEPLOY_ENV}
        '''
      }
    }

  }
}
