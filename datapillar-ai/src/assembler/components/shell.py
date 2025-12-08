"""
Shell 组件组装器
"""

from typing import Dict, Any
from ..base import ComponentAssembler


class ShellAssembler(ComponentAssembler):
    """
    Shell 脚本执行组装器

    LLM 应返回格式：
    {
        "component": "shell",
        "scriptContent": "#!/bin/bash\necho 'Hello World'",
        "workDir": "/tmp"  # 可选
    }

    输出格式：
    {
        "id": "shell-1",
        "type": "shell",
        "position": {"x": 350, "y": 200},
        "data": {
            "label": "Shell脚本",
            "script_content": "#!/bin/bash...",
            "work_dir": "/tmp",
            "status": "idle",
            "forbidden": false
        }
    }
    """

    @property
    def component_type(self) -> str:
        return "shell"

    def validate(self, step: Dict[str, Any]) -> bool:
        """
        验证必需参数

        必需：
        - scriptContent: Shell 脚本内容
        """
        return "scriptContent" in step and step["scriptContent"].strip()

    def assemble_node(self, step: Dict[str, Any], index: int, node_id: str = None) -> Dict[str, Any]:
        """
        组装 Shell 节点

        Args:
            step: LLM 返回的配置
            index: 节点索引
            node_id: 可选的自定义节点ID

        Returns:
            标准 ReactFlow 节点
        """
        # 生成节点ID
        if node_id is None:
            node_id = f"shell-{index + 1}"

        script_content = step.get("scriptContent", "").strip()
        work_dir = step.get("workDir", "")

        # 生成显示标签
        label = self._generate_label_from_script(script_content)

        # 生成节点位置
        position = self.generate_position(index, 0)

        # 组装节点（转换为下划线命名）
        return {
            "id": node_id,
            "type": "shell",
            "position": position,
            "data": {
                "label": label,
                "script_content": script_content,  # 转换为下划线命名
                "work_dir": work_dir,
                "status": "idle",
                "forbidden": False,
            },
            "measured": {"width": 85, "height": 32},
            "selected": False
        }

    def generate_label(self, step: Dict[str, Any]) -> str:
        """生成节点显示标签"""
        script_content = step.get("scriptContent", "")
        return self._generate_label_from_script(script_content)

    def _generate_label_from_script(self, script_content: str) -> str:
        """
        从脚本内容生成简洁的标签

        尝试从脚本的第一行注释或命令中提取关键信息
        """
        lines = script_content.strip().split("\n")

        # 查找第一个有意义的注释行
        for line in lines:
            line = line.strip()
            if line.startswith("#") and not line.startswith("#!"):
                # 去掉 # 符号，取前20个字符
                comment = line.lstrip("#").strip()
                if comment:
                    return f"Shell: {comment[:20]}"

        # 如果没有注释，使用通用标签
        return "Shell脚本"
