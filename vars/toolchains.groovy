#!/usr/bin/env groovy

def call(Map params = [:]) {

  def jdkToUse = params.containsKey("jdkToUse") ? params.jdkToUse : '' // exception if empty?
  def nodesToUse = params.containsKey("nodes") ? params.nodes.split(",") : [''] // exception if empty?
  def failFast = true;
  
  jdkpathfinder nodes: ['load-master-2', 'load-2', 'load-5', 'load-3', 'load-6', 'load-sample'],
                jdkNames: [jdkToUse]
  stash name: 'toolchains.xml', includes: '*toolchains.xml'

  echo "stash done"
  
  def buildStages = [:]
  
  for(node in nodesToUse) {
    buildStages.put(node,doCreateTask(node, jdkToUse))
  }  

  parallel(buildStages)  
}

def doCreateTask(node, jdkToUse)
{  
  echo "doCreateTask ${node}, ${jdkToUse}"
  /*return {
    echo "running for ${node}, ${jdkToUse}"  
  }*/  
  return {
    //node("${node}"){
      stage("install ${node}") {
        
        echo "running for ${node}, ${jdkToUse}"  
        //steps {
          //tool "${jdkToUse}"
          //unstash name: "toolchains.xml"
          //sh "cp ${node}-toolchains.xml ~/${node}-toolchains.xml"
          //sh "echo ${node}"
        //}
      }        
    //}  
  }
}
