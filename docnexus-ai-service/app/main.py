import os
import socket
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from v2.nacos import (
    ClientConfigBuilder,
    DeregisterInstanceParam,
    NacosNamingService,
    RegisterInstanceParam,
)

SERVICE_NAME = os.getenv("SERVICE_NAME", "docnexus-ai-service")
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8105"))
SERVICE_IP = os.getenv("SERVICE_IP")

NACOS_SERVER_ADDR = os.getenv("NACOS_SERVER_ADDR", "127.0.0.1:8848")
NACOS_GROUP = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")
NACOS_NAMESPACE = os.getenv("NACOS_NAMESPACE", "")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "nacos")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "change-me")

nacos_client = None
registered_ip = None


def get_local_ip() -> str:
    """获取可被网关访问的本机 IP，避免注册成 127.0.0.1。"""
    if SERVICE_IP:
        return SERVICE_IP

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    finally:
        sock.close()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务启动时注册到 Nacos，关闭时注销。"""
    global nacos_client, registered_ip

    registered_ip = get_local_ip()

    client_config = (
        ClientConfigBuilder()
        .server_address(NACOS_SERVER_ADDR)
        .namespace_id(NACOS_NAMESPACE)
        .username(NACOS_USERNAME)
        .password(NACOS_PASSWORD)
        .log_level("INFO")
        .build()
    )

    nacos_client = await NacosNamingService.create_naming_service(client_config)

    await nacos_client.register_instance(
        request=RegisterInstanceParam(
            service_name=SERVICE_NAME,
            group_name=NACOS_GROUP,
            ip=registered_ip,
            port=SERVICE_PORT,
            weight=1.0,
            cluster_name="DEFAULT",
            metadata={"runtime": "python", "framework": "fastapi"},
            enabled=True,
            healthy=True,
            ephemeral=True,
        )
    )

    print(f"已注册到 Nacos: {SERVICE_NAME} {registered_ip}:{SERVICE_PORT}")

    try:
        yield
    finally:
        await nacos_client.deregister_instance(
            request=DeregisterInstanceParam(
                service_name=SERVICE_NAME,
                group_name=NACOS_GROUP,
                ip=registered_ip,
                port=SERVICE_PORT,
                cluster_name="DEFAULT",
                ephemeral=True,
            )
        )
        print(f"已从 Nacos 注销: {SERVICE_NAME} {registered_ip}:{SERVICE_PORT}")


app = FastAPI(
    title="文枢智能 DocNexus AI 服务",
    lifespan=lifespan,
)


@app.get("/actuator/health")
async def health():
    return {"status": "UP"}


if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=SERVICE_PORT,
        reload=False,
    )
