#!groovy
pipeline {
    agent any
    triggers {
      cron '@daily'
    }
    options {
      buildDiscarder logRotator( numToKeepStr: '100' )
    }
    parameters {
      string(defaultValue: '12.0.4-SNAPSHOT', description: 'Jetty version', name: 'JETTY_VERSION')
      string(defaultValue: 'jetty-12.0.x', description: 'Jetty branch', name: 'JETTY_BRANCH')
      string(defaultValue: 'load-jdk17', description: 'JDK to use', name: 'JDK_TO_USE')
      string(defaultValue: '', description: 'Extra monitored items, as a CSV string.' +
          ' You can choose from this list: GC_LOGS, ASYNC_PROF_CPU, ASYNC_PROF_ALLOCATION, ASYNC_PROF_LOCK', name: 'OPTIONAL_MONITORED_ITEMS')
      string(defaultValue: '*', description: 'Test pattern to use', name: 'TEST_TO_RUN')
      string(defaultValue: 'main-12.0.x', description: 'Jetty perf branch', name: 'JETTY_PERF_BRANCH')
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
                               string(name: 'OPTIONAL_MONITORED_ITEMS', value: "${OPTIONAL_MONITORED_ITEMS}"),
                  ])
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
