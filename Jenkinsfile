#!groovy

pipeline {
  agent any
  options {
    buildDiscarder logRotator(numToKeepStr: '20')
  }
  parameters {
    string(defaultValue: '', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: '', description: 'Jetty perf branch name to use', name: 'JETTY_PERF_BRANCH')
    string(defaultValue: '', description: 'Test Pattern to use', name: 'TEST_TO_RUN')
    string(defaultValue: '', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: '', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: '', description: 'Use Loom if possible', name: 'USE_LOOM_IF_POSSIBLE')
    string(defaultValue: '', description: 'Extra monitored items', name: 'OPTIONAL_MONITORED_ITEMS')
  }
  tools {
    jdk "${JDK_TO_USE}"
  }
  stages {
    stage('generate-toolchains-file') {
      agent any
      options {
          timeout(time: 30, unit: 'MINUTES')
      }      
      steps {
        jdkpathfinder nodes: ['load-master', 'load-1', 'load-5', 'load-3', 'load-4', 'load-sample'],
                jdkNames: ["${JDK_TO_USE}"]
        stash name: 'toolchains.xml', includes: '*toolchains.xml'
      }
    }
    stage('Get Load nodes') {
      options {
          timeout(time: 30, unit: 'MINUTES')
      }          
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
        stage('install load-5') {
          agent { node { label 'load-5' } }
          steps {
            tool "${JDK_TO_USE}"
            unstash name: 'toolchains.xml'
            sh "cp load-5-toolchains.xml ~/load-5-toolchains.xml"
            sh "echo load-5"
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
                  //sh "mvn -Pfast -ntp -s $GLOBAL_MVN_SETTINGS -V -B -U -Psnapshot-repositories -am clean install -Dmaven.test.skip=true -T6 -e"
                    sh "mvn -DskipTests -Dcheckstyle.skip=true -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -DskipTests -T7 -e" // -Dmaven.build.cache.enabled=false"
                  // cannot use remote cache as doesn't run in k8s so must use an external ipp
                  // sh "mvn -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -e -Dmaven.build.cache.remote.url=dav:http://nginx-cache-service.jenkins.svc.cluster.local:80 -Dmaven.build.cache.remote.enabled=true -Dmaven.build.cache.remote.save.enabled=true -Dmaven.build.cache.remote.server.id=remote-build-cache-server"
                }
              }
            }
          }
        }
      }
    }
    stage('jetty-perf') {
      agent { node { label 'load-master' } }
      options {
          timeout(time: 120, unit: 'MINUTES')
      }            
      steps {
        lock('jetty-perf') {
          // clean the directory before clone
          sh "rm -rf *"
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
              sh "mvn -ntp -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS  -Dmaven.test.failure.ignore=true -V -B -e clean test" +
                  " -Dtest=${TEST_TO_RUN}" +
                  " -Djetty.version=${JETTY_VERSION}" +
                  " -Dtest.jdk.name=${JDK_TO_USE}" +
                  " -Dtest.jdk.useLoom=${USE_LOOM_IF_POSSIBLE}" +
                  " -Dtest.optional.monitored.items=${OPTIONAL_MONITORED_ITEMS}" +
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
