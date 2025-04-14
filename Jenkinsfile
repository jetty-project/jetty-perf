#!groovy

pipeline {
    agent { node { label 'load-master' } }
    options {
        buildDiscarder logRotator(numToKeepStr: '100')
    }
    parameters {
        // These settings are only used in this script.
        string(defaultValue: 'jetty-12.0.x', description: 'Jetty Branch', name: 'JETTY_BRANCH')
        string(defaultValue: '12.0.20-SNAPSHOT', description: 'Jetty Version', name: 'JETTY_VERSION')
        string(defaultValue: 'profiler-12.0.x', description: 'Profiler Branch', name: 'PROFILER_BRANCH')
        string(defaultValue: 'CoreHandlerPerfTest#testNoGzipAsync', description: 'Test Pattern to use, e.g.: CoreHandlerPerfTest, EE9ServletPerfTest, EE10ServletPerfTest', name: 'TEST_TO_RUN')

        // These settings are used both by the test JVM and by this script too.
        string(defaultValue: 'load-jdk21', description: 'JDK to use', name: 'JDK_TO_USE')

        // These settings are only used by the test JVM.
        string(defaultValue: 'ASYNC_PROF_CPU', description: 'Extra monitored items, as a CSV string.' +
            ' You can choose from this list: GC_LOGS, SJK_TTOP, ASYNC_PROF_CPU, ASYNC_PROF_ALLOC, ASYNC_PROF_LOCK, ASYNC_PROF_CACHE_MISSES', name: 'OPTIONAL_MONITORED_ITEMS')

        string(defaultValue: 'load-master', description: 'Name of the server machine', name: 'SERVER_NAME')
        string(defaultValue: '-Xms32G -Xmx32G', description: 'Arguments of the server JVM', name: 'SERVER_JVM_OPTS')
        string(defaultValue: 'load-client-1,load-client-2,load-client-3,load-client-4', description: 'CSV list of names of the loader machines', name: 'LOADER_NAMES')
        string(defaultValue: '-Xms8G -Xmx8G', description: 'Arguments of the loader JVMs', name: 'LOADER_JVM_OPTS')
        string(defaultValue: 'load-client-5', description: 'Name of the probe machine', name: 'PROBE_NAME')
        string(defaultValue: '-Xms8G -Xmx8G', description: 'Arguments of the probe JVM', name: 'PROBE_JVM_OPTS')

        string(defaultValue: '60', description: 'Duration of warmup in seconds', name: 'WARMUP_DURATION')
        string(defaultValue: '180', description: 'Duration of measured run in seconds', name: 'RUN_DURATION')
        string(defaultValue: '60000', description: 'Rate of requests/s of each individual loader', name: 'LOADER_RATE')
        string(defaultValue: '1', description: 'Number of threads used by the loaders, no value or a value < 1 indicates to use the number of cores', name: 'LOADER_THREADS')
        string(defaultValue: '6000', description: 'Rate of requests/s of the probe', name: 'PROBE_RATE')
        string(defaultValue: '', description: 'The loaders\' connection pool type. You can choose from this list: first, round-robin, random', name: 'LOADER_CONNECTION_POOL_FACTORY_TYPE')
        string(defaultValue: '', description: 'The loaders\' max connection per destination', name: 'LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION')
        string(defaultValue: '', description: 'The number of acceptor threads to use', name: 'SERVER_ACCEPTOR_COUNT')
        string(defaultValue: '', description: 'The number of selector threads to use', name: 'SERVER_SELECTOR_COUNT')
        string(defaultValue: '', description: 'Use virtual threads. Defaults to false', name: 'SERVER_USE_VIRTUAL_THREADS')
        string(defaultValue: '', description: 'Pool byte buffers. Defaults to true', name: 'SERVER_USE_BYTE_BUFFER_POOLING')
        string(defaultValue: '', description: 'The server\'s thread pool size. Defaults to 200', name: 'SERVER_THREAD_POOL_SIZE')
        string(defaultValue: '', description: 'The server\'s reserved threads. Defaults to -1', name: 'SERVER_RESERVED_THREADS')
        string(defaultValue: '', description: 'The HTTP protocol to use, defaults to http. You can choose from this list: http, h2c', name: 'HTTP_PROTOCOL')
        string(defaultValue: '', description: 'The JSSE provider to use, defaults to the JVM internal one. You can choose from this list: -empty string-, Conscrypt, BCJSSE', name: 'JSSE_PROVIDER')
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
                echo "before toolchains"
                toolchains (jdkToUse: "$JDK_TO_USE", nodes: "$LOADER_NAMES,$PROBE_NAME")
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
                                    // the good one sh "mvn -DskipTests -Dcheckstyle.skip=true -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -DskipTests -T7 -e" // -Dmaven.build.cache.enabled=false"
                                    sh "mvn -ntp -s $GLOBAL_MVN_SETTINGS -V -B clean install -e -DskipTests -Dmaven.build.cache.remote.url=http://10.0.0.15:8081/repository/maven-build-cache -Dmaven.build.cache.remote.enabled=true -Dmaven.build.cache.remote.save.enabled=true -Dmaven.build.cache.remote.server.id=nexus-cred"
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('jetty-profiler') {
            agent { node { label "${SERVER_NAME}" } }
            options {
                timeout(time: 120, unit: 'MINUTES')
            }
            steps {
                lock('jetty-perf') {
                    // clean the directory before clone
                    sh "rm -rf *"
                    checkout([$class           : 'GitSCM',
                              branches         : [[name: "*/$PROFILER_BRANCH"]],
                              extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                              userRemoteConfigs: [[url: 'https://github.com/jetty-project/jetty-perf.git']]])
                    withEnv(["JAVA_HOME=${tool "jdk17"}",
                             "PATH+MAVEN=${tool "jdk17"}/bin:${tool "maven3"}/bin",
                             "MAVEN_OPTS=-Xmx4G -Djava.awt.headless=true"]) {
                        configFileProvider(
                            [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
                                sh "echo mvn build"
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
