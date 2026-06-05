from pydantic import BaseModel, Field


class AgentChatRequest(BaseModel):
    """Agent 对话请求，用于承载用户问题和可选知识库范围。"""

    question: str = Field(..., description="用户问题")
    knowledge_base_id: str | None = Field(default=None, description="知识库 ID")
    skill_names: list[str] = Field(default_factory=list, description="期望调用的技能名称")


class AgentChatResponse(BaseModel):
    """Agent 对话响应，包含答案、引用和技能执行结果。"""

    answer: str = Field(..., description="Agent 生成的回答")
    citations: list[dict] = Field(default_factory=list, description="引用片段")
    skill_results: list[dict] = Field(default_factory=list, description="技能执行结果")


class AgentCapabilityResponse(BaseModel):
    """Agent 能力说明响应，用于前端动态展示可用 AI 能力。"""

    service_name: str = Field(..., description="服务名称")
    capabilities: list[str] = Field(default_factory=list, description="能力列表")
    routes: list[str] = Field(default_factory=list, description="核心接口路径")
