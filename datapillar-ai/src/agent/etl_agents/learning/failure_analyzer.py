"""
失败分析模块

分析 Agent 执行失败的原因，生成避免策略。
支持两种分析模式：
1. 规则匹配（快速，适合明确的语法/语义错误）
2. LLM 深度分析（慢，适合复杂的逻辑/需求偏差错误）
"""

import re
import json
import logging
from typing import Optional, List
from enum import Enum
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class FailureType(str, Enum):
    """失败类型"""
    SYNTAX_ERROR = "syntax_error"           # SQL 语法错误
    SEMANTIC_ERROR = "semantic_error"       # 语义错误（表/列不存在）
    LOGIC_ERROR = "logic_error"             # 逻辑错误（JOIN 条件错误）
    PERFORMANCE_ERROR = "performance_error" # 性能问题
    REQUIREMENT_MISMATCH = "requirement_mismatch"  # 需求理解偏差
    KNOWLEDGE_GAP = "knowledge_gap"         # 知识缺口
    UNKNOWN = "unknown"


class FailureAnalysis(BaseModel):
    """失败分析结果"""
    failure_type: FailureType
    error_message: str
    root_cause: Optional[str] = None
    involved_tables: List[str] = Field(default_factory=list)
    involved_columns: List[str] = Field(default_factory=list)
    avoidance_hint: Optional[str] = None
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)


class FailureAnalyzer:
    """
    失败分析器

    分析 SQL 生成、测试执行等环节的失败原因，
    提取模式，生成避免策略。
    """

    # 错误模式匹配规则
    ERROR_PATTERNS = {
        FailureType.SYNTAX_ERROR: [
            r"syntax error",
            r"unexpected token",
            r"invalid.*syntax",
            r"parse error",
        ],
        FailureType.SEMANTIC_ERROR: [
            r"table.*not found",
            r"column.*not found",
            r"unknown column",
            r"relation.*does not exist",
            r"no such table",
            r"invalid object name",
        ],
        FailureType.LOGIC_ERROR: [
            r"ambiguous column",
            r"duplicate column",
            r"join.*condition",
            r"cartesian product",
            r"missing.*join",
        ],
        FailureType.PERFORMANCE_ERROR: [
            r"timeout",
            r"out of memory",
            r"too many",
            r"resource.*exceeded",
        ],
        FailureType.REQUIREMENT_MISMATCH: [
            r"not.*expected",
            r"mismatch",
            r"incorrect.*result",
            r"wrong.*output",
        ],
    }

    # 失败类型对应的避免策略模板
    AVOIDANCE_TEMPLATES = {
        FailureType.SYNTAX_ERROR: "检查 SQL 语法，确保关键字、括号、引号正确配对",
        FailureType.SEMANTIC_ERROR: "验证表名和列名是否存在于目标数据库",
        FailureType.LOGIC_ERROR: "检查 JOIN 条件是否正确，避免笛卡尔积",
        FailureType.PERFORMANCE_ERROR: "优化查询性能，考虑添加索引或分页",
        FailureType.REQUIREMENT_MISMATCH: "重新理解用户需求，确认输出字段和过滤条件",
        FailureType.KNOWLEDGE_GAP: "补充缺失的表结构或业务规则信息",
        FailureType.UNKNOWN: "人工分析失败原因",
    }

    def analyze(
        self,
        error_message: str,
        sql_text: Optional[str] = None,
        user_query: Optional[str] = None,
        user_feedback: Optional[str] = None,
    ) -> FailureAnalysis:
        """
        分析失败原因

        Args:
            error_message: 错误信息
            sql_text: 失败的 SQL（可选）
            user_query: 用户原始查询（可选）
            user_feedback: 用户反馈（可选）

        Returns:
            失败分析结果
        """
        # 1. 识别失败类型
        failure_type = self._classify_error(error_message)

        # 2. 提取涉及的表和列
        involved_tables = self._extract_tables(error_message, sql_text)
        involved_columns = self._extract_columns(error_message, sql_text)

        # 3. 分析根因
        root_cause = self._find_root_cause(
            failure_type, error_message, sql_text, user_feedback
        )

        # 4. 生成避免策略
        avoidance_hint = self._generate_avoidance_hint(
            failure_type, error_message, involved_tables, involved_columns
        )

        # 5. 计算置信度
        confidence = self._calculate_confidence(failure_type, error_message)

        return FailureAnalysis(
            failure_type=failure_type,
            error_message=error_message,
            root_cause=root_cause,
            involved_tables=involved_tables,
            involved_columns=involved_columns,
            avoidance_hint=avoidance_hint,
            confidence=confidence,
        )

    def _classify_error(self, error_message: str) -> FailureType:
        """分类错误类型"""
        error_lower = error_message.lower()

        for failure_type, patterns in self.ERROR_PATTERNS.items():
            for pattern in patterns:
                if re.search(pattern, error_lower):
                    return failure_type

        return FailureType.UNKNOWN

    def _extract_tables(
        self,
        error_message: str,
        sql_text: Optional[str],
    ) -> List[str]:
        """提取涉及的表名"""
        tables = []

        # 从错误信息中提取
        table_patterns = [
            r"table\s+['\"]?(\w+)['\"]?",
            r"relation\s+['\"]?(\w+)['\"]?",
        ]
        for pattern in table_patterns:
            matches = re.findall(pattern, error_message, re.IGNORECASE)
            tables.extend(matches)

        # 从 SQL 中提取（简化版）
        if sql_text:
            from_pattern = r"(?:FROM|JOIN)\s+['\"]?(\w+)['\"]?"
            matches = re.findall(from_pattern, sql_text, re.IGNORECASE)
            tables.extend(matches)

        return list(set(tables))

    def _extract_columns(
        self,
        error_message: str,
        sql_text: Optional[str],
    ) -> List[str]:
        """提取涉及的列名"""
        columns = []

        # 从错误信息中提取
        column_patterns = [
            r"column\s+['\"]?(\w+)['\"]?",
            r"field\s+['\"]?(\w+)['\"]?",
        ]
        for pattern in column_patterns:
            matches = re.findall(pattern, error_message, re.IGNORECASE)
            columns.extend(matches)

        return list(set(columns))

    def _find_root_cause(
        self,
        failure_type: FailureType,
        error_message: str,
        sql_text: Optional[str],
        user_feedback: Optional[str],
    ) -> Optional[str]:
        """分析根因"""
        # 如果有用户反馈，优先使用
        if user_feedback:
            return f"用户反馈: {user_feedback}"

        # 根据失败类型推断根因
        if failure_type == FailureType.SYNTAX_ERROR:
            return "SQL 语法不符合目标数据库规范"
        elif failure_type == FailureType.SEMANTIC_ERROR:
            return "引用了不存在的表或列"
        elif failure_type == FailureType.LOGIC_ERROR:
            return "SQL 逻辑错误，可能是 JOIN 条件不正确"
        elif failure_type == FailureType.PERFORMANCE_ERROR:
            return "查询性能问题，可能缺少索引或数据量过大"
        elif failure_type == FailureType.REQUIREMENT_MISMATCH:
            return "生成结果与用户需求不符"
        else:
            return None

    def _generate_avoidance_hint(
        self,
        failure_type: FailureType,
        error_message: str,
        involved_tables: List[str],
        involved_columns: List[str],
    ) -> str:
        """生成避免策略"""
        base_hint = self.AVOIDANCE_TEMPLATES.get(failure_type, "")

        # 添加具体建议
        specific_hints = []
        if involved_tables:
            specific_hints.append(f"涉及表: {', '.join(involved_tables)}")
        if involved_columns:
            specific_hints.append(f"涉及列: {', '.join(involved_columns)}")

        if specific_hints:
            return f"{base_hint}。{'; '.join(specific_hints)}"
        return base_hint

    def _calculate_confidence(
        self,
        failure_type: FailureType,
        error_message: str,
    ) -> float:
        """计算分析置信度"""
        if failure_type == FailureType.UNKNOWN:
            return 0.3
        elif failure_type in (FailureType.SYNTAX_ERROR, FailureType.SEMANTIC_ERROR):
            return 0.9  # 这类错误很明确
        elif failure_type == FailureType.LOGIC_ERROR:
            return 0.7
        else:
            return 0.5

    async def analyze_with_llm(
        self,
        error_message: str,
        sql_text: Optional[str] = None,
        user_query: Optional[str] = None,
        user_feedback: Optional[str] = None,
        table_schemas: Optional[str] = None,
    ) -> FailureAnalysis:
        """
        使用 LLM 进行深度根因分析

        当规则匹配无法确定失败类型（UNKNOWN）或置信度较低时，
        使用 LLM 进行更深入的分析。

        Args:
            error_message: 错误信息
            sql_text: 失败的 SQL
            user_query: 用户原始查询
            user_feedback: 用户反馈
            table_schemas: 相关表结构

        Returns:
            失败分析结果
        """
        from src.integrations.llm import call_llm

        prompt = self._build_llm_analysis_prompt(
            error_message=error_message,
            sql_text=sql_text,
            user_query=user_query,
            user_feedback=user_feedback,
            table_schemas=table_schemas,
        )

        try:
            llm = call_llm(temperature=0.0)
            response = await llm.ainvoke(prompt)

            # 解析 LLM 响应
            analysis = self._parse_llm_response(response.content, error_message)
            logger.info(f"LLM 失败分析完成: {analysis.failure_type.value}, 置信度: {analysis.confidence}")
            return analysis

        except Exception as e:
            logger.error(f"LLM 失败分析异常: {e}")
            # 降级到规则匹配
            return self.analyze(
                error_message=error_message,
                sql_text=sql_text,
                user_query=user_query,
                user_feedback=user_feedback,
            )

    def _build_llm_analysis_prompt(
        self,
        error_message: str,
        sql_text: Optional[str],
        user_query: Optional[str],
        user_feedback: Optional[str],
        table_schemas: Optional[str],
    ) -> str:
        """构建 LLM 分析提示词"""
        prompt = f"""你是数据开发专家，请分析以下 ETL SQL 生成失败的根本原因。

## 用户需求
{user_query or '（未提供）'}

## 生成的 SQL
{sql_text or '（未提供）'}

## 错误信息
{error_message}

## 用户反馈
{user_feedback or '（未提供）'}

## 相关表结构
{table_schemas or '（未提供）'}

请分析并返回 JSON 格式的结果：
{{
    "failure_type": "分类（syntax_error|semantic_error|logic_error|performance_error|requirement_mismatch|knowledge_gap|unknown）",
    "root_cause": "根本原因的详细描述",
    "involved_tables": ["涉及的表名列表"],
    "involved_columns": ["涉及的列名列表"],
    "avoidance_hint": "避免此类错误的具体建议",
    "confidence": 0.0-1.0 的置信度
}}

只返回 JSON，不要其他内容。
"""
        return prompt

    def _parse_llm_response(self, response: str, error_message: str) -> FailureAnalysis:
        """解析 LLM 响应"""
        try:
            # 尝试从响应中提取 JSON
            json_match = re.search(r'\{[\s\S]*\}', response)
            if json_match:
                data = json.loads(json_match.group())

                failure_type_str = data.get("failure_type", "unknown")
                try:
                    failure_type = FailureType(failure_type_str)
                except ValueError:
                    failure_type = FailureType.UNKNOWN

                return FailureAnalysis(
                    failure_type=failure_type,
                    error_message=error_message,
                    root_cause=data.get("root_cause"),
                    involved_tables=data.get("involved_tables", []),
                    involved_columns=data.get("involved_columns", []),
                    avoidance_hint=data.get("avoidance_hint"),
                    confidence=float(data.get("confidence", 0.7)),
                )

        except json.JSONDecodeError as e:
            logger.warning(f"LLM 响应 JSON 解析失败: {e}")

        # 解析失败，返回默认结果
        return FailureAnalysis(
            failure_type=FailureType.UNKNOWN,
            error_message=error_message,
            root_cause="LLM 分析结果解析失败",
            confidence=0.3,
        )

    async def smart_analyze(
        self,
        error_message: str,
        sql_text: Optional[str] = None,
        user_query: Optional[str] = None,
        user_feedback: Optional[str] = None,
        table_schemas: Optional[str] = None,
    ) -> FailureAnalysis:
        """
        智能失败分析

        先使用规则匹配快速分析，如果结果是 UNKNOWN 或置信度低于阈值，
        则升级到 LLM 深度分析。

        Args:
            error_message: 错误信息
            sql_text: 失败的 SQL
            user_query: 用户原始查询
            user_feedback: 用户反馈
            table_schemas: 相关表结构

        Returns:
            失败分析结果
        """
        # 先用规则匹配
        rule_analysis = self.analyze(
            error_message=error_message,
            sql_text=sql_text,
            user_query=user_query,
            user_feedback=user_feedback,
        )

        # 如果规则匹配结果明确且置信度高，直接返回
        if rule_analysis.failure_type != FailureType.UNKNOWN and rule_analysis.confidence >= 0.7:
            logger.info(f"规则匹配成功: {rule_analysis.failure_type.value}")
            return rule_analysis

        # 否则升级到 LLM 分析
        logger.info("规则匹配置信度不足，升级到 LLM 分析")
        return await self.analyze_with_llm(
            error_message=error_message,
            sql_text=sql_text,
            user_query=user_query,
            user_feedback=user_feedback,
            table_schemas=table_schemas,
        )
