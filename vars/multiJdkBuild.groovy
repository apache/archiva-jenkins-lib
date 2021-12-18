#!groovy

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Library for running jenkins pipeline builds with multiple JDKs. 
 * You can add the library to your Jenkins configuration and put the following line
 * to the Jenkinsfile:
 * <code>asfStandardBuild()</code>
 *
 * If you have want to use it in your local jenkins installation you can add the 
 * environment variable: <code>NONAPACHEORG_RUN=y</code> to your central jenkins
 * configuration. That means that not deploy task is executed, only install.
 */


def call(Map params = [:]) {
  // Faster build and reduces IO needs
  properties([
    disableConcurrentBuilds(),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '3'))
  ])

  // now determine params
  def jdk = params.containsKey('jdk') ? params.jdk : 'jdk_1.8_latest'
  def jdk11 = params.containsKey('jdk11') ? params.jdk : 'jdk_11_latest'
  def upstreamTriggers = params.containsKey('upstreamTriggers')?params.upstreamTriggers:''
  // use the cmdLine parameter otherwise default depending on current branch
  def cmdline = params.containsKey('cmdline') ? params.cmdline : ((env.NONAPACHEORG_RUN != 'y' && env.BRANCH_NAME == 'master') ?"clean deploy":"clean install")
  def cmdlineJdk11 = params.containsKey('cmdlineJdk11') ? params.cmdlineJdk11 : "clean install"
  def mvnName = params.containsKey('mvnName') ? params.mvnName : 'maven_3.6.3'


  def defaultPublishers = [artifactsPublisher(disabled: false), junitPublisher(ignoreAttachments: false, disabled: false),
                          findbugsPublisher(disabled: true), openTasksPublisher(disabled: true),
                           dependenciesFingerprintPublisher(disabled: false), invokerPublisher(disabled: true),
                            pipelineGraphPublisher(disabled: false), mavenLinkerPublisher(disabled: false)]

  def defaultPublishersJdk = [artifactsPublisher(disabled: true), junitPublisher(ignoreAttachments: false, disabled: false),
                           findbugsPublisher(disabled: true), openTasksPublisher(disabled: true),
                           dependenciesFingerprintPublisher(disabled: true), invokerPublisher(disabled: true),
                           pipelineGraphPublisher(disabled: true), mavenLinkerPublisher(disabled: true)]

  def publishers = params.containsKey('publishers') ? params.publishers : defaultPublishers


  pipeline {
    agent any
    triggers { 
        upstream(upstreamProjects: upstreamTriggers, threshold: hudson.model.Result.SUCCESS) 
    }

    stages{
      stage("Build JDK8") {
        agent { node { label 'ubuntu' } }
        options { timeout(time: 120, unit: 'MINUTES') }
        steps {
          mavenBuild(jdk, cmdline, mvnName, publishers)
        }
      }
      stage("Build JDK11") {
        agent { node { label 'ubuntu' } }
        options { timeout(time: 120, unit: 'MINUTES') }
        steps {
          mavenBuild(jdk11, cmdlineJdk11, mvnName, publishers)
        }
      }
    }
    post {
      always {
        cleanWs() // deleteDirs: true, notFailBuild: true, patterns: [[pattern: '**/.repository/**', type: 'INCLUDE']]
      }
      unstable {
        script{
          notifyBuild( "Unstable Build ")
        }
      }
      failure {
        script{
          notifyBuild( "Error in build ")
        }
      }
      success {
        script {
          def previousResult = currentBuild.previousBuild?.result
          if (previousResult && !currentBuild.resultIsWorseOrEqualTo( previousResult ) ) {
            notifyBuild( "Fixed" )
          }
        }
      }
    }
  }
}

/**
 * To other developers, if you are using this method above, please use the following syntax.
 *
 * mavenBuild("<jdk>", "<profiles> <goals> <plugins> <properties>"
 *
 * @param jdk the jdk tool name (in jenkins) to use for this build
 * @param cmdline the command line in "<profiles> <goals> <properties>"`format.
 * @paran mvnName maven installation to use
 * @param publishers array of publishers to configure (need to be defined as we publisherStrategy: 'EXPLICIT')
 * @return the Jenkinsfile step representing a maven build
 */
def mavenBuild(jdk, cmdline, mvnName, publishers) {
  def localRepo = "../.maven_repositories/${env.EXECUTOR_NUMBER}" // ".repository" //
  def settingsName = 'archiva-uid-jenkins'
  def mavenOpts = '-Xms1g -Xmx4g -Djava.awt.headless=true'

  withMaven(
          maven: mvnName,
          jdk: "$jdk",
          options: publishers,
          publisherStrategy: 'EXPLICIT',
          //globalMavenSettingsConfig: settingsName,
          mavenOpts: mavenOpts,
          mavenLocalRepo: localRepo) {
    // Some common Maven command line + provided command line
    sh "mvn -V -B -U -e -Dmaven.test.failure.ignore=true $cmdline "
  }
}

def notifyBuild(String buildStatus) {
  // default the value
  buildStatus = buildStatus ?: "UNKNOWN"

  def email = "notifications@archiva.apache.org"
  def summary = "${env.JOB_NAME}#${env.BUILD_NUMBER} - ${buildStatus} - ${currentBuild?.currentResult}"
  def detail = """<h4>Job: <a href='${env.JOB_URL}'>${env.JOB_NAME}</a> [#${env.BUILD_NUMBER}]</h4>
  <p><b>${buildStatus}</b></p>
  <table>
    <tr><td>Build</td><td><a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></td><tr>
    <tr><td>Console</td><td><a href='${env.BUILD_URL}console'>${env.BUILD_URL}console</a></td><tr>
    <tr><td>Test Report</td><td><a href='${env.BUILD_URL}testReport/'>${env.BUILD_URL}testReport/</a></td><tr>
  </table>
  """

  emailext(
          to: email,
          subject: summary,
          body: detail,
          mimeType: 'text/html'
  )
}

// vim: et:ts=2:sw=2:ft=groovy
