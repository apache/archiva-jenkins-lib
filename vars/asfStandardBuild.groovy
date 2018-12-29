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

def call(Map params = [:]) {
  // Faster build and reduces IO needs
  properties([
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '3'))
  ])

  // now determine the matrix of parallel builds
  def jdk = params.containsKey('jdk') ? params.jdk : 'JDK 1.8 (latest)'
  def cmdline = params.containsKey('cmdline') ? params.cmdline : 'clean install'
  def mvnName = params.containsKey('mvnName') ? params.mvnName : 'Maven 3.5.2'
  def publishers = params.containsKey('publishers') ? params.publishers : []

  mavenBuild( jdk, cmdline, mvnName, publishers)

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
  def localRepo = ".repository" // "${env.JENKINS_HOME}/${env.EXECUTOR_NUMBER}" //
  def settingsName = 'archiva-uid-jenkins'
  def mavenOpts = '-Xms1g -Xmx4g -Djava.awt.headless=true'

  withMaven(
          maven: mvnName,
          jdk: "$jdk",
          publisherStrategy: 'EXPLICIT',
          globalMavenSettingsConfig: settingsName,
          mavenOpts: mavenOpts,
          mavenLocalRepo: localRepo,
          options: publishers) {
    // Some common Maven command line + provided command line
    sh "mvn -V -B -e -Dmaven.test.failure.ignore=true $cmdline "
  }
}

// vim: et:ts=2:sw=2:ft=groovy