#!groovy

pipeline {
    agent any
    tools {
      jdk 'jdk8'
      jdk 'jdk11'
      jdk 'jdk16'
    }
    options {
      buildDiscarder logRotator( numToKeepStr: '30' )
    }
    parameters {
      string(defaultValue: '*', description: 'Junit test to run -Dtest=', name: 'TEST_TO_RUN')
      string(defaultValue: '10.0.2', description: 'Jetty Version', name: 'JETTY_VERSION')
      string(defaultValue: '2.0.0', description: 'LoadGenerator Version', name: 'LOADGENERATOR_VERSION')
      string(defaultValue: '10', description: 'Time in minutes to run load test', name: 'RUN_FOR')
      string(defaultValue: 'jdk11', description: 'jdk to use', name: 'JDK_TO_USE')
      string(defaultValue: '-Xmx8g', description: 'extra JVM arguments to use', name: 'EXTRA_ARGS_TO_USE')
    }
    stages {
        stage('Get Load nodes') {
          parallel {
            stage('install load-1') {
              agent { node { label 'load-1' } }
              steps {
                sh "echo load-1"
              }
            }
            stage('install load-2') {
              agent { node { label 'load-2' } }
              steps {
                sh "echo load-2"
              }
            }
            stage('install load-3') {
              agent { node { label 'load-3' } }
              steps {
                sh "echo load-3"
              }
            }
            stage('install load-4') {
              agent { node { label 'load-4' } }
              steps {
                sh "echo load-4"
              }
            }
          }
        }
        stage('ssl-perf') {
            agent { node { label 'load-master' } }
            steps {
                /*withCredentials(bindings: [sshUserPrivateKey(credentialsId: 'jenkins_with_key', \
                                                             keyFileVariable: 'SSH_KEY_FOR_JENKINS', \
                                                             passphraseVariable: '', \
                                                             usernameVariable: '')]) {     */
                //sh "mkdir ~/.ssh"
                //sh 'cp $SSH_KEY_FOR_JENKINS ~/.ssh/id_rsa'
                withEnv(["JAVA_HOME=${ tool "jdk11" }",
                         "PATH+MAVEN=${ tool "jdk11" }/bin:${tool "maven3"}/bin",
                         "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                  configFileProvider(
                          [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                    sh "mvn --no-transfer-progress -DtrimStackTrace=false -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -V -B -e install -Dtest=${TEST_TO_RUN} -Dtest.jdk.name=${JDK_TO_USE} -Dtest.jdk.extraArgs=\"${EXTRA_ARGS_TO_USE}\" -Dtest.runFor=${RUN_FOR} -Djetty.version=${JETTY_VERSION} -Dloadgenerator.version=${LOADGENERATOR_VERSION}"
                  }
                }
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: "**/target/report/**/**",allowEmptyArchive: true
            }
        }
    }
}

