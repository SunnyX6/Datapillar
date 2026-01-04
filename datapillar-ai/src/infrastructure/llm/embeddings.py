"""
Embedding 集成层
支持 GLM、OpenAI、DeepSeek 等多种 Embedding 模型
"""

import logging
from functools import lru_cache

logger = logging.getLogger(__name__)

from neo4j_graphrag.embeddings.base import Embedder

from src.infrastructure.llm.model_manager import model_manager


# 模块级缓存：text -> embedding（最多缓存 500 个）
@lru_cache(maxsize=500)
def _cached_embed(
    provider: str, api_key: str, base_url: str, model_name: str, text: str
) -> tuple[float, ...]:
    """缓存的 embedding 调用（返回 tuple 以支持 lru_cache）"""
    if provider == "glm":
        from zai import ZhipuAiClient

        client = ZhipuAiClient(api_key=api_key, base_url=base_url)
        response = client.embeddings.create(model=model_name, input=text)
        if hasattr(response, "data") and len(response.data) > 0:
            return tuple(response.data[0].embedding)
        elif isinstance(response, dict) and "data" in response:
            return tuple(response["data"][0]["embedding"])
        else:
            raise ValueError(f"无法从 Embedding 响应中提取向量: {response}")

    elif provider in ["openai", "deepseek"]:
        from openai import OpenAI

        client = OpenAI(api_key=api_key, base_url=base_url)
        response = client.embeddings.create(model=model_name, input=text)
        return tuple(response.data[0].embedding)

    else:
        raise ValueError(f"不支持的 Embedding 模型提供商: {provider}")


class UnifiedEmbedder(Embedder):
    """
    统一 Embedder（实现 neo4j-graphrag 的 Embedder 接口）

    单例模式：无论调用多少次 UnifiedEmbedder()，都返回同一个实例
    """

    _instance = None
    _initialized = False

    # 不同模型的默认批量大小
    DEFAULT_BATCH_SIZES = {
        "glm": 20,
        "openai": 100,
        "deepseek": 50,
    }

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        """从 model_manager 读取默认 embedding 模型配置（只初始化一次）"""
        if self._initialized:
            return

        model = model_manager.default_embed_model()
        if not model:
            raise ValueError("未找到默认 Embedding 模型配置，请在 MySQL ai_model 表中配置")

        self.provider = model.provider.lower()
        self.api_key = model.api_key
        self.base_url = model.base_url
        self.model_name = model.model_name
        self.batch_size = self.DEFAULT_BATCH_SIZES.get(self.provider, 20)

        UnifiedEmbedder._initialized = True
        logger.info(
            f"UnifiedEmbedder 初始化完成: {self.provider}/{self.model_name}, batch_size={self.batch_size}"
        )

    def embed_query(self, text: str) -> list[float]:
        """
        生成单个查询的向量嵌入（带 LRU 缓存）

        Args:
            text: 要转换的文本

        Returns:
            向量嵌入列表
        """
        # 使用模块级缓存函数
        result = _cached_embed(self.provider, self.api_key, self.base_url, self.model_name, text)
        return list(result)

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """
        批量生成向量嵌入

        Args:
            texts: 文本列表

        Returns:
            向量嵌入列表的列表，顺序与输入一致
        """
        if not texts:
            return []

        if self.provider == "glm":
            from zai import ZhipuAiClient

            client = ZhipuAiClient(api_key=self.api_key, base_url=self.base_url)
            response = client.embeddings.create(model=self.model_name, input=texts)
            if hasattr(response, "data") and len(response.data) > 0:
                sorted_data = sorted(response.data, key=lambda x: int(x.index or 0))
                return [item.embedding for item in sorted_data]
            elif isinstance(response, dict) and "data" in response:
                sorted_data = sorted(response["data"], key=lambda x: int(x.get("index") or 0))
                return [item["embedding"] for item in sorted_data]
            else:
                raise ValueError(f"无法从 Embedding 响应中提取向量: {response}")

        elif self.provider in ["openai", "deepseek"]:
            from openai import OpenAI

            client = OpenAI(api_key=self.api_key, base_url=self.base_url)
            response = client.embeddings.create(model=self.model_name, input=texts)
            sorted_data = sorted(response.data, key=lambda x: int(x.index or 0))
            return [item.embedding for item in sorted_data]

        else:
            raise ValueError(f"不支持的 Embedding 模型提供商: {self.provider}")
