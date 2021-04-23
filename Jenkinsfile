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
      string(defaultValue: 'false', description: 'use load generator', name: 'USE_LOADGENERATOR')
    }
    stages {
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
        stage('ssl-perf') {
            agent { node { label 'load-master' } }
            steps {
                /*withCredentials(bindings: [sshUserPrivateKey(credentialsId: 'jenkins_with_key', \
                                                             keyFileVariable: 'SSH_KEY_FOR_JENKINS', \
                                                             passphraseVariable: '', \
                                                             usernameVariable: '')]) {     */
                //sh "mkdir ~/.ssh"
                //sh 'cp $SSH_KEY_FOR_JENKINS ~/.ssh/id_rsa'
                mavenBuild( "jdk11", "clean verify", "maven3")
                //}
            }
        }
    }
}

def mavenBuild(jdk, cmdline, mvnName) {
  script {
    try {
      withEnv(["JAVA_HOME=${ tool "$jdk" }",
               "PATH+MAVEN=${ tool "$jdk" }/bin:${tool "$mvnName"}/bin",
               "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
        configFileProvider(
                [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
          sh "mvn --no-transfer-progress -DtrimStackTrace=false -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -V -B -e $cmdline -Dtest=${TEST_TO_RUN} -Dperf.useLoadGenerator=${USE_LOADGENERATOR}"
        }
      }
    }
    finally
    {
      junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml', allowEmptyResults: true
    }
  }
}
