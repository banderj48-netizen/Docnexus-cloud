from pydantic import BaseModel, Field


class SkillMetadata(BaseModel):
    """Skill 元信息，用于描述一个可调用 AI 工具。"""

    name: str = Field(..., description="技能名称")
    description: str = Field(..., description="技能说明")
    input_schema: dict = Field(default_factory=dict, description="输入结构说明")


class SkillInvokeRequest(BaseModel):
    """Skill 调用请求，payload 由具体技能自行解释。"""

    payload: dict = Field(default_factory=dict, description="技能输入参数")


class SkillInvokeResponse(BaseModel):
    """Skill 调用响应，返回技能名称和执行结果。"""

    skill_name: str = Field(..., description="技能名称")
    result: dict = Field(default_factory=dict, description="执行结果")
