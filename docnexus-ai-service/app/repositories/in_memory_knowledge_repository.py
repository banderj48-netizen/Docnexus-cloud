# RagChunk 是 RAG 检索和索引共享的知识片段数据结构。
from app.schemas.rag import RagChunk


class InMemoryKnowledgeRepository:
    """开发期知识库仓储，使用内存保存切片，后续可替换为向量数据库。"""

    # 函数功能：初始化内存片段列表。
    def __init__(self) -> None:
        """创建空知识片段存储。"""
        self._chunks: list[RagChunk] = []

    # 函数功能：批量保存知识片段。
    def save_chunks(self, chunks: list[RagChunk]) -> int:
        """写入知识片段并返回写入数量。"""
        self._chunks.extend(chunks)
        return len(chunks)

    # 函数功能：按照问题检索知识片段。
    def search(
        self,
        question: str,
        knowledge_base_id: str | None,
        top_k: int,
    ) -> list[RagChunk]:
        """基于简单关键词重合度检索片段，作为后续向量检索的替身。"""
        query_terms = {term.lower() for term in question.split() if term.strip()}
        matched_chunks: list[RagChunk] = []

        for chunk in self._chunks:
            if knowledge_base_id and chunk.knowledge_base_id != knowledge_base_id:
                continue

            content = chunk.content.lower()
            # 关键逻辑：没有英文空格分词时，退化为问题整体包含匹配，兼容中文短问题。
            score = sum(1 for term in query_terms if term in content)
            if score == 0 and question.strip() in chunk.content:
                score = 1

            if score > 0:
                matched_chunks.append(chunk.model_copy(update={"score": float(score)}))

        matched_chunks.sort(key=lambda item: item.score, reverse=True)
        return matched_chunks[:top_k]
