pipeline {
    agent any

    environment {
        IMAGE_NAME = 'boxty123/backend'
        IMAGE_TAG = 'latest'
    }

    stages {
        stage('Git Clone') {
            steps {
                git url: 'https://github.com/So-That/sothat-server.git', branch: 'master'
            }
        }

        stage('Docker Build and Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                    echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                    docker build -t $IMAGE_NAME:$IMAGE_TAG .
                    docker push $IMAGE_NAME:$IMAGE_TAG
                    docker logout
                    '''
                }
            }
        }
    }
 }
