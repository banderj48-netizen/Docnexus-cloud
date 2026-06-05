# RagChunk 是切片器输出的统一知识片段结构。
from app.schemas.rag import RagChunk, RagDocumentInput


class SimpleTextChunker:
    """简单文本切片器，后续可替换为按标题、页码、Token 的专业切片器。"""

    # 函数功能：初始化切片长度配置。
    def __init__(self, chunk_size: int = 500) -> None:
        """保存单个片段最大字符数。"""
        self.chunk_size = chunk_size

    # 函数功能：将资料文本切分为 RAG 知识片段。
    def split(
        self,
        knowledge_base_id: str,
        document: RagDocumentInput,
    ) -> list[RagChunk]:
        """按固定字符长度切分资料正文。"""
        content = document.content.strip()
        if not content:
            return []

        chunks: list[RagChunk] = []
        for index, start in enumerate(range(0, len(content), self.chunk_size), start=1):
            # 关键逻辑：片段 ID 保持可读，便于排查引用来自哪份资料和第几个片段。
            chunk_content = content[start : start + self.chunk_size]
            chunks.append(
                RagChunk(
                    chunk_id=f"{document.document_id}-{index}",
                    knowledge_base_id=knowledge_base_id,
                    document_id=document.document_id,
                    title=document.title,
                    content=chunk_content,
                    score=0.0,
                )
            )

        return chunks
