from contextlib import asynccontextmanager

from fastapi import FastAPI

# settings 提供是否启用 Nacos 以及服务注册所需环境配置。
from app.core.config import settings
# NacosRegistry 封装服务启动注册和关闭注销逻辑。
from app.infrastructure.registry.nacos_registry import NacosRegistry


# 函数功能：创建 FastAPI 生命周期管理器，统一处理服务注册和资源释放。
@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务启动时注册到 Nacos，关闭时从 Nacos 注销。"""
    registry = NacosRegistry()
    print(f"AI 服务启动配置：{settings.startup_summary()}")

    if settings.nacos_enabled:
        await registry.register()
    else:
        print("Nacos 注册已禁用，AI 服务将仅以本地模式运行。")

    try:
        yield
    finally:
        if settings.nacos_enabled:
            await registry.deregister()
