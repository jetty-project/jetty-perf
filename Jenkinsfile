#!groovy

pipeline {
    agent any
    options {
      buildDiscarder logRotator( numToKeepStr: '30' )
    }
    parameters {
      string(defaultValue: '*', description: 'Junit test to run -Dtest=', name: 'TEST_TO_RUN')
      //string(defaultValue: '10.0.2', description: 'Jetty Version', name: 'JETTY_VERSION')
      //string(defaultValue: '2.0.0', description: 'LoadGenerator Version', name: 'LOADGENERATOR_VERSION')
      string(defaultValue: '10', description: 'Time in minutes to run load test', name: 'RUN_FOR')
      string(defaultValue: 'load-jdk11', description: 'jdk to use', name: 'JDK_TO_USE')
      string(defaultValue: '-Xmx8g', description: 'extra JVM arguments to use', name: 'EXTRA_ARGS_TO_USE')
    }
    tools {
      jdk 'load-jdk11'
      jdk 'load-jdk16'
      jdk "${JDK_TO_USE}"
    }
    stages {
        stage('generate-toolchains-file') {
          agent { node { label 'load-master' } }
          steps {
            jdkpathfinder nodes: ['load-master', 'load-1', 'load-2', 'load-3', 'load-4', 'zwerg'],
                        jdkNames: ["${JDK_TO_USE}", "jdk11", "jdk8", "load-jdk16"]
            stash name: 'toolchains.xml', includes: '*toolchains.xml'
          }
        }
        stage('Get Load nodes') {
          parallel {
            stage('install load-1') {
              agent { node { label 'load-1' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-1-toolchains.xml ~/load-1-toolchains.xml"
                sh "echo load-1"
              }
            }
            stage('install load-2') {
              agent { node { label 'load-2' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-2-toolchains.xml ~/load-2-toolchains.xml"
                sh "echo load-2"
              }
            }
            stage('install load-3') {
              agent { node { label 'load-3' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-3-toolchains.xml ~/load-3-toolchains.xml"
                sh "echo load-3"
              }
            }
            stage('install load-4') {
              agent { node { label 'load-4' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-4-toolchains.xml ~/load-4-toolchains.xml"
                sh "echo load-4"
              }
            }
            stage('install probe') {
              agent { node { label 'zwerg-osx' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp zwerg-toolchains.xml  ~/zwerg-toolchains.xml "
                sh "echo zwerg"
              }
            }
          }
        }
        stage('ssl-perf') {
            agent { node { label 'load-master' } }
            steps {
                unstash name: 'toolchains.xml'
                sh "cp load-master-toolchains.xml  ~/load-master-toolchains.xml "
//                echo 'load-master toolchain'
//                sh 'cat load-master-toolchains.xml'
//                echo 'load-1 toolchain'
//                sh 'cat load-1-toolchains.xml'
//                echo 'zwerg-osx toolchain'
//                sh 'cat zwerg-osx-toolchains.xml'
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
                    sh "mvn --no-transfer-progress -DtrimStackTrace=false -s $GLOBAL_MVN_SETTINGS -V -B -e clean install -Dtest=${TEST_TO_RUN} -Dtest.jdk.name=${JDK_TO_USE} -Dtest.jdk.extraArgs=\"${EXTRA_ARGS_TO_USE}\" -Dtest.runFor=${RUN_FOR}"
                    //-Djetty.version=${JETTY_VERSION} -Dloadgenerator.version=${LOADGENERATOR_VERSION}"
                  }
                }
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: "**/target/report/**/**",allowEmptyArchive: true
            }
        }
    }
}

