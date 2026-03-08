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
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.dto.dataset.WordRootDTO;
import org.apache.gravitino.exceptions.NoSuchTagException;
import org.apache.gravitino.tag.SupportsTags;
import org.apache.gravitino.tag.Tag;

class GenericWordRoot implements WordRoot, SupportsTags, SupportsRoles {

  private final WordRootDTO wordRootDTO;
  private final MetadataObjectTagOperations objectTagOperations;
  private final MetadataObjectRoleOperations objectRoleOperations;

  GenericWordRoot(WordRootDTO wordRootDTO, RESTClient restClient, Namespace rootNs) {
    this.wordRootDTO = wordRootDTO;
    MetadataObject rootObject =
        MetadataObjects.of(
            rootNs.level(1) + "." + rootNs.level(2),
            wordRootDTO.code(),
            MetadataObject.Type.WORDROOT);
    this.objectTagOperations =
        new MetadataObjectTagOperations(rootNs.level(0), rootObject, restClient);
    this.objectRoleOperations =
        new MetadataObjectRoleOperations(rootNs.level(0), rootObject, restClient);
  }

  @Override
  public String code() {
    return wordRootDTO.code();
  }

  @Override
  public String name() {
    return wordRootDTO.name();
  }

  @Override
  public String dataType() {
    return wordRootDTO.dataType();
  }

  @Override
  public String comment() {
    return wordRootDTO.comment();
  }

  @Override
  public Audit auditInfo() {
    return wordRootDTO.auditInfo();
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
    if (!(o instanceof GenericWordRoot)) {
      return false;
    }
    GenericWordRoot that = (GenericWordRoot) o;
    return wordRootDTO.equals(that.wordRootDTO);
  }

  @Override
  public int hashCode() {
    return wordRootDTO.hashCode();
  }
}
