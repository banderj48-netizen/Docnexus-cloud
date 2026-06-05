import uvicorn
from fastapi import FastAPI

# api_router 负责集中挂载健康检查、Agent、RAG 和 Skills 路由。
from app.api.router import api_router
# settings 提供应用标题、版本和端口配置。
from app.core.config import settings
# register_exception_handlers 负责注册统一异常响应。
from app.core.exception_handlers import register_exception_handlers
# lifespan 负责应用启动和关闭时的注册中心生命周期处理。
from app.core.lifespan import lifespan


# 函数功能：创建并装配 FastAPI 应用。
def create_app() -> FastAPI:
    """创建 AI 服务应用实例并挂载路由、异常处理器。"""
    fastapi_app = FastAPI(
        title=settings.app_title,
        version=settings.app_version,
        lifespan=lifespan,
    )
    fastapi_app.include_router(api_router)
    register_exception_handlers(fastapi_app)
    return fastapi_app


app = create_app()


# 函数功能：通过 uvicorn 启动本地 AI 服务。
def run() -> None:
    """使用配置端口启动 FastAPI 服务。"""
    uvicorn.run(
        "app.main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=False,
    )


if __name__ == "__main__":
    run()
