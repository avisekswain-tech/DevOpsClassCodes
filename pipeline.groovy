pipeline {
    agent any
    environment {
        branch_name = "master"
        git_url = "https://github.com/pemmasani1200/DevOpsClassCodes.git"
        SERVICE_NAME = "ecs-fargate-cluster-svc"
        IMAGE_VERSION = "${BUILD_NUMBER}"
        TASK_FAMILY = "first-run-task-definition"
        DESIRED_COUNT = "1"
        REGION = "us-east-2"
        CLUSTER_NAME = "ecs-fargate-cluster-test"
		ECR_REPO = "011194234014.dkr.ecr.us-east-2.amazonaws.com/irving"
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                
            }
        }
        stage('Git_Checkout') { 
            steps {
                git branch: branch_name,
                url: git_url
            }
        }
        stage('Maven_Compile') {
            steps {
                sh  "/opt/maven/bin/mvn compile"
            }   
        }
        stage('Maven_codereview') {
            steps {
                sh  "/opt/maven/bin/mvn -P metrics pmd:pmd"
            }   
        }
        stage('Maven_unittesting') {
            steps {
                sh  "/opt/maven/bin/mvn test"
            }   
        }
        stage('Maven_Codecoverage') {
            steps {
                sh  "/opt/maven/bin/mvn cobertura:cobertura -Dcobertura.report.format=xml"
            }   
        }
        stage('Maven_Package') {
            steps {
                sh  "/opt/maven/bin/mvn package"
            }   
        }
        stage('Docker Image Creation') {
            steps {
                sh "sudo docker build -t app:1 ."
            }
        }
        stage('Docker Image Tag') {
            steps {
                sh "sudo docker tag app:1 $ECR_REPO:$BUILD_NUMBER"
            }
        }
        stage('ECR Login') {
            steps {
                sh "sudo aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin 011194234014.dkr.ecr.us-east-2.amazonaws.com"
            }
        }
        stage('Push Image to ECR') {
            steps {
                sh "sudo docker push $ECR_REPO:$BUILD_NUMBER"
            }
        }
        stage('Remove Local copy of Image') {
            steps {
                sh "docker rmi app:1 $ECR_REPO:$BUILD_NUMBER"
            }
        }
        stage('Task Definition Creation') {
            steps {
                sh 'sed -e "s;%BUILD_NUMBER%;${BUILD_NUMBER};g" ${TASK_FAMILY}.json > ${TASK_FAMILY}-${BUILD_NUMBER}.json'
                sh 'aws ecs register-task-definition --family ${TASK_FAMILY} --cli-input-json file://${TASK_FAMILY}-${BUILD_NUMBER}.json'
            }
        }
        stage(' Update Fargate Service') {
            steps {
                sh "aws ecs update-service --cluster ${CLUSTER_NAME} --service ${SERVICE_NAME} --task-definition ${TASK_FAMILY} --desired-count ${DESIRED_COUNT}"
            }
        }
    }
}
