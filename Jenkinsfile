#!groovy

pipeline {
    agent { node { label 'load-master' } }
    triggers {
      cron '@daily'
    }
    options {
      buildDiscarder logRotator( numToKeepStr: '48' )
    }
    environment {
      TEST_TO_RUN = '*'
      JETTY_BRANCH = 'jetty-11.0.x'
    }
    parameters {
      string(defaultValue: '11.0.11-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
      string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
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
            stage('Build Jetty') {
              agent { node { label 'load-master' } }
              when {
                beforeAgent true
                expression {
                  return JETTY_VERSION.endsWith("SNAPSHOT");
                }
              }
              steps {
                dir("jetty.build") {
                  echo "building jetty ${JETTY_BRANCH}"
                  git url: "https://github.com/eclipse/jetty.project.git", branch: "$JETTY_BRANCH"
                  timeout(time: 30, unit: 'MINUTES') {
                    withEnv(["JAVA_HOME=${ tool "jdk11" }",
                             "PATH+MAVEN=${ tool "jdk11" }/bin:${tool "maven3"}/bin",
                             "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                      configFileProvider(
                              [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                        sh "mvn -Pfast --no-transfer-progress -s $GLOBAL_MVN_SETTINGS -V -B -U -Psnapshot-repositories -am clean install -DskipTests -T6 -e"
                      }
                    }
                  }
                }
              }
            }
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
            }
            post {
              always {
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: "**/target/reports/**/**", allowEmptyArchive: true, onlyIfSuccessful: false
              }
            }
        }
    }
}

