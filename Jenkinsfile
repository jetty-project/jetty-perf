#!groovy

pipeline {
    agent any
    options {
        buildDiscarder logRotator(numToKeepStr: '100')
    }
    parameters {
        // These settings are only used in this script.
        string(defaultValue: 'jetty-12.0.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
        string(defaultValue: '12.0.4-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
        string(defaultValue: '*', description: 'Test Pattern to use', name: 'TEST_TO_RUN')

        // Those settings are used by the test JVM, and some by this script too.
        string(defaultValue: 'load-jdk17', description: 'JDK to use', name: '_JDK_TO_USE')
        string(defaultValue: '', description: 'Extra monitored items, as a CSV string.' +
            ' You can choose from this list: GC_LOGS, ASYNC_PROF_CPU, ASYNC_PROF_ALLOC, ASYNC_PROF_LOCK, ASYNC_PROF_CACHE_MISSES', name: '_OPTIONAL_MONITORED_ITEMS')

        string(defaultValue: 'load-master', description: 'Name of the server machine', name: '_SERVER_NAME')
        string(defaultValue: '-Xms32G -Xmx32G', description: 'Arguments of the server JVM', name: '_SERVER_JVM_OPTS')
        string(defaultValue: 'load-2,load-3,load-4,load-5', description: 'CSV list of names of the loader machines', name: '_LOADER_NAMES')
        string(defaultValue: '-Xms8G -Xmx8G', description: 'Arguments of the loader JVMs', name: '_LOADER_JVM_OPTS')
        string(defaultValue: 'load-sample', description: 'Name of the probe machine', name: '_PROBE_NAME')
        string(defaultValue: '-Xms8G -Xmx8G', description: 'Arguments of the probe JVM', name: '_PROBE_JVM_OPTS')

        string(defaultValue: '60', description: 'Duration of warmup in seconds', name: '_WARMUP_DURATION')
        string(defaultValue: '180', description: 'Duration of measured run in seconds', name: '_RUN_DURATION')
        string(defaultValue: '60000', description: 'Rate of requests/s of each individual loader', name: '_LOADER_RATE')
        string(defaultValue: '1000', description: 'Rate of requests/s of the probe', name: '_PROBE_RATE')
    }
    tools {
        jdk "${_JDK_TO_USE}"
    }
    stages {
        stage('generate-toolchains-file') {
            agent any
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                jdkpathfinder nodes: ['load-master', 'load-1', 'load-2', 'load-3', 'load-4', 'load-5', 'load-6', 'load-7', 'load-8', 'load-sample'],
                    jdkNames: ["${_JDK_TO_USE}"]
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
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-1");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-1-toolchains.xml ~/load-1-toolchains.xml"
                        sh "echo load-1"
                    }
                }
                stage('install load-2') {
                    agent { node { label 'load-2' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-2");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-2-toolchains.xml ~/load-2-toolchains.xml"
                        sh "echo load-2"
                    }
                }
                stage('install load-3') {
                    agent { node { label 'load-3' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-3");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-3-toolchains.xml ~/load-3-toolchains.xml"
                        sh "echo load-3"
                    }
                }
                stage('install load-4') {
                    agent { node { label 'load-4' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-4");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-4-toolchains.xml ~/load-4-toolchains.xml"
                        sh "echo load-4"
                    }
                }
                stage('install load-5') {
                    agent { node { label 'load-5' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-5");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-5-toolchains.xml ~/load-5-toolchains.xml"
                        sh "echo load-5"
                    }
                }
                stage('install load-6') {
                    agent { node { label 'load-6' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-6");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-6-toolchains.xml ~/load-6-toolchains.xml"
                        sh "echo load-6"
                    }
                }
                stage('install load-7') {
                    agent { node { label 'load-7' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-7");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-7-toolchains.xml ~/load-7-toolchains.xml"
                        sh "echo load-7"
                    }
                }
                stage('install load-8') {
                    agent { node { label 'load-8' } }
                    when {
                        beforeAgent true
                        expression {
                            return ${_LOADER_NAMES}.contains("load-8");
                        }
                    }
                    steps {
                        tool "${_JDK_TO_USE}"
                        unstash name: 'toolchains.xml'
                        sh "cp load-8-toolchains.xml ~/load-8-toolchains.xml"
                        sh "echo load-8"
                    }
                }
                stage('install probe') {
                    agent { node { label 'load-sample' } }
                    steps {
                        tool "${_JDK_TO_USE}"
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
        stage('jetty-profiler') {
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
                              branches         : [[name: "*/profiler-12.0.x"]],
                              extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                              userRemoteConfigs: [[url: 'https://github.com/jetty-project/jetty-perf.git']]])
                    withEnv(["JAVA_HOME=${tool "jdk17"}",
                             "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                             "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true"]) {
                        configFileProvider(
                            [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                            sh "mvn -ntp -DtrimStackTrace=false -U -s $GLOBAL_MVN_SETTINGS  -Dmaven.test.failure.ignore=true -V -B -e clean test" +
                                " -Dtest='${TEST_TO_RUN}'" +
                                " -Djetty.version='${JETTY_VERSION}'" +
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
