"""
工作流数据结构（统一三端：AI/前端/调度）

层级关系：
- Workflow：工作流，由多个 Job 组成的 DAG
- Job：作业/任务，每个 Job 是一个独立的执行单元（前端一个节点）
- Stage：SQL 执行单元，一个 Job 可包含多个 Stage（架构师规划）

术语：
- type：Job 类型（hive/shell/datax/flink 等）
- depends：Job 之间的依赖关系
"""

from typing import List, Dict, Any, Optional, Literal
from pydantic import BaseModel, Field


class Stage(BaseModel):
    """
    Stage - SQL 执行单元

    由架构师规划，一个 Stage 对应一个 SQL，产出一个表。
    一个 Job 可包含多个 Stage，按顺序执行。
    """
    stage_id: int = Field(..., description="Stage 序号（Job 内唯一）")
    name: str = Field(..., description="Stage 名称")
    description: str = Field(..., description="这个 Stage 做什么")

    input_tables: List[str] = Field(default_factory=list, description="读取的表")
    output_table: str = Field(..., description="输出表")
    is_temp_table: bool = Field(default=True, description="是否是临时表")

    sql: Optional[str] = Field(None, description="SQL 语句（由 DeveloperAgent 生成）")


class Job(BaseModel):
    """
    作业定义（统一三端命名：Job）

    每个 Job 是一个独立的执行单元（前端一个节点）。
    一个 Job 可包含多个业务步骤（step_ids）和多个执行阶段（stages）。
    """
    # 基础信息
    id: str = Field(..., description="Job 唯一标识")
    name: str = Field(..., description="Job 名称（中文）")
    description: Optional[str] = Field(None, description="Job 描述")

    # 作业类型（统一字段：type）
    type: str = Field(..., description="Job 类型：hive/shell/datax/flink/jdbc/python")

    # 依赖关系（统一字段：depends）
    depends: List[str] = Field(default_factory=list, description="依赖的上游 Job ID 列表")

    # 关联的业务步骤（来自 AnalystAgent）
    step_ids: List[str] = Field(default_factory=list, description="包含的业务步骤 ID 列表")

    # 执行阶段（由 ArchitectAgent 规划）
    stages: List[Stage] = Field(default_factory=list, description="执行阶段列表")

    # 数据读写声明（通过共享存储传递数据）
    input_tables: List[str] = Field(default_factory=list, description="读取的表列表")
    output_table: Optional[str] = Field(None, description="写入的目标表")

    # 组件配置（由 DeveloperAgent 生成）
    config: Dict[str, Any] = Field(default_factory=dict, description="组件配置")

    # 运行时配置
    priority: int = Field(default=0, description="优先级")
    timeout: int = Field(default=3600, description="超时时间（秒）")
    retry_times: int = Field(default=3, description="失败重试次数")
    retry_interval: int = Field(default=60, description="重试间隔（秒）")

    # 状态标记
    config_generated: bool = Field(default=False, description="配置是否已生成")
    config_validated: bool = Field(default=False, description="配置是否已验证")

    def get_ordered_stages(self) -> List[Stage]:
        """按执行顺序返回 Stage"""
        return sorted(self.stages, key=lambda s: s.stage_id)


class Workflow(BaseModel):
    """
    工作流定义（统一三端命名：Workflow）

    描述完整的 ETL 工作流，由多个 Job 组成的 DAG。
    由 ArchitectAgent 根据业务分析结果规划。
    """
    # 基础信息
    id: Optional[str] = Field(None, description="工作流唯一标识")
    name: str = Field(..., description="工作流名称")
    description: Optional[str] = Field(None, description="工作流描述")

    # 调度配置
    schedule: Optional[str] = Field(None, description="调度 cron 表达式")
    env: Literal["dev", "stg", "prod"] = Field(default="dev", description="运行环境")

    # 作业列表
    jobs: List[Job] = Field(default_factory=list, description="作业列表")

    # 风险提示
    risks: List[str] = Field(default_factory=list, description="架构风险点")

    # 决策点（需要用户确认）
    decision_points: List[Dict[str, Any]] = Field(default_factory=list)

    # 置信度
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    def get_job(self, job_id: str) -> Optional[Job]:
        """获取作业"""
        for job in self.jobs:
            if job.id == job_id:
                return job
        return None

    def get_upstream_jobs(self, job_id: str) -> List[Job]:
        """获取上游作业"""
        job = self.get_job(job_id)
        if not job:
            return []
        return [j for j in self.jobs if j.id in job.depends]

    def get_downstream_jobs(self, job_id: str) -> List[Job]:
        """获取下游作业"""
        return [j for j in self.jobs if job_id in j.depends]

    def get_root_jobs(self) -> List[Job]:
        """获取根作业（无依赖的作业）"""
        return [j for j in self.jobs if not j.depends]

    def get_leaf_jobs(self) -> List[Job]:
        """获取叶子作业（无下游的作业）"""
        all_deps = set()
        for job in self.jobs:
            all_deps.update(job.depends)
        return [j for j in self.jobs if j.id not in all_deps]

    def topological_sort(self) -> List[Job]:
        """拓扑排序（按执行顺序）"""
        visited = set()
        result = []

        def dfs(job_id: str):
            if job_id in visited:
                return
            visited.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep_id in job.depends:
                    dfs(dep_id)
                result.append(job)

        for job in self.jobs:
            dfs(job.id)

        return result

    def validate_dag(self) -> List[str]:
        """验证 DAG 是否合法"""
        errors = []

        # 检查 Job ID 唯一性
        ids = [j.id for j in self.jobs]
        if len(ids) != len(set(ids)):
            errors.append("存在重复的 Job ID")

        # 检查依赖是否存在
        id_set = set(ids)
        for job in self.jobs:
            for dep in job.depends:
                if dep not in id_set:
                    errors.append(f"Job {job.id} 依赖的 {dep} 不存在")

        # 检查循环依赖
        def has_cycle(job_id: str, path: set) -> bool:
            if job_id in path:
                return True
            path.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep in job.depends:
                    if has_cycle(dep, path.copy()):
                        return True
            return False

        for job in self.jobs:
            if has_cycle(job.id, set()):
                errors.append(f"存在循环依赖，涉及 Job {job.id}")
                break

        return errors

    def validate_data_dependencies(self) -> tuple[List[str], List[str]]:
        """
        验证数据依赖是否正确声明

        检查逻辑：
        - 如果 Job B 的 input_tables 包含 Job A 的 output_table
        - 则 Job B 必须声明依赖 Job A（Job B.depends 包含 Job A.id）

        Returns:
            (errors, warnings): 错误列表和警告列表
            - errors: 缺失的关键依赖（会导致调度失败）
            - warnings: 可能的问题（需要人工确认）
        """
        errors = []
        warnings = []

        # 构建 output_table -> job_id 映射
        output_to_job: Dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # 检查每个 Job 的 input_tables
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                # 检查这个输入表是否由其他 Job 产出
                producer_job_id = output_to_job.get(input_table)
                if producer_job_id and producer_job_id != job.id:
                    # 检查是否已声明依赖
                    if producer_job_id not in job.depends:
                        errors.append(
                            f"Job '{job.id}' 读取表 '{input_table}'，"
                            f"该表由 Job '{producer_job_id}' 产出，"
                            f"但未声明依赖关系"
                        )

        return errors, warnings

    def fix_missing_dependencies(self) -> List[str]:
        """
        自动修复缺失的数据依赖

        Returns:
            修复记录列表
        """
        fixes = []

        # 构建 output_table -> job_id 映射
        output_to_job: Dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # 检查并修复每个 Job
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                producer_job_id = output_to_job.get(input_table)
                if producer_job_id and producer_job_id != job.id:
                    if producer_job_id not in job.depends:
                        job.depends.append(producer_job_id)
                        fixes.append(
                            f"自动添加依赖: Job '{job.id}' -> Job '{producer_job_id}' "
                            f"(因为读取表 '{input_table}')"
                        )

        return fixes

    def validate_temp_table_scope(self) -> List[str]:
        """
        验证临时表作用域

        规则：Stage 产出的临时表（is_temp_table=True）只能在当前 Job 内部使用，
        不能被其他 Job 引用。

        Returns:
            错误列表
        """
        errors = []

        # 收集每个 Job 内部的临时表
        job_temp_tables: Dict[str, set] = {}  # job_id -> {temp_table1, temp_table2}

        for job in self.jobs:
            temp_tables = set()
            for stage in job.stages:
                if stage.is_temp_table and stage.output_table:
                    temp_tables.add(stage.output_table)
            job_temp_tables[job.id] = temp_tables

        # 合并所有临时表
        all_temp_tables: Dict[str, str] = {}  # temp_table -> owner_job_id
        for job_id, temp_tables in job_temp_tables.items():
            for temp_table in temp_tables:
                all_temp_tables[temp_table] = job_id

        # 检查每个 Job 是否引用了其他 Job 的临时表
        for job in self.jobs:
            # 检查 Job 级别的 input_tables
            for input_table in job.input_tables:
                if input_table in all_temp_tables:
                    owner_job_id = all_temp_tables[input_table]
                    if owner_job_id != job.id:
                        errors.append(
                            f"Job '{job.id}' 引用了 Job '{owner_job_id}' 的临时表 '{input_table}'，"
                            f"临时表只能在定义它的 Job 内部使用"
                        )

            # 检查 Stage 级别的 input_tables
            for stage in job.stages:
                for input_table in stage.input_tables:
                    if input_table in all_temp_tables:
                        owner_job_id = all_temp_tables[input_table]
                        if owner_job_id != job.id:
                            errors.append(
                                f"Job '{job.id}' 的 Stage '{stage.name}' 引用了 "
                                f"Job '{owner_job_id}' 的临时表 '{input_table}'，"
                                f"临时表只能在定义它的 Job 内部使用"
                            )

        return errors


class TestCase(BaseModel):
    """测试用例（TesterAgent 生成）"""
    name: str
    description: Optional[str] = None
    test_type: Literal["positive", "boundary", "negative"] = "positive"
    node_id: Optional[str] = None
    input_data: Optional[str] = None
    expected_result: Optional[str] = None
    sql_assertion: Optional[str] = None


class TestResult(BaseModel):
    """
    测试结果（Tester Agent 输出）
    """

    passed: bool
    total_tests: int
    passed_tests: int
    failed_tests: int
    test_cases: List[TestCase] = Field(default_factory=list)
    validation_errors: List[str] = Field(default_factory=list)
    validation_warnings: List[str] = Field(default_factory=list)
    coverage_summary: Dict[str, Any] = Field(default_factory=dict)
    notes: Optional[str] = None

    def all_passed(self) -> bool:
        """是否全部测试通过"""
        return self.passed

    def has_warnings(self) -> bool:
        """是否有警告"""
        return len(self.validation_warnings) > 0
