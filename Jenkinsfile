#!groovy
pipeline {
    agent none
    triggers {
      cron '@daily'
    }
    options {
      buildDiscarder logRotator( numToKeepStr: '100' )
    }
    environment {
      // Use JETTY_LOAD_GENERATOR_VERSION = get_jetty_load_generator_version() in case new API changes breaking the load generator get merged.
      JETTY_LOAD_GENERATOR_VERSION = "4.0.0";
    }
    parameters {
      string(defaultValue: 'jetty-12.0.x', description: 'Jetty branch', name: 'JETTY_BRANCH')
      string(defaultValue: '12.0.2-SNAPSHOT', description: 'Jetty version', name: 'JETTY_VERSION')
      string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
      string(defaultValue: 'main-12.0.x', description: 'Jetty perf branch', name: 'JETTY_PERF_BRANCH')
      string(defaultValue: '*', description: 'Test pattern to use', name: 'TEST_TO_RUN')
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
                               string(name: 'JETTY_LOAD_GENERATOR_VERSION', value: "${JETTY_LOAD_GENERATOR_VERSION}")])
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

// def get_jetty_load_generator_version() {
//   if ("$params.JETTY_VERSION".endsWith("SNAPSHOT")) {
//     return "4.0.0-SNAPSHOT"
//   } else {
//     return "4.0.0.beta0"
//   }
// }
