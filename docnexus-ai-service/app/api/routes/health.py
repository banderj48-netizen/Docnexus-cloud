from fastapi import APIRouter

# settings 提供服务名称和版本，健康检查用于网关或注册中心探活。
from app.core.config import settings

router = APIRouter(tags=["Health"])


# 函数功能：返回服务健康状态。
@router.get("/health")
async def health() -> dict:
    """返回 Python AI 服务健康检查结果。"""
    return {
        "status": "UP",
        "service": settings.service_name,
        "version": settings.app_version,
    }
