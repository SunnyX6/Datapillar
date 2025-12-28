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

import java.util.List;
import org.apache.gravitino.storage.relational.po.ValueDomainPO;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

/** ValueDomain 元数据 Mapper */
public interface ValueDomainMetaMapper {

  String TABLE_NAME = "value_domain_meta";

  @InsertProvider(type = ValueDomainMetaSQLProviderFactory.class, method = "insertValueDomainMeta")
  void insertValueDomainMeta(@Param("domain") ValueDomainPO domainPO);

  @InsertProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "insertValueDomainMetaOnDuplicateKeyUpdate")
  void insertValueDomainMetaOnDuplicateKeyUpdate(@Param("domain") ValueDomainPO domainPO);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "listValueDomainPOsBySchemaId")
  List<ValueDomainPO> listValueDomainPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "listValueDomainPOsBySchemaIdWithPagination")
  List<ValueDomainPO> listValueDomainPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "countValueDomainsBySchemaId")
  Long countValueDomainsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "selectValueDomainMetaBySchemaIdAndDomainCode")
  ValueDomainPO selectValueDomainMetaBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "selectValueDomainMetaByDomainId")
  ValueDomainPO selectValueDomainMetaByDomainId(@Param("domainId") Long domainId);

  @SelectProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "listValueDomainPOsByDomainIds")
  List<ValueDomainPO> listValueDomainPOsByDomainIds(@Param("domainIds") List<Long> domainIds);

  @UpdateProvider(type = ValueDomainMetaSQLProviderFactory.class, method = "updateValueDomainMeta")
  Integer updateValueDomainMeta(
      @Param("newDomain") ValueDomainPO newDomainPO, @Param("oldDomain") ValueDomainPO oldDomainPO);

  @UpdateProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "softDeleteValueDomainMetaBySchemaIdAndDomainCode")
  Integer softDeleteValueDomainMetaBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode);

  @UpdateProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "softDeleteValueDomainMetasBySchemaId")
  Integer softDeleteValueDomainMetasBySchemaId(@Param("schemaId") Long schemaId);

  @UpdateProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "softDeleteValueDomainMetasByCatalogId")
  Integer softDeleteValueDomainMetasByCatalogId(@Param("catalogId") Long catalogId);

  @UpdateProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "softDeleteValueDomainMetasByMetalakeId")
  Integer softDeleteValueDomainMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @DeleteProvider(
      type = ValueDomainMetaSQLProviderFactory.class,
      method = "deleteValueDomainMetasByLegacyTimeline")
  Integer deleteValueDomainMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);
}
