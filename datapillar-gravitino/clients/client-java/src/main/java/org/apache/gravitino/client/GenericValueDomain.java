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

import java.util.List;
import org.apache.gravitino.Audit;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.authorization.SupportsRoles;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dto.dataset.ValueDomainDTO;
import org.apache.gravitino.exceptions.NoSuchTagException;
import org.apache.gravitino.tag.SupportsTags;
import org.apache.gravitino.tag.Tag;

class GenericValueDomain implements ValueDomain, SupportsTags, SupportsRoles {

  private final ValueDomainDTO valueDomainDTO;
  private final MetadataObjectTagOperations objectTagOperations;
  private final MetadataObjectRoleOperations objectRoleOperations;

  GenericValueDomain(ValueDomainDTO valueDomainDTO, RESTClient restClient, Namespace domainNs) {
    this.valueDomainDTO = valueDomainDTO;
    MetadataObject valueDomainObject =
        MetadataObjects.of(
            domainNs.level(1) + "." + domainNs.level(2),
            valueDomainDTO.domainCode(),
            MetadataObject.Type.VALUE_DOMAIN);
    this.objectTagOperations =
        new MetadataObjectTagOperations(domainNs.level(0), valueDomainObject, restClient);
    this.objectRoleOperations =
        new MetadataObjectRoleOperations(domainNs.level(0), valueDomainObject, restClient);
  }

  @Override
  public String domainCode() {
    return valueDomainDTO.domainCode();
  }

  @Override
  public String domainName() {
    return valueDomainDTO.domainName();
  }

  @Override
  public Type domainType() {
    return valueDomainDTO.domainType();
  }

  @Override
  public Level domainLevel() {
    return valueDomainDTO.domainLevel();
  }

  @Override
  public List<Item> items() {
    return valueDomainDTO.items();
  }

  @Override
  public String comment() {
    return valueDomainDTO.comment();
  }

  @Override
  public String dataType() {
    return valueDomainDTO.dataType();
  }

  @Override
  public Audit auditInfo() {
    return valueDomainDTO.auditInfo();
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
  public String[] listBindingRoleNames() {
    return objectRoleOperations.listBindingRoleNames();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GenericValueDomain)) {
      return false;
    }
    GenericValueDomain that = (GenericValueDomain) o;
    return valueDomainDTO.equals(that.valueDomainDTO);
  }

  @Override
  public int hashCode() {
    return valueDomainDTO.hashCode();
  }
}
