from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

# ApiResponse 提供统一错误响应结构。
from app.schemas.common import ApiResponse


# 函数功能：为 FastAPI 应用注册全局异常处理器。
def register_exception_handlers(app: FastAPI) -> None:
    """注册业务异常处理，保持接口返回结构统一。"""

    # 函数功能：处理业务参数或资源不存在异常。
    @app.exception_handler(ValueError)
    async def value_error_handler(request: Request, exc: ValueError) -> JSONResponse:
        """将 ValueError 转换为统一业务失败响应。"""
        response = ApiResponse.failed(str(exc), code=400)
        return JSONResponse(status_code=400, content=response.model_dump())
