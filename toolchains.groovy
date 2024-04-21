#!/usr/bin/env groovy

def call(Map params = [:]) {


  stage('generate-toolchains-file') {
      agent any
      options {
          timeout(time: 30, unit: 'MINUTES')
      }      
      steps {
        jdkpathfinder nodes: ['load-master-2', 'load-2', 'load-5', 'load-3', 'load-6', 'load-sample'],
                jdkNames: ["${JDK_TO_USE}"]
        stash name: 'toolchains.xml', includes: '*toolchains.xml'
      }
    }
    stage('Get Load nodes') {
      options {
          timeout(time: 30, unit: 'MINUTES')
      }          
      parallel {
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
        stage('install load-6') {
          agent { node { label 'load-6' } }
          steps {
            tool "${JDK_TO_USE}"
            unstash name: 'toolchains.xml'
            sh "cp load-6-toolchains.xml ~/load-6-toolchains.xml"
            sh "echo load-6"
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

  
}
  

