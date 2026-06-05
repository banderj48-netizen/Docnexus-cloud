# SkillRegistry 是 Skills 的注册和查找入口。
from app.ai.skills.registry import SkillRegistry
# Skill 数据模型用于服务层返回结构化工具信息。
from app.schemas.skill import SkillInvokeRequest, SkillInvokeResponse, SkillMetadata


class SkillService:
    """Skill 服务层，负责技能列表查询和技能执行。"""

    # 函数功能：初始化 Skill 服务依赖。
    def __init__(self, registry: SkillRegistry) -> None:
        """注入技能注册表。"""
        self.registry = registry

    # 函数功能：列出当前已注册 Skills。
    async def list_skills(self) -> list[SkillMetadata]:
        """返回所有可调用技能元信息。"""
        return self.registry.list_metadata()

    # 函数功能：调用指定 Skill。
    async def invoke(self, skill_name: str, request: SkillInvokeRequest) -> SkillInvokeResponse:
        """根据技能名称查找并执行技能。"""
        skill = self.registry.get(skill_name)
        if skill is None:
            raise ValueError(f"技能不存在：{skill_name}")

        result = await skill.invoke(request.payload)
        return SkillInvokeResponse(skill_name=skill_name, result=result)
