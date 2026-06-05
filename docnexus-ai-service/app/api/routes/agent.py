from fastapi import APIRouter, Depends

# get_agent_service 用于从依赖容器获取 Agent 服务实例。
from app.api.dependencies import get_agent_service
# Agent 请求模型用于接收前端对话入参。
from app.schemas.agent import AgentChatRequest
# ApiResponse 保持与 Java 微服务一致的统一响应格式。
from app.schemas.common import ApiResponse
# AgentService 负责 Agent 能力说明和对话编排。
from app.services.agent_service import AgentService

router = APIRouter()


# 函数功能：查询 AI 服务能力清单。
@router.get("/capabilities", response_model=ApiResponse)
async def capabilities(
    service: AgentService = Depends(get_agent_service),
) -> ApiResponse:
    """返回 Agent、RAG、Skills 等能力说明。"""
    result = await service.capabilities()
    return ApiResponse.success(result.model_dump())


# 函数功能：执行一次 Agent 对话。
@router.post("/chat", response_model=ApiResponse)
async def chat(
    request: AgentChatRequest,
    service: AgentService = Depends(get_agent_service),
) -> ApiResponse:
    """执行 Agent 对话编排并返回回答。"""
    result = await service.chat(request)
    return ApiResponse.success(result.model_dump())
