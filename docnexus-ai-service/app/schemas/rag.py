from pydantic import BaseModel, Field


class RagDocumentInput(BaseModel):
    """RAG 索引文档输入，代表一个待切片的资料文本。"""

    document_id: str = Field(..., description="资料 ID")
    title: str = Field(..., description="资料标题")
    content: str = Field(..., description="资料正文")


class RagIndexRequest(BaseModel):
    """RAG 索引请求，用于将资料写入知识库。"""

    knowledge_base_id: str = Field(..., description="知识库 ID")
    documents: list[RagDocumentInput] = Field(default_factory=list, description="资料列表")


class RagQueryRequest(BaseModel):
    """RAG 检索请求，用于按问题查询知识片段。"""

    question: str = Field(..., description="检索问题")
    knowledge_base_id: str | None = Field(default=None, description="知识库 ID")
    top_k: int = Field(default=5, ge=1, le=20, description="返回片段数量")


class RagChunk(BaseModel):
    """RAG 知识片段，后续可扩展向量 ID、页码、坐标和引用格式。"""

    chunk_id: str = Field(..., description="片段 ID")
    knowledge_base_id: str = Field(..., description="知识库 ID")
    document_id: str = Field(..., description="资料 ID")
    title: str = Field(..., description="资料标题")
    content: str = Field(..., description="片段内容")
    score: float = Field(default=0.0, description="匹配分数")


class RagIndexResponse(BaseModel):
    """RAG 索引响应，返回写入的片段数量。"""

    indexed_chunks: int = Field(default=0, description="已索引片段数")


class RagQueryResponse(BaseModel):
    """RAG 检索响应，返回匹配片段列表。"""

    chunks: list[RagChunk] = Field(default_factory=list, description="匹配片段")
