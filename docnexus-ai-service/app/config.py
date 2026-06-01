import os


class ServiceConfig:
    """AI 服务运行配置，优先从环境变量读取，便于 IDEA 和部署环境覆盖。"""

    service_name: str = os.getenv("SERVICE_NAME", "docnexus-ai-service")
    service_ip: str = os.getenv("SERVICE_IP", "127.0.0.1")
    service_port: int = int(os.getenv("SERVICE_PORT", "8105"))

    nacos_server_addr: str = os.getenv("NACOS_SERVER_ADDR", "127.0.0.1:8848")
    nacos_group: str = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")
    nacos_namespace: str = os.getenv("NACOS_NAMESPACE", "")
    nacos_username: str = os.getenv("NACOS_USERNAME", "nacos")
    nacos_password: str = os.getenv("NACOS_PASSWORD", "change-me")


settings = ServiceConfig()
