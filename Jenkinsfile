#!groovy

pipeline {
    agent any
    options {
      buildDiscarder logRotator( numToKeepStr: '10' )
    }
    parameters {
      string(defaultValue: '*', description: 'Test to run', name: 'TEST_TO_RUN')
      string(defaultValue: 'load-jdk11', description: 'Perf JDK tool name', name: 'JDK_TO_USE')
      string(defaultValue: '10.0.6', description: 'Jetty Version', name: 'JETTY_VERSION')
    }
    tools {
      jdk "${JDK_TO_USE}"
    }
    stages {
        stage('generate-toolchains-file') {
          agent { node { label 'load-master' } }
          steps {
            jdkpathfinder nodes: ['load-master', 'load-1', 'load-2', 'load-3', 'load-4', 'load-sample'],
                        jdkNames: ["${JDK_TO_USE}"]
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
              agent { node { label 'load-sample' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-sample-toolchains.xml  ~/load-sample-toolchains.xml "
                sh "cat load-sample-toolchains.xml"
                sh "echo load-sample"
              }
            }
          }
        }
        stage('jetty-perf') {
            agent { node { label 'load-master' } }
            steps {
                unstash name: 'toolchains.xml'
                sh "cp load-master-toolchains.xml  ~/load-master-toolchains.xml "
                withEnv(["JAVA_HOME=${ tool "jdk11" }",
                         "PATH+MAVEN=${ tool "jdk11" }/bin:${tool "maven3"}/bin",
                         "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                  configFileProvider(
                          [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                    sh "mvn --no-transfer-progress -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS -V -B -e clean install -Dtest=${TEST_TO_RUN} -Djetty.version=${JETTY_VERSION} -Dtest.jdk.name=${JDK_TO_USE}"
                    //-Dloadgenerator.version=${LOADGENERATOR_VERSION}"
                  }
                }
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: "**/target/report/**/**",allowEmptyArchive: true
            }
        }
    }
}

