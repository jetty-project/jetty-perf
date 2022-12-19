#!groovy

pipeline {
  agent any
  options {
    buildDiscarder logRotator(numToKeepStr: '20')
  }
  environment {
    TEST_TO_RUN = '*'
  }
  parameters {
    string(defaultValue: '10.0.13-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: 'jetty-10.0.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: 'false', description: 'Use Loom if possible', name: 'USE_LOOM_IF_POSSIBLE')
    string(defaultValue: '', description: 'Load Generator version to use', name: 'JETTY_LOAD_GENERATOR_VERSION')
    string(defaultValue: '', description: 'Jetty perf branch name to use', name: 'JETTY_PERF_BRANCH')
    string(defaultValue: '*', description: 'Test Pattern to use', name: 'TEST_TO_RUN')

  }
  tools {
    jdk "${JDK_TO_USE}"
  }
  stages {
    stage('generate-toolchains-file') {
      agent any
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
    stage('Build Jetty') {
      agent { node { label 'load-master' } }
      when {
        beforeAgent true
        expression {
          return JETTY_VERSION.endsWith("SNAPSHOT");
        }
      }
      steps {
        lock('jetty-perf') {
          dir("jetty.build") {

            echo "building jetty-load-generator 4.0.x"
            checkout([$class           : 'GitSCM',
                      branches         : [[name: "*/4.0.x"]],
                      extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                      userRemoteConfigs: [[url: 'https://github.com/jetty-project/jetty-load-generator.git']]])
            timeout(time: 30, unit: 'MINUTES') {
              withEnv(["JAVA_HOME=${tool "jdk17"}",
                       "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                       "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                configFileProvider(
                        [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                  sh "mvn -Pfast -ntp -s $GLOBAL_MVN_SETTINGS -V -B -U -Psnapshot-repositories -am clean install -Dmaven.test.skip=true -T6 -e"
                }
              }
            }

            echo "building jetty ${JETTY_BRANCH}"
            checkout([$class           : 'GitSCM',
                      branches         : [[name: "*/$JETTY_BRANCH"]],
                      extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                      userRemoteConfigs: [[url: 'https://github.com/eclipse/jetty.project.git']]])
            timeout(time: 30, unit: 'MINUTES') {
              withEnv(["JAVA_HOME=${tool "jdk17"}",
                       "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                       "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                configFileProvider(
                        [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                  sh "mvn -Pfast -ntp -s $GLOBAL_MVN_SETTINGS -V -B -U -Psnapshot-repositories -am clean install -Dmaven.test.skip=true -T6 -e"
                }
              }
            }
          }
        }
      }
    }
    stage('jetty-perf') {
      agent { node { label 'load-master' } }
      steps {
        lock('jetty-perf') {
          unstash name: 'toolchains.xml'
          sh "cp load-master-toolchains.xml  ~/load-master-toolchains.xml "

          checkout([$class           : 'GitSCM',
                    branches         : [[name: "*/$JETTY_PERF_BRANCH"]],
                    extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                    userRemoteConfigs: [[url: 'https://github.com/jetty-project/jetty-perf.git']]])
          withEnv(["JAVA_HOME=${tool "jdk17"}",
                   "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                   "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
            configFileProvider(
                    [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
              sh "mvn -ntp -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS  -Dmaven.test.failure.ignore=true -V -B -e clean install -Dtest=${TEST_TO_RUN} -Djetty.version=${JETTY_VERSION} -Dtest.jdk.name=${JDK_TO_USE} -Dtest.jdk.useLoom=${USE_LOOM_IF_POSSIBLE}" + buildMvnCmd()
            }
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

def buildMvnCmd() {
  String cmd = ""
  if ("${params.JETTY_LOAD_GENERATOR_VERSION}") {
    cmd += " -Djetty-load-generator.version=${params.JETTY_LOAD_GENERATOR_VERSION}"
  }
}
