from typing import Any

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    """统一接口响应结构，与 Java 微服务 ApiResponse 风格保持一致。"""

    code: int = Field(default=0, description="业务状态码，0 表示成功")
    message: str = Field(default="success", description="响应说明")
    data: Any = Field(default=None, description="响应数据")

    # 函数功能：构造成功响应。
    @classmethod
    def success(cls, data: Any = None, message: str = "success") -> "ApiResponse":
        """返回统一成功结构。"""
        return cls(code=0, message=message, data=data)

    # 函数功能：构造失败响应。
    @classmethod
    def failed(cls, message: str, code: int = 500) -> "ApiResponse":
        """返回统一失败结构。"""
        return cls(code=code, message=message, data=None)
