#!groovy

pipeline {
  agent any
  triggers {
    cron '@daily'
  }
  options {
    buildDiscarder logRotator(numToKeepStr: '100')
  }

  parameters {
    string(defaultValue: '10.0.14-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: 'jetty-10.0.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: 'false', description: 'Use Loom if possible', name: 'USE_LOOM_IF_POSSIBLE')
    string(defaultValue: 'main-10.0.x', description: 'Jetty Branch', name: 'JETTY_PERF_BRANCH')
    string(defaultValue: '*', description: 'Jetty Branch', name: 'TEST_TO_RUN')
  }

  stages {
    stage('Jetty Perf Run') {
      steps {
        script {
          def built = build(job: '/load_testing/jetty-perf-main', propagate: false,
                  parameters: [string(name: 'JETTY_VERSION', value: "${JETTY_VERSION}"),
                               string(name: 'JETTY_BRANCH', value: "${JETTY_BRANCH}"),
                               string(name: 'JDK_TO_USE', value: "${JDK_TO_USE}"),
                               string(name: 'JETTY_PERF_BRANCH', value: "${JETTY_PERF_BRANCH}"),
                               string(name: 'TEST_TO_RUN', value: "${TEST_TO_RUN}"),
                               string(name: 'USE_LOOM_IF_POSSIBLE', value: "${USE_LOOM_IF_POSSIBLE}")])
          copyArtifacts(projectName: '/load_testing/jetty-perf-main', selector: specific("${built.number}"));
        }
      }
      post {
        always {
          archiveArtifacts artifacts: "**/target/reports/**/**", allowEmptyArchive: true, onlyIfSuccessful: false
        }
      }
    }
  }
}

