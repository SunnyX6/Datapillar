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

import org.apache.gravitino.Audit;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.authorization.SupportsRoles;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dto.dataset.UnitDTO;
import org.apache.gravitino.exceptions.NoSuchTagException;
import org.apache.gravitino.tag.SupportsTags;
import org.apache.gravitino.tag.Tag;

class GenericUnit implements Unit, SupportsTags, SupportsRoles {

  private final UnitDTO unitDTO;
  private final MetadataObjectTagOperations objectTagOperations;
  private final MetadataObjectRoleOperations objectRoleOperations;

  GenericUnit(UnitDTO unitDTO, RESTClient restClient, Namespace unitNs) {
    this.unitDTO = unitDTO;
    MetadataObject unitObject =
        MetadataObjects.of(
            unitNs.level(1) + "." + unitNs.level(2), unitDTO.code(), MetadataObject.Type.UNIT);
    this.objectTagOperations =
        new MetadataObjectTagOperations(unitNs.level(0), unitObject, restClient);
    this.objectRoleOperations =
        new MetadataObjectRoleOperations(unitNs.level(0), unitObject, restClient);
  }

  @Override
  public String code() {
    return unitDTO.code();
  }

  @Override
  public String name() {
    return unitDTO.name();
  }

  @Override
  public String symbol() {
    return unitDTO.symbol();
  }

  @Override
  public String comment() {
    return unitDTO.comment();
  }

  @Override
  public Audit auditInfo() {
    return unitDTO.auditInfo();
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
    if (!(o instanceof GenericUnit)) {
      return false;
    }
    GenericUnit that = (GenericUnit) o;
    return unitDTO.equals(that.unitDTO);
  }

  @Override
  public int hashCode() {
    return unitDTO.hashCode();
  }
}
