FROM jenkins/jenkins:lts

USER root

# Docker CLI + Terraform 설치
RUN apt-get update && apt-get install -y \
    docker.io \
    wget \
    unzip

RUN wget https://releases.hashicorp.com/terraform/1.6.6/terraform_1.6.6_linux_amd64.zip \
    && unzip terraform_1.6.6_linux_amd64.zip \
    && mv terraform /usr/local/bin/terraform \
    && rm terraform_1.6.6_linux_amd64.zip

# docker 권한
RUN usermod -aG docker jenkins

USER jenkins
exit
docker restart jenkins
