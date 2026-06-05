# container 是应用依赖容器，集中持有服务层单例对象。
from app.core.container import container
# AgentService 负责 Agent 对话与能力说明。
from app.services.agent_service import AgentService
# RagService 负责 RAG 索引与检索业务。
from app.services.rag_service import RagService
# SkillService 负责 Skills 查询与调用业务。
from app.services.skill_service import SkillService


# 函数功能：获取 Agent 服务实例。
def get_agent_service() -> AgentService:
    """返回应用级 AgentService 单例。"""
    return container.agent_service


# 函数功能：获取 RAG 服务实例。
def get_rag_service() -> RagService:
    """返回应用级 RagService 单例。"""
    return container.rag_service


# 函数功能：获取 Skill 服务实例。
def get_skill_service() -> SkillService:
    """返回应用级 SkillService 单例。"""
    return container.skill_service
