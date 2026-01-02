pipeline {
    agent any

    environment {
        IMAGE_NAME = 'boxty123/final'
        IMAGE_TAG  = 'latest'
    }

    stages {

        stage('Load Terraform Outputs') {
            steps {
                dir('/terraform') {
                    script {
                        env.ALB_DNS_NAME = sh(
                            script: '''
                            terraform init -input=false
                            terraform output -raw alb_dns_name
                            ''',
                            returnStdout: true
                        ).trim()
                    }
                }

                echo "ALB DNS: ${ALB_DNS_NAME}"
            }
        }

        stage('Docker Build and Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh '''
                    echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                    docker build -t $IMAGE_NAME:$IMAGE_TAG .
                    docker push $IMAGE_NAME:$IMAGE_TAG
                    '''
                }
            }
        }
    }
}
