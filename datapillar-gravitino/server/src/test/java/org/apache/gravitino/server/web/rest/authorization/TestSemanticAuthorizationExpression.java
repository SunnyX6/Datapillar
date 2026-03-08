/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.server.web.rest.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Method;
import ognl.OgnlException;
import org.apache.gravitino.dto.requests.MetricModifierCreateRequest;
import org.apache.gravitino.dto.requests.MetricModifierUpdateRequest;
import org.apache.gravitino.dto.requests.MetricRegisterRequest;
import org.apache.gravitino.dto.requests.MetricUpdatesRequest;
import org.apache.gravitino.dto.requests.UnitCreateRequest;
import org.apache.gravitino.dto.requests.UnitUpdateRequest;
import org.apache.gravitino.dto.requests.ValueDomainCreateRequest;
import org.apache.gravitino.dto.requests.ValueDomainUpdateRequest;
import org.apache.gravitino.dto.requests.WordRootCreateRequest;
import org.apache.gravitino.dto.requests.WordRootUpdateRequest;
import org.apache.gravitino.server.authorization.annotations.AuthorizationExpression;
import org.apache.gravitino.server.web.rest.MetricOperations;
import org.apache.gravitino.server.web.rest.UnitOperations;
import org.apache.gravitino.server.web.rest.ValueDomainOperations;
import org.apache.gravitino.server.web.rest.WordRootOperations;
import org.junit.jupiter.api.Test;

public class TestSemanticAuthorizationExpression {

  @Test
  public void testMetricAuthorizationExpressions() throws NoSuchMethodException, OgnlException {
    MockAuthorizationExpressionEvaluator createEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "registerMetric",
                String.class,
                String.class,
                String.class,
                MetricRegisterRequest.class));
    assertFalse(createEvaluator.getResult(ImmutableSet.of()));
    assertFalse(
        createEvaluator.getResult(ImmutableSet.of("SCHEMA::CREATE_METRIC", "SCHEMA::USE_SCHEMA")));
    assertTrue(
        createEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::CREATE_METRIC", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator loadEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "getMetric", String.class, String.class, String.class, String.class));
    assertFalse(loadEvaluator.getResult(ImmutableSet.of()));
    assertFalse(
        loadEvaluator.getResult(ImmutableSet.of("SCHEMA::USE_METRIC", "SCHEMA::USE_SCHEMA")));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_METRIC", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of("METRIC::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator manageEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "alterMetric",
                String.class,
                String.class,
                String.class,
                String.class,
                MetricUpdatesRequest.class));
    assertFalse(
        manageEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_METRIC", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        manageEvaluator.getResult(
            ImmutableSet.of("METRIC::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
  }

  @Test
  public void testModifierAuthorizationExpressions() throws NoSuchMethodException, OgnlException {
    MockAuthorizationExpressionEvaluator createEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "createModifier",
                String.class,
                String.class,
                String.class,
                MetricModifierCreateRequest.class));
    assertTrue(
        createEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::CREATE_MODIFIER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator loadEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "getModifier", String.class, String.class, String.class, String.class));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_MODIFIER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator manageEvaluator =
        evaluator(
            MetricOperations.class.getMethod(
                "alterModifier",
                String.class,
                String.class,
                String.class,
                String.class,
                MetricModifierUpdateRequest.class));
    assertFalse(
        manageEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_MODIFIER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        manageEvaluator.getResult(
            ImmutableSet.of("MODIFIER::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
  }

  @Test
  public void testWordRootAuthorizationExpressions() throws NoSuchMethodException, OgnlException {
    MockAuthorizationExpressionEvaluator createEvaluator =
        evaluator(
            WordRootOperations.class.getMethod(
                "createWordRoot",
                String.class,
                String.class,
                String.class,
                WordRootCreateRequest.class));
    assertTrue(
        createEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::CREATE_WORDROOT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator loadEvaluator =
        evaluator(
            WordRootOperations.class.getMethod(
                "getWordRoot", String.class, String.class, String.class, String.class));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_WORDROOT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator manageEvaluator =
        evaluator(
            WordRootOperations.class.getMethod(
                "alterWordRoot",
                String.class,
                String.class,
                String.class,
                String.class,
                WordRootUpdateRequest.class));
    assertFalse(
        manageEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_WORDROOT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        manageEvaluator.getResult(
            ImmutableSet.of("WORDROOT::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
  }

  @Test
  public void testUnitAuthorizationExpressions() throws NoSuchMethodException, OgnlException {
    MockAuthorizationExpressionEvaluator createEvaluator =
        evaluator(
            UnitOperations.class.getMethod(
                "createUnit", String.class, String.class, String.class, UnitCreateRequest.class));
    assertTrue(
        createEvaluator.getResult(
            ImmutableSet.of("SCHEMA::CREATE_UNIT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator loadEvaluator =
        evaluator(
            UnitOperations.class.getMethod(
                "getUnit", String.class, String.class, String.class, String.class));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_UNIT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator manageEvaluator =
        evaluator(
            UnitOperations.class.getMethod(
                "alterUnit",
                String.class,
                String.class,
                String.class,
                String.class,
                UnitUpdateRequest.class));
    assertFalse(
        manageEvaluator.getResult(
            ImmutableSet.of("SCHEMA::USE_UNIT", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        manageEvaluator.getResult(
            ImmutableSet.of("UNIT::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
  }

  @Test
  public void testValueDomainAuthorizationExpressions()
      throws NoSuchMethodException, OgnlException {
    MockAuthorizationExpressionEvaluator createEvaluator =
        evaluator(
            ValueDomainOperations.class.getMethod(
                "createValueDomain",
                String.class,
                String.class,
                String.class,
                ValueDomainCreateRequest.class));
    assertTrue(
        createEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::CREATE_VALUE_DOMAIN", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator loadEvaluator =
        evaluator(
            ValueDomainOperations.class.getMethod(
                "getValueDomain", String.class, String.class, String.class, String.class));
    assertTrue(
        loadEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::USE_VALUE_DOMAIN", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));

    MockAuthorizationExpressionEvaluator manageEvaluator =
        evaluator(
            ValueDomainOperations.class.getMethod(
                "alterValueDomain",
                String.class,
                String.class,
                String.class,
                String.class,
                ValueDomainUpdateRequest.class));
    assertFalse(
        manageEvaluator.getResult(
            ImmutableSet.of(
                "SCHEMA::USE_VALUE_DOMAIN", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
    assertTrue(
        manageEvaluator.getResult(
            ImmutableSet.of("VALUE_DOMAIN::OWNER", "SCHEMA::USE_SCHEMA", "CATALOG::USE_CATALOG")));
  }

  private MockAuthorizationExpressionEvaluator evaluator(Method method) {
    AuthorizationExpression authorizationExpression =
        method.getAnnotation(AuthorizationExpression.class);
    return new MockAuthorizationExpressionEvaluator(authorizationExpression.expression());
  }
}
