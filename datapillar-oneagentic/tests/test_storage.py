"""
存储后端单元测试

测试模块：
- datapillar_oneagentic.storage.deliverable_stores
- datapillar_oneagentic.storage.learning_stores
- datapillar_oneagentic.storage.checkpointers
"""

import pytest

from datapillar_oneagentic.storage import (
    MemoryCheckpointer,
    InMemoryDeliverableStore,
)
from datapillar_oneagentic.storage.learning_stores import (
    VectorStore,
    VectorRecord,
    VectorSearchResult,
)


class TestMemoryCheckpointer:
    """MemoryCheckpointer 测试"""

    def test_create_checkpointer(self):
        """测试创建内存检查点存储"""
        checkpointer = MemoryCheckpointer()

        assert checkpointer is not None

    def test_get_saver(self):
        """测试获取 LangGraph saver"""
        checkpointer = MemoryCheckpointer()
        saver = checkpointer.get_saver()

        assert saver is not None

    @pytest.mark.asyncio
    async def test_close(self):
        """测试关闭（内存实现无需操作）"""
        checkpointer = MemoryCheckpointer()
        await checkpointer.close()


class TestInMemoryDeliverableStore:
    """InMemoryDeliverableStore 测试"""

    def test_create_store(self):
        """测试创建内存交付物存储"""
        store = InMemoryDeliverableStore()

        assert store is not None

    def test_get_store(self):
        """测试获取 LangGraph store"""
        store = InMemoryDeliverableStore()
        inner_store = store.get_store()

        assert inner_store is not None

    @pytest.mark.asyncio
    async def test_close(self):
        """测试关闭（内存实现无需操作）"""
        store = InMemoryDeliverableStore()
        await store.close()


class TestVectorRecord:
    """VectorRecord 测试"""

    def test_create_record(self):
        """测试创建向量记录"""
        record = VectorRecord(
            id="record_001",
            vector=[0.1, 0.2, 0.3],
            text="测试文本",
            metadata={"task_type": "etl"},
        )

        assert record.id == "record_001"
        assert record.vector == [0.1, 0.2, 0.3]
        assert record.text == "测试文本"
        assert record.metadata["task_type"] == "etl"

    def test_record_default_metadata(self):
        """测试记录默认元数据"""
        record = VectorRecord(
            id="record_001",
            vector=[0.1, 0.2, 0.3],
            text="测试文本",
        )

        assert record.metadata == {}


class TestVectorSearchResult:
    """VectorSearchResult 测试"""

    def test_create_search_result(self):
        """测试创建搜索结果"""
        result = VectorSearchResult(
            id="record_001",
            score=0.95,
            distance=0.05,
            metadata={"task_type": "etl"},
            text="测试文本",
        )

        assert result.id == "record_001"
        assert result.score == 0.95
        assert result.distance == 0.05
        assert result.text == "测试文本"

    def test_search_result_defaults(self):
        """测试搜索结果默认值"""
        result = VectorSearchResult(
            id="record_001",
            score=0.9,
            distance=None,
        )

        assert result.metadata == {}
        assert result.text == ""


class TestVectorStoreInterface:
    """VectorStore 接口测试"""

    def test_cannot_instantiate_base_class(self):
        """测试无法实例化基类"""
        with pytest.raises(TypeError):
            VectorStore()

    def test_custom_implementation(self):
        """测试自定义实现"""

        class MockVectorStore(VectorStore):
            def __init__(self):
                self._records: dict[str, VectorRecord] = {}

            async def initialize(self) -> None:
                pass

            async def close(self) -> None:
                pass

            async def add(self, record: VectorRecord) -> str:
                self._records[record.id] = record
                return record.id

            async def add_batch(self, records: list[VectorRecord]) -> list[str]:
                return [await self.add(r) for r in records]

            async def update(self, record: VectorRecord) -> bool:
                if record.id in self._records:
                    self._records[record.id] = record
                    return True
                return False

            async def delete(self, record_id: str) -> bool:
                if record_id in self._records:
                    del self._records[record_id]
                    return True
                return False

            async def get(self, record_id: str) -> VectorRecord | None:
                return self._records.get(record_id)

            async def search_by_vector(
                self,
                vector: list[float],
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def search_by_text(
                self,
                query: str,
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def count(self, filter: dict = None) -> int:
                return len(self._records)

            async def distinct(self, field: str) -> list:
                return []

        store = MockVectorStore()
        assert store is not None

    @pytest.mark.asyncio
    async def test_mock_store_operations(self):
        """测试 Mock 存储操作"""

        class MockVectorStore(VectorStore):
            def __init__(self):
                self._records: dict[str, VectorRecord] = {}

            async def initialize(self) -> None:
                pass

            async def close(self) -> None:
                pass

            async def add(self, record: VectorRecord) -> str:
                self._records[record.id] = record
                return record.id

            async def add_batch(self, records: list[VectorRecord]) -> list[str]:
                return [await self.add(r) for r in records]

            async def update(self, record: VectorRecord) -> bool:
                if record.id in self._records:
                    self._records[record.id] = record
                    return True
                return False

            async def delete(self, record_id: str) -> bool:
                if record_id in self._records:
                    del self._records[record_id]
                    return True
                return False

            async def get(self, record_id: str) -> VectorRecord | None:
                return self._records.get(record_id)

            async def search_by_vector(
                self,
                vector: list[float],
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def search_by_text(
                self,
                query: str,
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def count(self, filter: dict = None) -> int:
                return len(self._records)

            async def distinct(self, field: str) -> list:
                return []

        store = MockVectorStore()
        await store.initialize()

        record = VectorRecord(
            id="test_001",
            vector=[0.1, 0.2, 0.3],
            text="测试",
        )

        record_id = await store.add(record)
        assert record_id == "test_001"
        assert await store.count() == 1

        retrieved = await store.get("test_001")
        assert retrieved is not None
        assert retrieved.text == "测试"

        record.text = "更新后"
        success = await store.update(record)
        assert success is True

        retrieved = await store.get("test_001")
        assert retrieved.text == "更新后"

        success = await store.delete("test_001")
        assert success is True
        assert await store.count() == 0

        await store.close()

    @pytest.mark.asyncio
    async def test_mock_store_batch_add(self):
        """测试批量添加"""

        class MockVectorStore(VectorStore):
            def __init__(self):
                self._records: dict[str, VectorRecord] = {}

            async def initialize(self) -> None:
                pass

            async def close(self) -> None:
                pass

            async def add(self, record: VectorRecord) -> str:
                self._records[record.id] = record
                return record.id

            async def add_batch(self, records: list[VectorRecord]) -> list[str]:
                return [await self.add(r) for r in records]

            async def update(self, record: VectorRecord) -> bool:
                return False

            async def delete(self, record_id: str) -> bool:
                return False

            async def get(self, record_id: str) -> VectorRecord | None:
                return self._records.get(record_id)

            async def search_by_vector(
                self,
                vector: list[float],
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def search_by_text(
                self,
                query: str,
                k: int = 5,
                filter: dict = None,
            ) -> list[VectorSearchResult]:
                return []

            async def count(self, filter: dict = None) -> int:
                return len(self._records)

            async def distinct(self, field: str) -> list:
                return []

        store = MockVectorStore()

        records = [
            VectorRecord(id=f"batch_{i}", vector=[0.1 * i], text=f"记录{i}")
            for i in range(5)
        ]

        ids = await store.add_batch(records)

        assert len(ids) == 5
        assert await store.count() == 5
