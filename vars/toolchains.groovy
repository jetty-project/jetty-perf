#!/usr/bin/env groovy

def call(Map params = [:]) {

  def jdkToUse = params.containsKey("jdkToUse") ? params.jdkToUse : '' // exception if empty?
  def nodesToUse = params.containsKey("nodes") ? params.nodes.split(",") : [''] // exception if empty?
  def failFast = true;
  
  jdkpathfinder nodes: nodesToUse,
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
  return {
    stage("install ${node}") {
      
        echo "running for ${node}, ${jdkToUse}"  
        tool "${jdkToUse}"
        unstash name: "toolchains.xml"
        //sh "ls -lrt"
        sh "cp ${node}-toolchains.xml ~/${node}-toolchains.xml"  
    }         
  }
}
