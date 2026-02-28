import net.ltgt.gradle.errorprone.errorprone

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

description = "rpc"

plugins {
  `maven-publish`
  id("java")
  id("idea")
  id("com.google.protobuf") version "0.9.4"
}

dependencies {
  implementation(project(":api"))
  implementation(project(":common"))
  implementation(project(":clients:client-java"))
  implementation(libs.commons.lang3)
  implementation(libs.guava)
  implementation("org.apache.dubbo:dubbo:3.3.5")
  implementation("com.google.protobuf:protobuf-java:3.25.5")

  compileOnly(libs.lombok)

  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.inline)
  testRuntimeOnly(libs.junit.jupiter.engine)
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.25.5"
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone.excludedPaths.set(".*/build/generated/source/proto/.*")
}
