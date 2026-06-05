# BaseSkill 是内置技能需要实现的统一抽象接口。
from app.ai.skills.base import BaseSkill
# SkillMetadata 用于声明该技能的名称、说明和输入结构。
from app.schemas.skill import SkillMetadata


class DocumentSummarySkill(BaseSkill):
    """资料摘要技能，先提供确定性摘要能力，后续可替换为大模型摘要。"""

    # 函数功能：返回资料摘要技能元信息。
    def metadata(self) -> SkillMetadata:
        """返回资料摘要技能的名称和输入约定。"""
        return SkillMetadata(
            name="document_summary",
            description="根据输入文本生成简短资料摘要",
            input_schema={
                "text": "需要摘要的资料文本",
                "max_length": "摘要最大字符数，默认 180",
            },
        )

    # 函数功能：执行资料摘要逻辑。
    async def invoke(self, payload: dict) -> dict:
        """截取文本前部作为开发期摘要结果。"""
        text = str(payload.get("text", "")).strip()
        max_length = int(payload.get("max_length", 180))

        if not text:
            return {"summary": "", "message": "未提供可摘要文本"}

        # 关键逻辑：当前先使用确定性摘要，避免本地开发强依赖大模型密钥。
        summary = text[:max_length]
        if len(text) > max_length:
            summary = f"{summary}..."

        return {"summary": summary, "source_length": len(text)}
