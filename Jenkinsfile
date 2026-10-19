pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        DOCKER_COMPOSE_FILE = 'docker-compose.yml'
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        COMPOSE_HTTP_TIMEOUT = '300'
        DOCKER_CLIENT_TIMEOUT = '300'
        REGISTRY = 'localhost:5000/'
    }

    stages {

        /* =======================================================
         * 1. CHECKOUT
         * ======================================================= */
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: '*/dev']],
                    userRemoteConfigs: [[
                        url: 'git@github.com:bouzidyImed/BuyMe-microservice.git'
                    ]]
                ])
            }
        }

        /* =======================================================
         * 2. BUILD JAVA MICROSERVICES
         * ======================================================= */
        stage('Build Java Services') {
            steps {
                script {
                    // Shared library consumed by catalogue/order/cart/kafka:
                    // must be installed to the local repo before the services
                    // that depend on it are built.
                    dir('common-kafka') {
                        echo "▶ Building common-kafka (shared library)"
                        sh '''
                            chmod +x mvnw 2>/dev/null || true
                            mvn -B -DskipTests clean install
                        '''
                    }

                    def javaServices = [
                        'api-gateway',
                        'auth-register-service',
                        'catalogue-service',
                        'eureka-server',
                        'order-service',
                        'cart-service',
                        'kafka-service',
                        'payment-service'
                    ]

                    for (svc in javaServices) {
                        dir(svc) {
                            echo "▶ Building ${svc}"
                            sh '''
                                chmod +x mvnw
                                ./mvnw -B -DskipTests clean package
                            '''
                        }
                    }
                }
            }
        }

        /* =======================================================
         * 3. VALIDATE AI SERVICES
         * ======================================================= */
        stage('Validate AI Services') {
            steps {
                script {
                    def aiServices = [
                        'recommender-service',
                        'customer-segmentation-service'
                    ]

                    for (ai in aiServices) {
                        if (fileExists(ai)) {
                            dir(ai) {
                                echo "✔ AI service detected: ${ai}"
                                if (!fileExists('Dockerfile')) {
                                    error("❌ ${ai} is missing Dockerfile")
                                }
                            }
                        } else {
                            echo "⚠ AI service ${ai} not found (skipped)"
                        }
                    }
                }
            }
        }

        /* =======================================================
         * 4. BUILD DOCKER IMAGES
         * ======================================================= */
        stage('Docker Build') {
            steps {
                                echo '▶ Building Docker images...'
                                // Start local registry and mirror base images (uses Jenkins credentials)
                                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                                    sh '''
                                        set -eu
                                        DOCKER_USER="$DOCKER_USER" DOCKER_PASS="$DOCKER_PASS" bash ci/setup-local-registry.sh
                                    '''
                                }

                                sh '''
                                        set -eu
                                        max_retries=3
                                        
                                        attempt=1
                                        attempt=1
                                        until [ "$attempt" -gt "$max_retries" ]; do
                                            echo "▶ docker-compose build attempt #$attempt"
                                            COMPOSE_HTTP_TIMEOUT=${COMPOSE_HTTP_TIMEOUT} DOCKER_CLIENT_TIMEOUT=${DOCKER_CLIENT_TIMEOUT} \
                                                docker-compose -f ${DOCKER_COMPOSE_FILE} build --parallel && break || true
                                            attempt=$((attempt+1))
                                            echo "⏳ waiting before retry..."
                                            sleep 10
                                        done
                                        if [ "$attempt" -gt "$max_retries" ]; then
                                            echo "❌ docker-compose build failed after ${max_retries} attempts"
                                            exit 1
                                        fi
                                '''
            }
        }

        /* =======================================================
         * 5. START STACK
         * ======================================================= */
        stage('Publish Customer Segmentation Image') {
            steps {
                script {
                    echo '▶ Tagging and pushing customer-segmentation-service image (if credentials available)'
                    try {
                        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                            sh '''
                                set -eu
                                IMAGE_NAME=${REGISTRY}customer-segmentation-service:latest
                                echo "Building ${IMAGE_NAME}"
                                docker build -t "${IMAGE_NAME}" ./customer-segmentation-service
                                # login if registry requires authentication; use --password-stdin for safety
                                echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin ${REGISTRY%/} || true
                                docker push "${IMAGE_NAME}"
                            '''
                        }
                    } catch (err) {
                        echo "⚠ Credentials 'dockerhub-creds' not found; skipping image push."
                    }
                }
            }
        }
        stage('Start Stack') {
            steps {
                echo '▶ Starting all services...'
                sh '''
                    docker-compose -f ${DOCKER_COMPOSE_FILE} up -d
                '''
            }
        }

        /* =======================================================
         * 6. WAIT FOR CORE SERVICES
         * ======================================================= */
        stage('Wait for Services') {
            steps {
                echo '▶ Waiting for infrastructure to be ready...'
                sh '''
                                        wait_for() {
                                            name=$1
                                            url=$2
                                            for i in $(seq 1 30); do
                                                if curl -sf "$url" > /dev/null; then
                                                    echo "✔ $name is UP"
                                                    return 0
                                                fi
                                                echo "⏳ Waiting for $name..."
                                                sleep 10
                                            done
                                            echo "❌ $name failed to start"
                                            exit 1
                                        }

                    wait_for "Eureka" "http://localhost:8761"
                    wait_for "API Gateway" "http://localhost:8081/actuator/health"
                    wait_for "Angular Frontend" "http://localhost:4200"
                '''
            }
        }

        /* =======================================================
         * 7. SMOKE TESTS
         * ======================================================= */
        stage('Smoke Tests') {
            steps {
                echo '▶ Running smoke tests...'
                sh '''
                    curl -sf http://localhost:8761
                    curl -sf http://localhost:8081/actuator/health
                    curl -sf http://localhost:4200
                '''
                echo '✔ Smoke tests passed'
            }
        }
    }

    /* =======================================================
     * POST ACTIONS
     * ======================================================= */
    post {

        failure {
            echo '❌ Pipeline failed — dumping logs'
            sh '''
                docker-compose -f ${DOCKER_COMPOSE_FILE} ps || true
                docker-compose -f ${DOCKER_COMPOSE_FILE} logs --tail=300 || true
            '''
        }

        always {
            echo '🧹 Cleaning up Docker resources'
            sh '''
                docker-compose -f ${DOCKER_COMPOSE_FILE} down \
                  --remove-orphans \
                  --volumes || true
            '''
        }

        success {
            echo '✅ Pipeline completed successfully'
        }
    }
}
