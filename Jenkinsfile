pipeline {
    agent any

    environment {
        IMAGE_NAME = 'boxty123/backend'
        IMAGE_TAG = 'latest'
    }

    stages {
        stage('Git Clone') {
            steps {
                script {
                    echo "📦 Cloning Git Repository..."
                }
                // 실제 Git 클론
                git url: 'https://github.com/So-That/sothat-server.git', branch: 'master'

                // 클론 이후 워크스페이스 확인
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
                    echo "🐳 Logging in to DockerHub..."
                    echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin

                    echo "⚙️ Building Docker image..."
                    docker build -t $IMAGE_NAME:$IMAGE_TAG .

                    echo "📤 Pushing Docker image to DockerHub..."
                    docker push $IMAGE_NAME:$IMAGE_TAG

                    docker logout
                    echo "✅ Docker process completed."
                    '''
                }
            }
        }
    }
}
