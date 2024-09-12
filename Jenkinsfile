#!groovy

pipeline {
  agent any
  options {
    buildDiscarder logRotator(numToKeepStr: '20')
  }
  parameters {
    string(defaultValue: 'load-master-2', description: 'server node', name: 'SERVER_NAME')
    string(defaultValue: 'load-1,load-2,load-3,load-4', description: 'loader nodes', name: 'LOADER_NAMES')
    string(defaultValue: 'load-sample', description: 'probe node', name: 'PROBE_NAME')

    string(defaultValue: '', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: '', description: 'Jetty perf branch name to use', name: 'JETTY_PERF_BRANCH')
    string(defaultValue: '', description: 'Test Pattern to use', name: 'TEST_TO_RUN')
    string(defaultValue: '', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: '', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: '', description: 'Extra monitored items', name: 'OPTIONAL_MONITORED_ITEMS')
  }
  tools {
    jdk "${JDK_TO_USE}"
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
                  // sh "mvn -Pfast -ntp -s $GLOBAL_MVN_SETTINGS -V -B -U -Psnapshot-repositories -am clean install -Dmaven.test.skip=true -T6 -e"
                  //  sh "mvn -DskipTests -Dcheckstyle.skip=true -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -DskipTests -T7 -e" // -Dmaven.build.cache.enabled=false"
                  sh "mvn -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -e -Dmaven.build.cache.remote.url=http://10.0.0.15:8081/repository/maven-build-cache -Dmaven.build.cache.remote.enabled=true -Dmaven.build.cache.remote.save.enabled=true -Dmaven.build.cache.remote.server.id=nexus-cred"
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
              sh "mvn -ntp -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS  -Dmaven.test.failure.ignore=true -V -B -e clean test" +
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
