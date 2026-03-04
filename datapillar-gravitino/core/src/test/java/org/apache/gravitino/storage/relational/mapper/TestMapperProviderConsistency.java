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
package org.apache.gravitino.storage.relational.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.gravitino.storage.relational.mapper.provider.DefaultMapperPackageProvider;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.junit.jupiter.api.Test;

public class TestMapperProviderConsistency {

  @Test
  void testMapperProviderMethodsExist() {
    List<String> missingProviderMethods = new ArrayList<>();
    List<Class<?>> mapperClasses = new DefaultMapperPackageProvider().getMapperClasses();

    for (Class<?> mapperClass : mapperClasses) {
      for (Method mapperMethod : mapperClass.getDeclaredMethods()) {
        SelectProvider selectProvider = mapperMethod.getAnnotation(SelectProvider.class);
        if (selectProvider != null) {
          verifyProviderMethod(
              missingProviderMethods,
              mapperClass,
              mapperMethod,
              selectProvider.type(),
              selectProvider.method());
        }

        InsertProvider insertProvider = mapperMethod.getAnnotation(InsertProvider.class);
        if (insertProvider != null) {
          verifyProviderMethod(
              missingProviderMethods,
              mapperClass,
              mapperMethod,
              insertProvider.type(),
              insertProvider.method());
        }

        UpdateProvider updateProvider = mapperMethod.getAnnotation(UpdateProvider.class);
        if (updateProvider != null) {
          verifyProviderMethod(
              missingProviderMethods,
              mapperClass,
              mapperMethod,
              updateProvider.type(),
              updateProvider.method());
        }

        DeleteProvider deleteProvider = mapperMethod.getAnnotation(DeleteProvider.class);
        if (deleteProvider != null) {
          verifyProviderMethod(
              missingProviderMethods,
              mapperClass,
              mapperMethod,
              deleteProvider.type(),
              deleteProvider.method());
        }
      }
    }

    assertTrue(
        missingProviderMethods.isEmpty(),
        () -> "Missing provider methods were found:\n" + String.join("\n", missingProviderMethods));
  }

  private static void verifyProviderMethod(
      List<String> missingProviderMethods,
      Class<?> mapperClass,
      Method mapperMethod,
      Class<?> providerClass,
      String providerMethodName) {
    boolean methodExists =
        Arrays.stream(providerClass.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals(providerMethodName));
    if (!methodExists) {
      missingProviderMethods.add(
          String.format(
              "%s#%s -> %s.%s",
              mapperClass.getName(),
              mapperMethod.getName(),
              providerClass.getName(),
              providerMethodName));
    }
  }
}
