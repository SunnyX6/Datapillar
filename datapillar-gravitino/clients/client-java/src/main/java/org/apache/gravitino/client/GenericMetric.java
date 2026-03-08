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
package org.apache.gravitino.client;

import java.util.Map;
import org.apache.gravitino.Audit;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.authorization.SupportsRoles;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dto.dataset.MetricDTO;
import org.apache.gravitino.exceptions.NoSuchPolicyException;
import org.apache.gravitino.exceptions.NoSuchTagException;
import org.apache.gravitino.exceptions.PolicyAlreadyAssociatedException;
import org.apache.gravitino.policy.Policy;
import org.apache.gravitino.policy.SupportsPolicies;
import org.apache.gravitino.tag.SupportsTags;
import org.apache.gravitino.tag.Tag;

class GenericMetric implements Metric, SupportsTags, SupportsRoles, SupportsPolicies {

  private final MetricDTO metricDTO;
  private final MetadataObjectTagOperations objectTagOperations;
  private final MetadataObjectRoleOperations objectRoleOperations;
  private final MetadataObjectPolicyOperations objectPolicyOperations;

  GenericMetric(MetricDTO metricDTO, RESTClient restClient, Namespace metricNs) {
    this.metricDTO = metricDTO;
    MetadataObject metricObject =
        MetadataObjects.of(
            metricNs.level(1) + "." + metricNs.level(2),
            metricDTO.code(),
            MetadataObject.Type.METRIC);
    this.objectTagOperations =
        new MetadataObjectTagOperations(metricNs.level(0), metricObject, restClient);
    this.objectRoleOperations =
        new MetadataObjectRoleOperations(metricNs.level(0), metricObject, restClient);
    this.objectPolicyOperations =
        new MetadataObjectPolicyOperations(metricNs.level(0), metricObject, restClient);
  }

  @Override
  public SupportsTags supportsTags() {
    return this;
  }

  @Override
  public SupportsPolicies supportsPolicies() {
    return this;
  }

  @Override
  public SupportsRoles supportsRoles() {
    return this;
  }

  @Override
  public String name() {
    return metricDTO.name();
  }

  @Override
  public String code() {
    return metricDTO.code();
  }

  @Override
  public Type type() {
    return metricDTO.type();
  }

  @Override
  public String comment() {
    return metricDTO.comment();
  }

  @Override
  public String dataType() {
    return metricDTO.dataType();
  }

  @Override
  public String unit() {
    return metricDTO.unit();
  }

  @Override
  public String unitName() {
    return metricDTO.unitName();
  }

  @Override
  public Map<String, String> properties() {
    return metricDTO.properties();
  }

  @Override
  public int currentVersion() {
    return metricDTO.currentVersion();
  }

  @Override
  public int lastVersion() {
    return metricDTO.lastVersion();
  }

  @Override
  public Audit auditInfo() {
    return metricDTO.auditInfo();
  }

  @Override
  public String[] listTags() {
    return objectTagOperations.listTags();
  }

  @Override
  public Tag[] listTagsInfo() {
    return objectTagOperations.listTagsInfo();
  }

  @Override
  public Tag getTag(String name) throws NoSuchTagException {
    return objectTagOperations.getTag(name);
  }

  @Override
  public String[] associateTags(String[] tagsToAdd, String[] tagsToRemove) {
    return objectTagOperations.associateTags(tagsToAdd, tagsToRemove);
  }

  @Override
  public String[] listPolicies() {
    return objectPolicyOperations.listPolicies();
  }

  @Override
  public Policy[] listPolicyInfos() {
    return objectPolicyOperations.listPolicyInfos();
  }

  @Override
  public Policy getPolicy(String name) throws NoSuchPolicyException {
    return objectPolicyOperations.getPolicy(name);
  }

  @Override
  public String[] associatePolicies(String[] policiesToAdd, String[] policiesToRemove)
      throws PolicyAlreadyAssociatedException {
    return objectPolicyOperations.associatePolicies(policiesToAdd, policiesToRemove);
  }

  @Override
  public String[] listBindingRoleNames() {
    return objectRoleOperations.listBindingRoleNames();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GenericMetric)) {
      return false;
    }
    GenericMetric that = (GenericMetric) o;
    return metricDTO.equals(that.metricDTO);
  }

  @Override
  public int hashCode() {
    return metricDTO.hashCode();
  }
}
