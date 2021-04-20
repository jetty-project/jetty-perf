#!groovy

pipeline {
    agent any
    stages {
        stage('ssl-perf') {
            steps {
                withCredentials(bindings: [sshUserPrivateKey(credentialsId: 'jenkins_with_key', \
                                                             keyFileVariable: 'SSH_KEY_FOR_JENKINS', \
                                                             passphraseVariable: '', \
                                                             usernameVariable: '')]) {            
                    sh "mkdir ~/.ssh"
                    sh "cp $SSH_KEY_FOR_JENKINS ~/.ssh/id_rsa"
                    mavenBuild( "jdk11", "clean verify", "maven3")
                }                    
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
          sh "mvn --no-transfer-progress -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -Pci -V -B -e $cmdline"
        }
      }
    }
    finally
    {
      junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml', allowEmptyResults: true
    }
  }
}
