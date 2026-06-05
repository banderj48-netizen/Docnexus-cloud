import os
import socket
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parents[2]
load_dotenv(BASE_DIR / ".env")


# 函数功能：获取可被网关或注册中心访问的本机 IP。
def get_local_ip() -> str:
    """自动探测当前机器对外通信使用的本机 IP。"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # 关键逻辑：UDP connect 不会真正发包，只用于让系统选择默认出口网卡。
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    finally:
        sock.close()


# 函数功能：读取字符串环境变量。
def get_env(name: str, default: str = "") -> str:
    """读取字符串配置。"""
    return os.getenv(name, default)


# 函数功能：读取整数环境变量。
def get_int_env(name: str, default: int) -> int:
    """读取整数配置，空值时使用默认值。"""
    value = os.getenv(name)
    return default if value is None or value == "" else int(value)


# 函数功能：读取布尔环境变量。
def get_bool_env(name: str, default: bool) -> bool:
    """读取布尔配置。"""
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return value.lower() in {"true", "1", "yes", "y", "on"}


class AppSettings:
    """AI 服务全局配置，集中读取端口、注册中心和中间件配置。"""

    # 函数功能：从 .env 和系统环境变量构建全局配置。
    def __init__(self) -> None:
        """初始化 Python AI 服务运行配置。"""
        self.app_env = get_env("APP_ENV", "local")
        self.app_title = get_env("APP_TITLE", "文枢智能 DocNexus AI 服务")
        self.app_version = get_env("APP_VERSION", "0.1.0")

        self.service_name = get_env("SERVICE_NAME", "docnexus-ai-service")
        self.server_host = get_env("SERVER_HOST", "0.0.0.0")
        self.server_port = get_int_env("SERVER_PORT", get_int_env("SERVICE_PORT", 8105))
        self.service_port = self.server_port
        self.service_ip = get_env("SERVICE_IP", "") or get_local_ip()

        self.nacos_enabled = get_bool_env("NACOS_ENABLED", True)
        self.nacos_server_addr = get_env("NACOS_SERVER_ADDR", "127.0.0.1:8848")
        self.nacos_group = get_env("NACOS_GROUP", "DEFAULT_GROUP")
        self.nacos_namespace = get_env("NACOS_NAMESPACE", "")
        self.nacos_username = get_env("NACOS_USERNAME", "nacos")
        self.nacos_password = get_env("NACOS_PASSWORD", "change-me")
        self.nacos_cluster_name = get_env("NACOS_CLUSTER_NAME", "DEFAULT")
        self.nacos_weight = float(get_env("NACOS_WEIGHT", "1.0"))

        self.redis_host = get_env("REDIS_HOST", "127.0.0.1")
        self.redis_port = get_int_env("REDIS_PORT", 6379)
        self.redis_database = get_int_env("REDIS_DATABASE", 0)
        self.redis_username = get_env("REDIS_USERNAME", "")
        self.redis_password = get_env("REDIS_PASSWORD", "")
        self.redis_ssl = get_bool_env("REDIS_SSL", False)

        self.mysql_host = get_env("MYSQL_HOST", "127.0.0.1")
        self.mysql_port = get_int_env("MYSQL_PORT", 3306)
        self.mysql_database = get_env("MYSQL_DATABASE", "docnexus_cloud")
        self.mysql_username = get_env("MYSQL_USERNAME", "root")
        self.mysql_password = get_env("MYSQL_PASSWORD", "")
        self.mysql_url = get_env("MYSQL_URL", "")

        self.rocketmq_namesrv_addr = get_env("ROCKETMQ_NAMESRV_ADDR", "127.0.0.1:9876")
        self.rocketmq_producer_group = get_env(
            "ROCKETMQ_PRODUCER_GROUP",
            "docnexus-ai-producer-group",
        )
        self.rocketmq_consumer_group = get_env(
            "ROCKETMQ_CONSUMER_GROUP",
            "docnexus-ai-consumer-group",
        )
        self.rocketmq_topic_prefix = get_env("ROCKETMQ_TOPIC_PREFIX", "docnexus")

        self.minio_endpoint = get_env("MINIO_ENDPOINT", "http://127.0.0.1:9000")
        self.minio_bucket = get_env("MINIO_BUCKET", "docnexus")
        self.minio_access_key = get_env("MINIO_ACCESS_KEY", "")
        self.minio_secret_key = get_env("MINIO_SECRET_KEY", "")
        self.minio_secure = get_bool_env("MINIO_SECURE", False)

    # 函数功能：生成注册到 Nacos 的实例元数据。
    def nacos_metadata(self) -> dict:
        """返回可供 Nacos、网关和运维查看的服务实例元数据。"""
        return {
            "runtime": "python",
            "framework": "fastapi",
            "env": self.app_env,
            "version": self.app_version,
            "scheme": "http",
            "health.path": "/api/agent/health",
            "gateway.path": "/api/agent/**",
        }

    # 函数功能：生成脱敏后的启动配置摘要。
    def startup_summary(self) -> dict:
        """返回启动日志摘要，避免打印数据库密码和对象存储密钥。"""
        return {
            "service": f"{self.service_name} {self.service_ip}:{self.server_port}",
            "nacos": self.nacos_server_addr if self.nacos_enabled else "disabled",
            "redis": f"{self.redis_host}:{self.redis_port}/{self.redis_database}",
            "mysql": f"{self.mysql_host}:{self.mysql_port}/{self.mysql_database}",
            "rocketmq": self.rocketmq_namesrv_addr,
            "minio": f"{self.minio_endpoint}/{self.minio_bucket}",
        }


settings = AppSettings()
ServiceConfig = AppSettings
