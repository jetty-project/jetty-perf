#!/usr/bin/env groovy

def call(Map params = [:]) {

  def jdkToUse = params.containsKey("jdkToUse") ? params.branchesToNotify : '' // exception if empty?
  def nodesToUse = params.containsKey("nodes") ? params.nodes.split(",") : [''] // exception if empty?
  def failFast = true;
  
  jdkpathfinder nodes: ['load-master-2', 'load-2', 'load-5', 'load-3', 'load-6', 'load-sample'],
                jdkNames: [jdkToUse]
  stash name: 'toolchains.xml', includes: '*toolchains.xml'

  Map tasks = [failFast: failFast]
  
  for(node in nodesToUse) {
    doCreateTask(tasks, node, jdkToUse)  
  }  

  parallel(tasks)

  def doCreateTask(tasks, node, jdkToUse)
  {  
    tasks[node] = {
      node(node){
        stage('install ' + node) {
          steps {
            tool "${jdkToUse}"
            unstash name: 'toolchains.xml'
            sh "cp " + node +"-toolchains.xml ~/" + node + "-toolchains.xml"
            sh "echo " + node
          }
        }        
      }  
    }  
  }
  
}
