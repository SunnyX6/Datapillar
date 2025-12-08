# 创建全局统一的向量索引
"""
// 名字就叫 warehouse_knowledge_vector
CREATE VECTOR INDEX warehouse_knowledge_vector
// 关键点：用 | 分隔所有 Label，把它们一网打尽
FOR (n:Table | Column | Metric | Job | User)
ON (n.embedding) 
OPTIONS {indexConfig: {
 `vector.dimensions`: 1536, // 对应 OpenAI
 `vector.similarity_function`: 'cosine'
}}
"""
# 创建全局统一的全文索引
"""
// 名字就叫 warehouse_knowledge_fulltext
CREATE FULLTEXT INDEX warehouse_knowledge_fulltext
// 同样，囊括所有 Label
FOR (n:Table | Column | Metric | Job | User)
// 关键点：囊括所有可能包含文本信息的字段
ON EACH [n.name, n.description, n.comment, n.sql_logic, n.business_definition]
"""

# 初始化一个"全知全能"检索器
unified_retriever = HybridRetriever(
    driver=driver,
    vector_index_name="warehouse_knowledge_vector",   # 大一统向量
    fulltext_index_name="warehouse_knowledge_fulltext", # 大一统全文
    embedder=embedder,
    # 返回结果时，把实体的类型(Label)带上，方便前端渲染或Agent判断
    return_properties=["name", "description", "sql_logic"], 
)