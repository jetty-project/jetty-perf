#!groovy

pipeline {
    agent any
    options {
      buildDiscarder logRotator( numToKeepStr: '30' )
    }
    parameters {
      string(defaultValue: '*', description: 'Junit test to run -Dtest=', name: 'TEST_TO_RUN')
      string(defaultValue: '10.0.3', description: 'Jetty Version', name: 'JETTY_VERSION')
      string(defaultValue: 'release', description: 'Jetty Branch to build (use release if you are using a release or any branch, Jetty Version must match', name: 'JETTY_BRANCH')
      //string(defaultValue: '2.0.0', description: 'LoadGenerator Version', name: 'LOADGENERATOR_VERSION')
      string(defaultValue: '10', description: 'Time in minutes to run load test', name: 'RUN_FOR')
      string(defaultValue: 'load-jdk11', description: 'jdk to use', name: 'JDK_TO_USE')
      string(defaultValue: '-Xmx8g', description: 'extra JVM arguments to use', name: 'EXTRA_ARGS_TO_USE')
    }
    tools {
      jdk "${JDK_TO_USE}"
    }
    stages {
        stage('generate-toolchains-file') {
          agent { node { label 'load-master' } }
          steps {
            jdkpathfinder nodes: ['load-master', 'load-1', 'load-2', 'load-3', 'load-4', 'load-5', 'load-6', 'load-7', 'load-8', 'load-sample', 'zwerg-osx', 'windows-nuc'],
                        jdkNames: ["${JDK_TO_USE}", "load-jdk8", "load-jdk11", "load-jdk16", "load-jdk17"]
            stash name: 'toolchains.xml', includes: '*toolchains.xml'
          }
        }
        stage('Get Load nodes') {
          parallel {
            stage('Build Jetty') {
              agent { node { label 'load-master' } }
              when {
                beforeAgent true
                expression {
                  return JETTY_BRANCH != 'release';
                }
              }
              steps {
                echo "build jetty branch ${JETTY_BRANCH}"
              }
            }
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
            stage('install load-5') {
              agent { node { label 'load-5' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-5-toolchains.xml ~/load-5-toolchains.xml"
                sh "echo load-5"
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
            stage('install load-7') {
              agent { node { label 'load-7' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-7-toolchains.xml ~/load-7-toolchains.xml"
                sh "echo load-7"
              }
            }
            stage('install load-8') {
              agent { node { label 'load-8' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp load-8-toolchains.xml ~/load-8-toolchains.xml"
                sh "echo load-8"
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
            stage('install zwerg-osx') {
              agent { node { label 'zwerg-osx' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                sh "cp zwerg-osx-toolchains.xml  ~/zwerg-osx-toolchains.xml "
                sh "cat zwerg-osx-toolchains.xml"
                sh "echo zwerg-osx"
              }
            }
            stage('install windows-nuc') {
              agent { node { label 'windows-nuc' } }
              steps {
                tool "${JDK_TO_USE}"
                unstash name: 'toolchains.xml'
                bat "copy windows-nuc-toolchains.xml  %systemdrive%%homepath%\\windows-nuc-toolchains.xml "
                //bat "cat windows-nuc-toolchains.xml"
                bat "echo windows-nuc"
              }
            }
          }
        }
        stage('ssl-perf') {
            agent { node { label 'load-master' } }
            steps {
                unstash name: 'toolchains.xml'
                sh "cp load-master-toolchains.xml  ~/load-master-toolchains.xml "
//                echo 'load-master toolchain'
//                sh 'cat load-master-toolchains.xml'
//                echo 'load-1 toolchain'
//                sh 'cat load-1-toolchains.xml'
//                echo 'zwerg-osx toolchain'
//                sh 'cat zwerg-osx-toolchains.xml'
                /*withCredentials(bindings: [sshUserPrivateKey(credentialsId: 'jenkins_with_key', \
                                                             keyFileVariable: 'SSH_KEY_FOR_JENKINS', \
                                                             passphraseVariable: '', \
                                                             usernameVariable: '')]) {     */
                //sh "mkdir ~/.ssh"
                //sh 'cp $SSH_KEY_FOR_JENKINS ~/.ssh/id_rsa'
                withEnv(["JAVA_HOME=${ tool "jdk11" }",
                         "PATH+MAVEN=${ tool "jdk11" }/bin:${tool "maven3"}/bin",
                         "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                  configFileProvider(
                          [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                    sh "mvn --no-transfer-progress -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS -V -B -e clean install -Dtest=${TEST_TO_RUN} -Djetty.version=${JETTY_VERSION} -Dtest.jdk.name=${JDK_TO_USE} -Dtest.jdk.extraArgs=\"${EXTRA_ARGS_TO_USE}\" -Dtest.runFor=${RUN_FOR}"
                    //-Dloadgenerator.version=${LOADGENERATOR_VERSION}"
                  }
                }
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: "**/target/report/**/**",allowEmptyArchive: true
            }
        }
    }
}

