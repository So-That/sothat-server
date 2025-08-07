pipeline {
    agent any

    environment {
        IMAGE_NAME = 'boxty123/backend'
        IMAGE_TAG = 'latest'
        AWS_REGION = 'ap-northeast-2'
    }

    stages {
        stage('Git Clone') {
            steps {
                git url: 'https://github.com/So-That/sothat-server.git', branch: 'master'

                sh '''
                    echo "📁 Current workspace path: $(pwd)"
                    echo "📂 Listing workspace contents:"
                    ls -al

                    echo "🔍 Checking if this is a git repo:"
                    git rev-parse --is-inside-work-tree || echo "⚠️ Not inside a git repo"
                '''
            }
        }

        stage('Docker Build and Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                    echo "Logging in to DockerHub..."
                    echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin

                    docker build -t $IMAGE_NAME:$IMAGE_TAG .
                    docker push $IMAGE_NAME:$IMAGE_TAG

                    docker logout
                    echo "Docker process completed."
                    '''
                }
            }
        }

        stage('Deploy to EC2 via SSM') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials'],
                    string(credentialsId: 'ec2-instance-id', variable: 'INSTANCE_ID')
                ]) {
                    sh '''
                    echo "🚀 Deploying to EC2 via SSM..."

                    aws ssm send-command \
                        --document-name "AWS-RunShellScript" \
                        --comment "Deploying new backend version" \
                        --instance-ids "$INSTANCE_ID" \
                        --region "$AWS_REGION" \
                        --parameters commands='
                            docker compose down
                            docker compose up -d
                        ' \
                        --output text
                    '''
                }
            }
        }

    }
}
