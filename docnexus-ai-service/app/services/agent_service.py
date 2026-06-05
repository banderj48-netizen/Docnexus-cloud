# DocNexusAgent 是默认 Agent 实现，负责把检索和技能结果组织成回答。
from app.ai.agents.docnexus_agent import DocNexusAgent
# settings 提供服务名称，用于能力说明接口。
from app.core.config import settings
# Agent 请求响应模型定义路由层与服务层之间的数据契约。
from app.schemas.agent import AgentCapabilityResponse, AgentChatRequest, AgentChatResponse
# RagQueryRequest 用于 Agent 对话时触发知识检索。
from app.schemas.rag import RagQueryRequest
# SkillInvokeRequest 用于 Agent 对话时触发指定技能。
from app.schemas.skill import SkillInvokeRequest, SkillInvokeResponse
# RagService 负责 Agent 需要的知识检索能力。
from app.services.rag_service import RagService
# SkillService 负责 Agent 需要的技能调用能力。
from app.services.skill_service import SkillService


class AgentService:
    """Agent 服务层，负责统一编排 RAG、Skills 和默认 Agent。"""

    # 函数功能：初始化 Agent 服务依赖。
    def __init__(self, rag_service: RagService, skill_service: SkillService) -> None:
        """注入 RAG、Skills 服务并创建默认 Agent。"""
        self.rag_service = rag_service
        self.skill_service = skill_service
        self.agent = DocNexusAgent()

    # 函数功能：返回 AI 服务当前能力清单。
    async def capabilities(self) -> AgentCapabilityResponse:
        """返回 Agent、RAG、Skills 等核心能力说明。"""
        return AgentCapabilityResponse(
            service_name=settings.service_name,
            capabilities=[
                "Agent 对话编排",
                "RAG 资料索引",
                "RAG 知识检索",
                "Skills 注册与调用",
                "Nacos 服务注册",
            ],
            routes=[
                "/api/agent/health",
                "/api/agent/capabilities",
                "/api/agent/chat",
                "/api/agent/rag/index",
                "/api/agent/rag/query",
                "/api/agent/skills",
            ],
        )

    # 函数功能：处理 Agent 对话请求。
    async def chat(self, request: AgentChatRequest) -> AgentChatResponse:
        """执行知识检索、可选技能调用，并返回 Agent 回答。"""
        rag_result = await self.rag_service.query(
            RagQueryRequest(
                question=request.question,
                knowledge_base_id=request.knowledge_base_id,
                top_k=5,
            )
        )

        skill_results: list[SkillInvokeResponse] = []
        for skill_name in request.skill_names:
            # 关键逻辑：Agent 将问题作为默认技能输入，后续可升级为模型自动规划参数。
            skill_results.append(
                await self.skill_service.invoke(
                    skill_name,
                    SkillInvokeRequest(payload={"text": request.question}),
                )
            )

        answer = await self.agent.answer(request.question, rag_result, skill_results)
        return AgentChatResponse(
            answer=answer,
            citations=[chunk.model_dump() for chunk in rag_result.chunks],
            skill_results=[item.model_dump() for item in skill_results],
        )
