#!groovy

pipeline {
    agent any
    tools {
      jdk 'jdk8'
      jdk 'jdk11'
      jdk 'jdk16'
    }
    stages {
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
        stage('install load-1') {
          agent { node { label 'load-1' } }
          steps {
            sh "echo foo"
          }
        }
    }
}

def mavenBuild(jdk, cmdline, mvnName) {
  script {
    try {
      withEnv(["JAVA_HOME=${ tool "$jdk" }",
               "JDK16_PATH=${ tool "jdk16"}",
               "JDK11_PATH=${ tool "jdk11"}",
               "JDK8_PATH=${ tool "jdk8"}",
               "PATH+MAVEN=${ tool "$jdk" }/bin:${tool "$mvnName"}/bin",
               "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
        configFileProvider(
                [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
          sh "mvn --no-transfer-progress -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -V -B -e $cmdline"
        }
      }
    }
    finally
    {
      junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml', allowEmptyResults: true
    }
  }
}
