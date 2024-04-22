#!/usr/bin/env groovy

def call(Map params = [:]) {

  def jdkToUse = params.containsKey("jdkToUse") ? params.branchesToNotify : '' // exception if empty?

  jdkpathfinder nodes: ['load-master-2', 'load-2', 'load-5', 'load-3', 'load-6', 'load-sample'],
                jdkNames: ["${JDK_TO_USE}"]
  stash name: 'toolchains.xml', includes: '*toolchains.xml'
  
}
