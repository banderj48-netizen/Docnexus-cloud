from v2.nacos import (
    ClientConfigBuilder,
    DeregisterInstanceParam,
    NacosNamingService,
    RegisterInstanceParam,
)

# settings 提供 Nacos 地址、账号和服务元数据配置。
from app.core.config import settings


class NacosRegistry:
    """Nacos 服务注册适配器，隔离 nacos-sdk-python 的具体调用方式。"""

    # 函数功能：初始化 Nacos 注册适配器状态。
    def __init__(self) -> None:
        """创建空客户端和注册 IP 状态。"""
        self.client = None
        self.registered_ip: str | None = None

    # 函数功能：服务启动时注册到 Nacos。
    async def register(self) -> None:
        """创建 Nacos 客户端并注册当前 AI 服务实例。"""
        self.registered_ip = settings.service_ip
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
                ip=self.registered_ip,
                port=settings.server_port,
                weight=settings.nacos_weight,
                cluster_name=settings.nacos_cluster_name,
                metadata=settings.nacos_metadata(),
                enabled=True,
                healthy=True,
                ephemeral=True,
            )
        )

        print(
            f"已注册到 Nacos: "
            f"{settings.service_name} {self.registered_ip}:{settings.service_port}"
        )

    # 函数功能：服务关闭时从 Nacos 注销。
    async def deregister(self) -> None:
        """注销当前 AI 服务实例。"""
        if self.client is None or self.registered_ip is None:
            return

        await self.client.deregister_instance(
            request=DeregisterInstanceParam(
                service_name=settings.service_name,
                group_name=settings.nacos_group,
                ip=self.registered_ip,
                port=settings.server_port,
                cluster_name=settings.nacos_cluster_name,
                ephemeral=True,
            )
        )

        print(
            f"已从 Nacos 注销: "
            f"{settings.service_name} {self.registered_ip}:{settings.service_port}"
        )
