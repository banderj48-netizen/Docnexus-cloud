from fastapi import APIRouter

# agent_router 承载 Agent 对话和能力查询接口。
from app.api.routes.agent import router as agent_router
# health_router 承载服务健康检查接口。
from app.api.routes.health import router as health_router
# rag_router 承载 RAG 索引和检索接口。
from app.api.routes.rag import router as rag_router
# skill_router 承载 Skills 查询和调用接口。
from app.api.routes.skills import router as skill_router

api_router = APIRouter(prefix="/api/agent")
api_router.include_router(health_router)
api_router.include_router(agent_router, tags=["Agent"])
api_router.include_router(rag_router, prefix="/rag", tags=["RAG"])
api_router.include_router(skill_router, prefix="/skills", tags=["Skills"])
