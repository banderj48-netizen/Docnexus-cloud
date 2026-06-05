from fastapi import APIRouter, Depends

# get_skill_service 用于从依赖容器获取 Skill 服务实例。
from app.api.dependencies import get_skill_service
# ApiResponse 保持与 Java 微服务一致的统一响应格式。
from app.schemas.common import ApiResponse
# SkillInvokeRequest 定义调用技能时的入参结构。
from app.schemas.skill import SkillInvokeRequest
# SkillService 负责技能列表查询和技能执行。
from app.services.skill_service import SkillService

router = APIRouter()


# 函数功能：查询当前可用 Skills。
@router.get("", response_model=ApiResponse)
async def list_skills(
    service: SkillService = Depends(get_skill_service),
) -> ApiResponse:
    """返回所有已注册技能。"""
    skills = await service.list_skills()
    return ApiResponse.success([skill.model_dump() for skill in skills])


# 函数功能：调用指定 Skill。
@router.post("/{skill_name}/invoke", response_model=ApiResponse)
async def invoke_skill(
    skill_name: str,
    request: SkillInvokeRequest,
    service: SkillService = Depends(get_skill_service),
) -> ApiResponse:
    """执行指定技能并返回结果。"""
    result = await service.invoke(skill_name, request)
    return ApiResponse.success(result.model_dump())
