from v2.nacos import (
    ClientConfigBuilder,
    DeregisterInstanceParam,
    NacosNamingService,
    RegisterInstanceParam,
)

from app.config import settings


class NacosRegistry:
    """封装 Nacos 服务注册和注销逻辑。"""

    def __init__(self):
        self.client = None

    async def register(self):
        """服务启动时注册到 Nacos。"""
        client_config = (
            ClientConfigBuilder()
            .server_address(settings.nacos_server_addr)
            .namespace_id(settings.nacos_namespace)
            .username(settings.nacos_username)
            .password(settings.nacos_password)
            .log_level("INFO")
            .build()
        )

        self.client = await NacosNamingService.create_naming_service(client_config)

        await self.client.register_instance(
            request=RegisterInstanceParam(
                service_name=settings.service_name,
                group_name=settings.nacos_group,
                ip=settings.service_ip,
                port=settings.service_port,
                weight=1.0,
                cluster_name="DEFAULT",
                metadata={"runtime": "python", "framework": "fastapi"},
                enabled=True,
                healthy=True,
                ephemeral=True,
            )
        )

        print(
            f"已注册到 Nacos: "
            f"{settings.service_name} {settings.service_ip}:{settings.service_port}"
        )

    async def deregister(self):
        """服务关闭时从 Nacos 注销。"""
        if self.client is None:
            return

        await self.client.deregister_instance(
            request=DeregisterInstanceParam(
                service_name=settings.service_name,
                group_name=settings.nacos_group,
                ip=settings.service_ip,
                port=settings.service_port,
                cluster_name="DEFAULT",
                ephemeral=True,
            )
        )

        print(
            f"已从 Nacos 注销: "
            f"{settings.service_name} {settings.service_ip}:{settings.service_port}"
        )