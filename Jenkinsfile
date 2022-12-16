#!groovy

pipeline {
  agent any
  triggers {
    cron '@daily'
  }
  options {
    buildDiscarder logRotator(numToKeepStr: '48')
  }
  environment {
    TEST_TO_RUN = '*'
  }
  parameters {
    string(defaultValue: '10.0.13-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
    string(defaultValue: 'jetty-10.0.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
    string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
    string(defaultValue: 'false', description: 'Use Loom if possible', name: 'USE_LOOM_IF_POSSIBLE')
  }

  stages {
    stage('Jetty Perf Run') {
      steps {
        script {
          def built = build(job: 'jetty-perf-main', propagate: false,
                  parameters: [string(name: 'JETTY_VERSION', value: "${JETTY_VERSION}"),
                               string(name: 'JETTY_BRANCH', value: "${JETTY_BRANCH}"),
                               string(name: 'JDK_TO_USE', value: "${JDK_TO_USE}"),
                               string(name: 'JETTY_PERF_BRANCH', value: "main-10.0.x"),
                               string(name: 'USE_LOOM_IF_POSSIBLE', value: "${USE_LOOM_IF_POSSIBLE}")])
          copyArtifacts(projectName: 'jetty-perf-main', selector: specific("${built.number}"));
        }
        post {
          always {
            archiveArtifacts artifacts: "**/target/reports/**/**", allowEmptyArchive: true, onlyIfSuccessful: false
        }
      }
    }
  }
}

