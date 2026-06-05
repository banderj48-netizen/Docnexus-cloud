from fastapi import APIRouter, Depends

# get_rag_service 用于从依赖容器获取 RAG 服务实例。
from app.api.dependencies import get_rag_service
# ApiResponse 保持与 Java 微服务一致的统一响应格式。
from app.schemas.common import ApiResponse
# RAG 请求模型用于索引资料和检索知识片段。
from app.schemas.rag import RagIndexRequest, RagQueryRequest
# RagService 负责 RAG 业务编排。
from app.services.rag_service import RagService

router = APIRouter()


# 函数功能：将资料写入 RAG 知识库。
@router.post("/index", response_model=ApiResponse)
async def index_documents(
    request: RagIndexRequest,
    service: RagService = Depends(get_rag_service),
) -> ApiResponse:
    """对资料进行切片并写入知识仓储。"""
    result = await service.index_documents(request)
    return ApiResponse.success(result.model_dump())


# 函数功能：根据问题检索 RAG 知识片段。
@router.post("/query", response_model=ApiResponse)
async def query(
    request: RagQueryRequest,
    service: RagService = Depends(get_rag_service),
) -> ApiResponse:
    """检索与问题相关的知识片段。"""
    result = await service.query(request)
    return ApiResponse.success(result.model_dump())
