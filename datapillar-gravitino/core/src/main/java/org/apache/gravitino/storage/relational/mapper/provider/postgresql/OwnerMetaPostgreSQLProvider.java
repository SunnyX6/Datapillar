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
package org.apache.gravitino.storage.relational.mapper.provider.postgresql;

import static org.apache.gravitino.storage.relational.mapper.OwnerMetaMapper.OWNER_TABLE_NAME;

import org.apache.gravitino.storage.relational.mapper.CatalogMetaMapper;
import org.apache.gravitino.storage.relational.mapper.FilesetMetaMapper;
import org.apache.gravitino.storage.relational.mapper.ModelMetaMapper;
import org.apache.gravitino.storage.relational.mapper.SchemaMetaMapper;
import org.apache.gravitino.storage.relational.mapper.TableMetaMapper;
import org.apache.gravitino.storage.relational.mapper.TopicMetaMapper;
import org.apache.gravitino.storage.relational.mapper.provider.TenantSqlSupport;
import org.apache.gravitino.storage.relational.mapper.provider.base.OwnerMetaBaseSQLProvider;
import org.apache.ibatis.annotations.Param;

public class OwnerMetaPostgreSQLProvider extends OwnerMetaBaseSQLProvider {
  @Override
  public String softDeleteOwnerRelByMetadataObjectIdAndType(
      Long metadataObjectId, String metadataObjectType) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + OWNER_TABLE_NAME
        + " SET deleted_at = floor(extract(epoch from(current_timestamp - "
        + " timestamp '1970-01-01 00:00:00')) *1000 )"
        + " WHERE metadata_object_id = #{metadataObjectId} AND metadata_object_type = #{metadataObjectType} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  @Override
  public String softDeleteOwnerRelByOwnerIdAndType(Long ownerId, String ownerType) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + OWNER_TABLE_NAME
        + " SET deleted_at = floor(extract(epoch from(current_timestamp - "
        + " timestamp '1970-01-01 00:00:00')) *1000 )"
        + " WHERE owner_id = #{ownerId} AND owner_type = #{ownerType} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  @Override
  public String softDeleteOwnerRelByMetalakeId(Long metalakeId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE  "
        + OWNER_TABLE_NAME
        + " SET deleted_at = floor(extract(epoch from(current_timestamp - "
        + " timestamp '1970-01-01 00:00:00')) *1000 )"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at =0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  @Override
  public String softDeleteOwnerRelByCatalogId(Long catalogId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE  "
        + OWNER_TABLE_NAME
        + " ot SET deleted_at = floor(extract(epoch from(current_timestamp - "
        + " timestamp '1970-01-01 00:00:00')) *1000 )"
        + " WHERE ot.deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate("ot", tenantId)
        + " AND EXISTS ("
        + " SELECT ct.catalog_id FROM "
        + CatalogMetaMapper.TABLE_NAME
        + " ct WHERE ct.catalog_id = #{catalogId} AND "
        + "ct.catalog_id = ot.metadata_object_id AND ot.metadata_object_type = 'CATALOG' AND "
        + TenantSqlSupport.tenantPredicate("ct", tenantId)
        + " UNION "
        + " SELECT st.catalog_id FROM "
        + SchemaMetaMapper.TABLE_NAME
        + " st WHERE st.catalog_id = #{catalogId} AND "
        + "st.schema_id = ot.metadata_object_id AND ot.metadata_object_type = 'SCHEMA' AND "
        + TenantSqlSupport.tenantPredicate("st", tenantId)
        + " UNION "
        + " SELECT tt.catalog_id FROM "
        + TopicMetaMapper.TABLE_NAME
        + " tt WHERE tt.catalog_id = #{catalogId} AND "
        + "tt.topic_id = ot.metadata_object_id AND ot.metadata_object_type = 'TOPIC' AND "
        + TenantSqlSupport.tenantPredicate("tt", tenantId)
        + " UNION "
        + " SELECT tat.catalog_id FROM "
        + TableMetaMapper.TABLE_NAME
        + " tat WHERE tat.catalog_id = #{catalogId} AND "
        + "tat.table_id = ot.metadata_object_id AND ot.metadata_object_type = 'TABLE' AND "
        + TenantSqlSupport.tenantPredicate("tat", tenantId)
        + " UNION "
        + " SELECT ft.catalog_id FROM "
        + FilesetMetaMapper.META_TABLE_NAME
        + " ft WHERE ft.catalog_id = #{catalogId} AND"
        + " ft.fileset_id = ot.metadata_object_id AND ot.metadata_object_type = 'FILESET' AND "
        + TenantSqlSupport.tenantPredicate("ft", tenantId)
        + " UNION "
        + " SELECT mt.catalog_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " mt WHERE mt.catalog_id = #{catalogId} AND"
        + " mt.model_id = ot.metadata_object_id AND ot.metadata_object_type = 'MODEL' AND "
        + TenantSqlSupport.tenantPredicate("mt", tenantId)
        + ")";
  }

  @Override
  public String softDeleteOwnerRelBySchemaId(Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE  "
        + OWNER_TABLE_NAME
        + " ot SET deleted_at = floor(extract(epoch from(current_timestamp - timestamp '1970-01-01 00:00:00')) * 1000) "
        + " WHERE ot.deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate("ot", tenantId)
        + " AND EXISTS ("
        + " SELECT st.schema_id FROM "
        + SchemaMetaMapper.TABLE_NAME
        + " st WHERE st.schema_id = #{schemaId} "
        + " AND st.schema_id = ot.metadata_object_id AND ot.metadata_object_type = 'SCHEMA' AND "
        + TenantSqlSupport.tenantPredicate("st", tenantId)
        + " UNION "
        + " SELECT tt.schema_id FROM "
        + TopicMetaMapper.TABLE_NAME
        + " tt WHERE tt.schema_id = #{schemaId} AND "
        + "tt.topic_id = ot.metadata_object_id AND ot.metadata_object_type = 'TOPIC' AND "
        + TenantSqlSupport.tenantPredicate("tt", tenantId)
        + " UNION "
        + " SELECT tat.schema_id FROM "
        + TableMetaMapper.TABLE_NAME
        + " tat WHERE tat.schema_id = #{schemaId} AND "
        + "tat.table_id = ot.metadata_object_id AND ot.metadata_object_type = 'TABLE' AND "
        + TenantSqlSupport.tenantPredicate("tat", tenantId)
        + " UNION "
        + " SELECT ft.schema_id FROM "
        + FilesetMetaMapper.META_TABLE_NAME
        + " ft WHERE ft.schema_id = #{schemaId} AND "
        + "ft.fileset_id = ot.metadata_object_id AND ot.metadata_object_type = 'FILESET' AND "
        + TenantSqlSupport.tenantPredicate("ft", tenantId)
        + " UNION "
        + " SELECT mt.schema_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " mt WHERE mt.schema_id = #{schemaId} AND "
        + "mt.model_id = ot.metadata_object_id AND ot.metadata_object_type = 'MODEL' AND "
        + TenantSqlSupport.tenantPredicate("mt", tenantId)
        + ")";
  }

  @Override
  public String deleteOwnerMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "DELETE FROM "
        + OWNER_TABLE_NAME
        + " WHERE id IN (SELECT id FROM "
        + OWNER_TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " LIMIT #{limit})";
  }
}
