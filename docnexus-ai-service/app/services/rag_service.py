# SimpleTextChunker 是 RAG 服务当前使用的开发期切片器。
from app.ai.rag.simple_chunker import SimpleTextChunker
# InMemoryKnowledgeRepository 是当前知识片段数据交互实现。
from app.repositories.in_memory_knowledge_repository import InMemoryKnowledgeRepository
# RAG 请求响应模型用于保证服务层和路由层的数据契约清晰。
from app.schemas.rag import RagIndexRequest, RagIndexResponse, RagQueryRequest, RagQueryResponse


class RagService:
    """RAG 服务层，负责编排资料切片、索引写入和知识检索。"""

    # 函数功能：初始化 RAG 服务依赖。
    def __init__(self, repository: InMemoryKnowledgeRepository) -> None:
        """注入知识仓储并创建默认切片器。"""
        self.repository = repository
        self.chunker = SimpleTextChunker()

    # 函数功能：索引资料到知识库。
    async def index_documents(self, request: RagIndexRequest) -> RagIndexResponse:
        """将资料切片后保存到知识仓储。"""
        chunks = []
        for document in request.documents:
            chunks.extend(self.chunker.split(request.knowledge_base_id, document))

        indexed_count = self.repository.save_chunks(chunks)
        return RagIndexResponse(indexed_chunks=indexed_count)

    # 函数功能：根据问题检索知识片段。
    async def query(self, request: RagQueryRequest) -> RagQueryResponse:
        """查询知识仓储并返回匹配片段。"""
        chunks = self.repository.search(
            question=request.question,
            knowledge_base_id=request.knowledge_base_id,
            top_k=request.top_k,
        )
        return RagQueryResponse(chunks=chunks)
