from abc import ABC, abstractmethod

# SkillMetadata 描述技能名称、说明和输入结构，供路由层返回给前端。
from app.schemas.skill import SkillMetadata


class BaseSkill(ABC):
    """AI Skill 抽象基类，所有技能都需要实现元信息和执行逻辑。"""

    # 函数功能：返回技能元信息。
    @abstractmethod
    def metadata(self) -> SkillMetadata:
        """返回技能名称、说明和输入结构。"""

    # 函数功能：执行具体技能逻辑。
    @abstractmethod
    async def invoke(self, payload: dict) -> dict:
        """根据 payload 执行技能并返回结构化结果。"""
