#!groovy

pipeline {
  agent { node { label 'linux-light' } }
  triggers {
    cron '@daily'
  }
  options {
    buildDiscarder logRotator(numToKeepStr: '100')
  }
  parameters {
    string(defaultValue: 'jetty-12.1.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: 'main-12.1.x', description: 'Jetty perf Branch', name: 'JETTY_PERF_BRANCH')
    string(defaultValue: 'jdk21', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: '*', description: 'Test pattern to use', name: 'TEST_TO_RUN')

    string(defaultValue: 'load-master', description: 'server node', name: 'SERVER_NAME')
    string(defaultValue: 'load-client-1,load-client-2,load-client-3,load-client-4,load-client-5', description: 'loader nodes', name: 'LOADER_NAMES')
    string(defaultValue: 'load-sample', description: 'probe node', name: 'PROBE_NAME')

    string(defaultValue: '12-1-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: '', description: 'Extra monitored items', name: 'OPTIONAL_MONITORED_ITEMS')


  }

  stages {

    stage('Build Jetty') {
      agent { node { label "${SERVER_NAME}" } }
      when {
        beforeAgent true
        expression {
          return JETTY_VERSION.endsWith("SNAPSHOT");
        }
      }
      steps {
        toolchains (jdkToUse: "$JDK_TO_USE", nodes: "$SERVER_NAME,$LOADER_NAMES,$PROBE_NAME")
        lock('jetty-perf') {
          dir("jetty.build") {
            echo "building jetty ${JETTY_BRANCH}"
            sh "rm -rf *"
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
                  sh "mvn -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -DskipTests -e -Dmaven.build.cache.remote.url=http://10.0.0.15:8081/repository/maven-build-cache -Dmaven.build.cache.remote.enabled=true -Dmaven.build.cache.remote.save.enabled=true -Dmaven.build.cache.remote.server.id=nexus-cred"

                  script {
                    def version = sh(
                        script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                        returnStdout: true
                    ).trim()

                    echo "Project version: ${version}"
                    JETTY_VERSION = version
                    echo "Detected Jetty version: $JETTY_VERSION"
                  }

                }
              }
            }
          }
        }
      }
    }
    stage('jetty-perf') {
      agent { node { label "${SERVER_NAME}" } }
      options {
        timeout(time: 120, unit: 'MINUTES')
      }
      steps {
        lock('jetty-perf') {
          // clean the directory before clone
          sh "rm -rf *"
          checkout([$class           : 'GitSCM',
                    branches         : [[name: "*/$JETTY_PERF_BRANCH"]],
                    extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                    userRemoteConfigs: [[url: 'https://github.com/jetty-project/jetty-perf.git']]])
          withEnv(["JAVA_HOME=${tool "jdk17"}",
                   "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                   "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
            configFileProvider(
                [configFile(fileId: 'all-repos', variable: 'GLOBAL_MVN_SETTINGS')]) {
              sh "mvn -ntp -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS -Dmaven.test.failure.ignore=true -V -B -e clean test" +
                  " -Dtest='${TEST_TO_RUN}'" +
                  " -Djetty.version='${JETTY_VERSION}'" +
                  " -Dtest.jdk.name='${JDK_TO_USE}'" +
                  " -Dtest.optional.monitored.items='${OPTIONAL_MONITORED_ITEMS}'" +
                  ""
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
