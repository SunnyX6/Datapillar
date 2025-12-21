 修正后的节点设计

  // 元数据（Gravitino 同步）
  (:Metalake), (:Catalog), (:Schema), (:Table), (:Column)

  // 指标（Gravitino 同步，按类型拆分）
  (:AtomicMetric)      // 原子指标
  (:DerivedMetric)     // 派生指标
  (:CompositeMetric)   // 复合指标

  // 血缘（OpenLineage 解析）
  (:SQL)

  11 种节点，AI 一眼就能理解。