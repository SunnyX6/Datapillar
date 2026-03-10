package com.sunny.datapillar.openlineage.source;

import com.sunny.datapillar.openlineage.model.Catalog;
import com.sunny.datapillar.openlineage.model.Column;
import com.sunny.datapillar.openlineage.model.Metric;
import com.sunny.datapillar.openlineage.model.MetricVersion;
import com.sunny.datapillar.openlineage.model.Modifier;
import com.sunny.datapillar.openlineage.model.Schema;
import com.sunny.datapillar.openlineage.model.Table;
import com.sunny.datapillar.openlineage.model.Tag;
import com.sunny.datapillar.openlineage.model.TagRelation;
import com.sunny.datapillar.openlineage.model.Unit;
import com.sunny.datapillar.openlineage.model.ValueDomain;
import com.sunny.datapillar.openlineage.model.WordRoot;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Parsed current-model payload from a source adapter. */
@Getter
@Setter
public class OpenLineageSourceModels {

  private Long facetTenantId;
  private String facetTenantCode;
  private String facetTenantName;
  private final List<Catalog> catalogs = new ArrayList<>();
  private final List<Schema> schemas = new ArrayList<>();
  private final List<Table> tables = new ArrayList<>();
  private final List<Column> columns = new ArrayList<>();
  private final List<Metric> metrics = new ArrayList<>();
  private final List<MetricVersion> metricVersions = new ArrayList<>();
  private final List<Tag> tags = new ArrayList<>();
  private final List<TagRelation> tagRelations = new ArrayList<>();
  private final List<WordRoot> wordRoots = new ArrayList<>();
  private final List<Modifier> modifiers = new ArrayList<>();
  private final List<Unit> units = new ArrayList<>();
  private final List<ValueDomain> valueDomains = new ArrayList<>();
}
