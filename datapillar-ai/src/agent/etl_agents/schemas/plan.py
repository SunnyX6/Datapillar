"""
工作流数据结构（统一三端：AI/前端/调度）

统一术语：
- Workflow：工作流，由多个 Job 组成的 DAG
- Job：作业/任务，每个 Job 是一个独立的执行单元
- depends：Job 之间的依赖关系（直接在 Job 内部定义）
- type：Job 类型（hive/shell/datax/flink 等）
"""

from typing import List, Dict, Any, Optional, Literal
from pydantic import BaseModel, Field


class Job(BaseModel):
    """
    作业定义（统一三端命名：Job）

    每个 Job 是一个独立的执行单元，由调度系统执行。
    Job 之间通过 depends 字段定义依赖关系。
    """
    # 基础信息
    id: str = Field(..., description="Job 唯一标识")
    name: str = Field(..., description="Job 名称（中文）")
    description: Optional[str] = Field(None, description="Job 描述")

    # 作业类型（统一字段：type）
    type: str = Field(..., description="Job 类型：hive/shell/datax/flink/jdbc/python")

    # 依赖关系（统一字段：depends）
    depends: List[str] = Field(default_factory=list, description="依赖的上游 Job ID 列表")

    # 数据读写声明（通过共享存储传递数据）
    input_tables: List[str] = Field(default_factory=list, description="读取的表列表，如 ['ods.ods_order', 'ods.ods_user']")
    output_table: Optional[str] = Field(None, description="写入的目标表，如 'dwd.dwd_order_detail'")

    # 组件配置（按 config_schema 填充，由 DeveloperAgent 生成）
    config: Dict[str, Any] = Field(default_factory=dict, description="组件配置，结构由 component.config_schema 定义")

    # 运行时配置
    priority: int = Field(default=0, description="优先级，数值越大优先级越高")
    timeout: int = Field(default=3600, description="超时时间（秒）")
    retry_times: int = Field(default=3, description="失败重试次数")
    retry_interval: int = Field(default=60, description="重试间隔（秒）")

    # 状态标记
    config_generated: bool = Field(default=False, description="配置是否已生成")
    config_validated: bool = Field(default=False, description="配置是否已验证")


class Workflow(BaseModel):
    """
    工作流定义（统一三端命名：Workflow）

    描述完整的 ETL 工作流设计，由多个 Job 组成的 DAG。
    """
    # 基础信息
    id: Optional[str] = Field(None, description="工作流唯一标识")
    name: str = Field(..., description="工作流名称")
    description: Optional[str] = Field(None, description="工作流描述")

    # 调度配置
    schedule: Optional[str] = Field(None, description="调度 cron 表达式，如 '0 2 * * *'")
    env: Literal["dev", "stg", "prod"] = Field(default="dev", description="运行环境")

    # 作业列表（统一字段：jobs）
    jobs: List[Job] = Field(default_factory=list, description="作业列表")

    # 数据分层（用于参考）
    layers: List[Literal["SRC", "ODS", "DWD", "DWS", "ADS"]] = Field(default_factory=list)

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


class ReviewIssue(BaseModel):
    """评审问题（与 ReviewerAgent 提示词保持一致）"""
    severity: Literal["critical", "high", "medium", "low"]
    category: Literal["completeness", "correctness", "performance", "security", "best_practice"]
    description: str
    suggestion: Optional[str] = None
    affected_nodes: List[str] = Field(default_factory=list)


class ReviewResult(BaseModel):
    """评审结果（Reviewer Agent 输出）"""

    approved: bool
    issues: List[ReviewIssue] = Field(default_factory=list)
    improvements: List[str] = Field(default_factory=list)
    summary: Optional[str] = None

    def has_blocker(self) -> bool:
        """是否存在关键阻断问题"""
        return any(issue.severity in ("critical", "high") for issue in self.issues)


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
    coverage_summary: Dict[str, Any] = Field(default_factory=dict)
    notes: Optional[str] = None

    def all_passed(self) -> bool:
        """是否全部测试通过"""
        return self.passed
