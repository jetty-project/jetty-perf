#!groovy

pipeline {
    agent any
    options {
      buildDiscarder logRotator( numToKeepStr: '100' )
    }
    environment {
      JETTY_LOAD_GENERATOR_VERSION = get_jetty_load_generator_version()
    }
    parameters {
      string(defaultValue: 'jetty-12.0.x', description: 'Jetty branch', name: 'JETTY_BRANCH')
      string(defaultValue: '12.0.0.alpha3', description: 'Jetty Version', name: 'JETTY_VERSION')
      string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')

      string(defaultValue: 'false', description: 'Use Loom if possible', name: 'USE_LOOM_IF_POSSIBLE')
      string(defaultValue: 'main-12.0.x', description: 'Jetty Branch', name: 'JETTY_PERF_BRANCH')
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
                               string(name: 'JETTY_LOAD_GENERATOR_VERSION', value: "${JETTY_LOAD_GENERATOR_VERSION}"),
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

def get_jetty_load_generator_version() {
  if ("$params.JETTY_VERSION".endsWith("SNAPSHOT")) {
    return "4.0.0-SNAPSHOT"
  } else {
    return "4.0.0.alpha3"
  }
}


