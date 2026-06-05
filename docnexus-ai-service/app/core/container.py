# DocumentSummarySkill 是内置资料摘要技能，用于验证 Skills 注册和调用链路。
from app.ai.skills.builtin.document_summary_skill import DocumentSummarySkill
# SkillRegistry 负责集中注册和查找所有可调用 Skills。
from app.ai.skills.registry import SkillRegistry
# InMemoryKnowledgeRepository 提供开发期 RAG 数据读写能力，后续可替换为向量库实现。
from app.repositories.in_memory_knowledge_repository import InMemoryKnowledgeRepository
# AgentService 负责对外承接 Agent 对话和能力说明。
from app.services.agent_service import AgentService
# RagService 负责资料索引和知识检索业务编排。
from app.services.rag_service import RagService
# SkillService 负责 Skills 查询和调用业务编排。
from app.services.skill_service import SkillService


class ApplicationContainer:
    """应用依赖容器，集中装配仓储、AI 能力和服务层对象。"""

    # 函数功能：初始化 AI 服务运行期依赖。
    def __init__(self) -> None:
        """初始化内存仓储、技能注册表和业务服务。"""
        self.knowledge_repository = InMemoryKnowledgeRepository()
        self.skill_registry = SkillRegistry()
        self.skill_registry.register(DocumentSummarySkill())

        self.rag_service = RagService(self.knowledge_repository)
        self.skill_service = SkillService(self.skill_registry)
        self.agent_service = AgentService(self.rag_service, self.skill_service)


container = ApplicationContainer()
