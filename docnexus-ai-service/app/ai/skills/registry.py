# BaseSkill 定义所有 Skills 的统一接口，注册表只依赖抽象能力。
from app.ai.skills.base import BaseSkill
# SkillMetadata 用于对外展示技能列表。
from app.schemas.skill import SkillMetadata


class SkillRegistry:
    """Skill 注册表，集中管理内置技能和后续动态加载的技能。"""

    # 函数功能：初始化技能注册表。
    def __init__(self) -> None:
        """创建空技能字典。"""
        self._skills: dict[str, BaseSkill] = {}

    # 函数功能：注册一个技能实例。
    def register(self, skill: BaseSkill) -> None:
        """按技能名称注册技能，重复名称会被后注册的技能覆盖。"""
        metadata = skill.metadata()
        self._skills[metadata.name] = skill

    # 函数功能：获取指定名称的技能。
    def get(self, name: str) -> BaseSkill | None:
        """按名称查找技能，找不到时返回 None。"""
        return self._skills.get(name)

    # 函数功能：列出所有技能元信息。
    def list_metadata(self) -> list[SkillMetadata]:
        """返回所有已注册技能的元信息。"""
        return [skill.metadata() for skill in self._skills.values()]
