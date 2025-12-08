import json
import asyncio
import copy
from typing import Dict, Any, List
import logging

logger = logging.getLogger(__name__)
from langchain_core.messages import AIMessage
from langgraph.graph import StateGraph, END
from langgraph.types import Command

from src.agent.state import OrchestratorState
from src.agent.schemas import WorkflowOutput
from src.agent.utils import extract_json_from_text
from src.integrations.llm import call_llm


class CoderAgent:
    """
    CoderAgent - 代码生成专家（Worker）
    
    【核心特性】
    1. 并发加速：使用 asyncio.gather 并行处理所有节点的配置生成，而非串行等待。
    2. 职责解耦：Slot填充、拓扑构建、Prompt构建分离。
    3. 标准流控：使用 Command 控制 LangGraph 流程。
    """

    def __init__(self):
        # 初始化 LLM，开启 JSON 模式增强稳定性
        self.llm = call_llm(temperature=0.1, enable_json_mode=True)

    async def __call__(self, state: OrchestratorState) -> Command:
        """
        Worker 核心入口
        """
        logger.info("💻 CoderAgent: 开始生成工作流配置...")

        # 1. 获取并规范化 Plan 数据
        plan = getattr(state, "plan", None)
        if not plan:
            return self._handle_error("未找到执行计划 (state.plan 为空)")
        
        # 兼容 Pydantic 对象或 Dict
        plan_data = plan if isinstance(plan, dict) else plan.model_dump()
        
        # ⚠️ 深拷贝：因为我们要修改 nodes 内部结构，不要污染原始 plan 记录
        workflow_data = copy.deepcopy(plan_data)
        nodes = workflow_data.get("nodes", [])
        edges = workflow_data.get("edges", [])

        try:
            # =========================================================
            # 🔥 核心优化：并发填充所有节点的 Slot
            # 注意：这里我们只收集任务，不立即 await，从而实现并发
            # =========================================================
            tasks = []
            
            # 筛选出需要 LLM 填充的节点 (带 __slot__: true 的节点)
            for node in nodes:
                config = node.get("data", {}).get("config", {})
                if config.get("__slot__"):
                    # 创建协程任务并加入列表
                    tasks.append(self._process_single_node(node))
            
            if tasks:
                logger.info(f"🚀 [并发启动] 正在并行处理 {len(tasks)} 个节点的配置生成...")
                # 🔥 并发执行所有 LLM 调用，等待全部完成
                # 相比串行循环，这里的时间消耗 = 最慢的那个节点耗时，而不是总和
                await asyncio.gather(*tasks)
            else:
                logger.info("ℹ️ 没有发现需要填充的 Slot，直接使用原始配置")
            
            # =========================================================
            # 3. 构建最终拓扑结构（添加 Start/End 节点并连接）
            # =========================================================
            final_nodes, final_edges = self._build_topology(nodes, edges)

            # 4. 组装最终输出对象 (Pydantic 校验)
            workflow_output = WorkflowOutput(
                workflowName=workflow_data.get("workflowName", "Generated Workflow"),
                taskType="ETL",
                description=workflow_data.get("description", ""),
                nodes=final_nodes,
                edges=final_edges,
            )

            logger.info(f"✅ 工作流生成完成: {workflow_output.workflowName}")

            # 5. 返回 Command (显式结束子图)
            return Command(
                update={
                    "messages": [AIMessage(content=f"代码生成完成，工作流已就绪：{workflow_output.workflowName}")],
                    # 这里转为 dict 存入 state
                    "workflow": workflow_output.model_dump(mode="json"),
                    "current_agent": "coder_agent",
                    "is_found": True
                },
                goto=END  # 明确告诉父图：Coder 任务结束
            )

        except Exception as e:
            logger.exception(f"❌ CoderAgent 运行异常: {e}")
            return self._handle_error(f"工作流生成发生系统错误: {str(e)}")

    async def _process_single_node(self, node: Dict[str, Any]):
        """
        [原子任务] 处理单个节点：生成 Prompt -> 调用 LLM -> 更新 Node Config
        注意：此方法会被并发调用，修改的是 node 对象的引用
        """
        node_id = node["id"]
        node_type = node["type"]
        config = node["data"]["config"]
        context_hints = config.get("__context_hints__", {})

        # 构造 Prompt
        prompt = self._build_prompt(node, config, context_hints)

        try:
            # ⏳ 耗时操作：调用 LLM
            response = await self.llm.ainvoke(prompt)
            
            # 解析 JSON
            generated_json = extract_json_from_text(response.content)
            filled_config = json.loads(generated_json)

            # 🧹 清理标记字段 (防止污染前端)
            filled_config.pop("__slot__", None)
            filled_config.pop("__context_hints__", None)

            # 🔄 更新节点配置 (引用修改)
            node["data"]["config"] = filled_config
            # logger.debug(f"✨ 节点 {node_id} ({node_type}) 填充完毕")

        except Exception as e:
            logger.error(f"❌ 节点 {node_id} 配置生成失败: {e}")
            # 兜底策略：移除 slot 标记，保留错误信息，防止前端死循环加载
            node["data"]["config"]["__slot__"] = False
            node["data"]["config"]["__error__"] = f"生成失败: {str(e)}"

    def _build_topology(self, nodes: List[Dict], edges: List[Dict]):
        """
        辅助方法：标准化拓扑，添加 Start/End 节点
        """
        start_node = {
            "id": "node_start_sys", 
            "type": "start", 
            "position": {"x": 50, "y": 200}, 
            "data": {"label": "开始"}
        }
        end_node = {
            "id": "node_end_sys", 
            "type": "end", 
            "position": {"x": 1200, "y": 200}, 
            "data": {"label": "结束"}
        }

        # 过滤掉可能已存在的 start/end (避免重复添加)
        biz_nodes = [n for n in nodes if n["type"] not in ("start", "end")]
        
        if not biz_nodes:
            # 空流程兜底
            return [start_node, end_node], [{"id": "link_start_end", "source": start_node["id"], "target": end_node["id"]}]

        first_id = biz_nodes[0]["id"]
        last_id = biz_nodes[-1]["id"]

        # 组装节点列表
        final_nodes = [start_node] + biz_nodes + [end_node]
        
        # 组装连线
        # Start -> 第一个业务节点
        edges.insert(0, {
            "id": f"link_start_{first_id}",
            "source": start_node["id"],
            "target": first_id
        })
        # 最后一个业务节点 -> End
        edges.append({
            "id": f"link_{last_id}_end",
            "source": last_id,
            "target": end_node["id"]
        })

        return final_nodes, edges

    def _build_prompt(self, node, config, hints):
        """构造 Prompt 模板"""
        # 显式序列化为字符串，确保 prompt 格式正确
        config_str = json.dumps(config, indent=2, ensure_ascii=False)
        hints_str = json.dumps(hints, indent=2, ensure_ascii=False)
        
        return f"""
        你是 Data AI Builder 的配置生成专家。
        请根据上下文为【{node['type']}】节点生成完整的 JSON 配置。

        ## 节点信息
        - ID: {node['id']}
        - 类型: {node['type']}
        - 标签: {node['data'].get('label', '未命名')}

        ## 待填充的配置模板

        {config_str}

        ## 业务上下文 (Context Hints)
        {hints_str}
        ## 要求
        基于上下文填充：利用 Context Hints 中的表名、字段映射、SQL逻辑填充模板中的空值。

        保持结构：输出的 JSON 结构必须与模板完全一致。

        清理数据：输出结果中不要包含 __slot__ 和 __context_hints__ 字段。

        格式严格：只输出标准的 JSON 字符串，不要 Markdown 代码块。
        """
    def _handle_error(self, msg: str) -> Command: 
        """统一错误处理返回""" 
        return Command(update={ "messages": [AIMessage(content=msg)], "is_found": False }, goto=END)

    def build_coder_subgraph(): 
        """构建子图""" 
        builder = StateGraph(OrchestratorState)
        builder.add_node("coder_llm", CoderAgent()) 
        builder.set_entry_point("coder_llm") 
        # 因为 CoderAgent 返回了 Command(goto=END)，这里只需要定义节点 , 但为了图结构的完整性，显式添加边是好习惯 
        builder.add_edge("coder_llm", END) 

        return builder.compile()