# RagQueryResponse 是 Agent 组织答案时使用的检索结果结构。
from app.schemas.rag import RagQueryResponse
# SkillInvokeResponse 是 Agent 汇总工具执行结果时使用的结构。
from app.schemas.skill import SkillInvokeResponse


class DocNexusAgent:
    """文枢智能默认 Agent，负责把检索片段和技能结果组织成可解释答案。"""

    # 函数功能：根据 RAG 和 Skills 结果生成回答。
    async def answer(
        self,
        question: str,
        rag_result: RagQueryResponse,
        skill_results: list[SkillInvokeResponse],
    ) -> str:
        """生成开发期确定性回答，后续接入大模型后替换此处逻辑。"""
        if not rag_result.chunks and not skill_results:
            return f"已收到问题：{question}。当前没有检索到可引用资料，也没有执行技能。"

        answer_parts = [f"问题：{question}"]

        if rag_result.chunks:
            top_chunk = rag_result.chunks[0]
            answer_parts.append(
                f"基于资料《{top_chunk.title}》的相关片段，当前可参考内容为：{top_chunk.content}"
            )

        if skill_results:
            skill_names = "、".join(item.skill_name for item in skill_results)
            answer_parts.append(f"已执行技能：{skill_names}。")

        return "\n".join(answer_parts)
