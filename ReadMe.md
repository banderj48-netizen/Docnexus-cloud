# 文枢智能 DocNexus Cloud

文枢智能 DocNexus Cloud 是一个面向“资料上传、知识理解、AI 学习交流、文档生成与交付”的微服务工作台。项目当前已经从单体思路升级为前后端分离、多服务拆分、Gateway 统一入口、Redis 实时安全态、RocketMQ 异步事件和 MySQL 最终事实数据的架构。

目标业务闭环：

```text
上传资料
 -> AI 解析并理解资料
 -> 构建知识库和可引用片段
 -> 用户与 AI 基于资料交流学习
 -> AI 根据资料生成或修改 Word / PPT
 -> 审阅引用、格式和风险
 -> 导出最终交付文件
```

## 一、当前架构

```text
Vue 前端
  |
  | /api
  v
docnexus-gateway-service
  |-- JWT/JWKS 验签
  |-- Redis 实时鉴权
  |-- CORS
  |-- Redis 分布式限流
  |-- Sentinel 网关保护
  |-- 可信用户头注入
  |-- Gateway 审计与安全告警事件
  |
  |-- docnexus-user-service
  |-- docnexus-file-service
  |-- docnexus-log-service
  |-- docnexus-document-service
```

基础设施：

```text
MySQL：用户、会话、文件、上传、任务、日志等最终事实数据
Redis：登录态、黑名单、tokenVersion、在线状态、缓存、锁、限流、JWKS 二级缓存
RocketMQ：Gateway 审计、安全告警、用户会话事件、业务操作日志、文件事件
Nacos：服务注册发现
MinIO：原始文件、临时分片和后续生成文件对象存储
Sentinel：Gateway 路由级保护、限流、阻断与降级扩展
```

## 二、模块拆分

| 模块 | 定位 | 当前功能 |
| --- | --- | --- |
| `frontend` | 正式 Vue 前端 | 登录注册、工作台、文档库、账号中心、用户日志、真实后端请求 |
| `new_front` | 静态演示前端 | 不依赖后端的原项目界面还原和页面跳转演示 |
| `docnexus-gateway-service` | API 网关 | 路由、鉴权、JWKS 多级缓存、可信头注入、CORS、Redis 限流、Sentinel、审计事件 |
| `docnexus-user-service` | 用户与认证服务 | 注册、登录、refresh、退出、资料、改密、会话、JWKS、Redis 安全态、用户事件 |
| `docnexus-file-service` | 文件服务 | 普通上传、分片上传、下载、预览、删除、MinIO、文件列表缓存、上传恢复 |
| `docnexus-log-service` | 日志服务 | 消费 Gateway/业务日志事件，写入 MySQL，提供用户日志与管理员日志查询接口 |
| `docnexus-document-service` | 文档服务 | 当前为服务骨架，后续承接解析、生成、交付任务 |
| `docnexus-common` | 公共模块 | 统一返回、分页、异常、常量、MQ Topic、事件 DTO、业务日志注解 |
| `docnexus-api` | 接口契约预留 | 后续沉淀 Feign/Client/DTO 契约 |
| `docs/sql` | 数据库脚本 | 初始化用户、会话、文件、日志、索引和迁移脚本 |

## 三、技术栈

### 后端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Java | 17 | 后端服务运行和编译版本 |
| Spring Boot | 3.5.14 | 微服务基础框架、Web、Actuator、配置管理 |
| Spring Cloud | 2025.0.1 | Gateway、LoadBalancer、服务治理基础 |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos Discovery、Sentinel Starter |
| Spring Cloud Gateway WebFlux | 随 Spring Cloud | 统一 API 网关、响应式路由转发 |
| Spring Security | 随 Spring Boot | Gateway WebFlux 安全基础配置，禁用默认 Session 认证 |
| MyBatis Spring Boot Starter | 3.0.5 | XML SQL、Mapper、数据库访问 |
| MySQL Connector/J | 随 Spring Boot | 连接 MySQL 业务库 |
| Redis / Lettuce | 随 Spring Boot | 登录态、缓存、锁、限流、JWKS 二级缓存 |
| RocketMQ Spring Boot Starter | 2.3.5 | 异步事件、日志投递、会话最终一致 |
| Sentinel | 1.8.8 | Gateway 路由级限流、阻断、降级扩展 |
| Caffeine | 3.2.3 | Gateway L1 鉴权缓存、JWKS 本地 kid 缓存 |
| MinIO Java SDK | 8.6.0 | 对象存储、分片临时对象和正式文件对象 |
| OkHttp JVM | 5.1.0 | MinIO SDK 编译和运行依赖 |
| java-jwt | 4.4.0 | UserService 签发 JWT，Gateway 验签 JWT |
| Spring Security Crypto | 随 Spring Boot | BCrypt 密码加密与校验 |
| Hutool | 5.8.36 | 工具类能力补充 |
| gRPC Netty | 1.53.0 | Gateway 相关依赖兼容 |

### 前端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Vue | 3.5.25 | 单页应用框架 |
| Vite | 4.5.3 | 前端开发服务器和构建工具 |
| Vue Router | 5.0.2 | 页面路由、守卫和登录跳转 |
| Element Plus | 2.14.0 | 表单、按钮、分页、消息等基础 UI |
| Element Plus Icons | 2.3.2 | 导航和操作图标 |
| Axios | 1.13.5 | 统一请求 Gateway |
| DOMPurify | 3.4.5 | 富文本和 Markdown 内容安全净化 |
| markdown-it | 14.1.1 | Markdown 渲染 |
| KaTeX | 0.16.47 | 数学公式渲染预留 |
| STOMP / SockJS | 7.3.0 / 1.6.1 | 后续任务进度和消息推送预留 |

## 四、数据库设计

数据库脚本位于 `docs/sql`，当前以 MySQL 8.x 为目标。建议新库按以下顺序执行：

```text
docs/sql/docnexus_cloud_init.sql
docs/sql/file_service_upload_v1.sql
docs/sql/log_service_v1.sql
docs/sql/auth_session_fix.sql
docs/sql/auth_session_mq_update.sql
docs/sql/auth_session_performance_indexes.sql
docs/sql/auth_session_device_takeover_indexes.sql
```

### 用户与认证

`user_account`：用户账号事实表。

核心字段包括 `id`、`username`、`password`、`email`、`phone`、`role`、`status`、`token_version`、`create_time`、`update_time`。当前代码已提供 BCrypt 配置，目标密码存储方案是 BCrypt；历史数据可通过登录兼容迁移逐步升级。

`user_session`：登录会话事实表。

核心字段包括 `session_id`、`user_id`、`refresh_token_hash`、`access_jti`、`token_version`、`device_id`、`device_name`、`client_ip`、`user_agent`、`status`、`online_status`、`login_at`、`last_active_at`、`access_expires_at`、`refresh_expires_at`、`logout_at`、`offline_at`、`expired_at`、`close_reason`。Redis 负责实时在线态和鉴权态，MySQL 保留最终事实和审计快照。

### 文件与上传

`document_file`：上传成功后的正式文件元数据表。

核心字段包括 `file_id`、`user_id`、`knowledge_base_id`、`original_name`、`file_category`、`file_ext`、`mime_type`、`file_size`、`file_sha256`、`storage_type`、`bucket_name`、`object_key`、`upload_status`、`parse_status`、`index_status`、`graph_status`、`summary`、`keywords_json`、`error_message`、`deleted`。

`file_upload_session`：上传过程会话表。

用于记录普通上传和分片上传的过程状态，核心字段包括 `upload_id`、`user_id`、`file_id`、`file_name`、`file_size`、`chunk_size`、`total_chunks`、`uploaded_chunks`、`status`、`temp_bucket_name`、`temp_prefix`、`expires_at`、`error_message`。

`file_upload_chunk`：分片上传状态表。

以 `upload_id + chunk_index` 做唯一约束，记录每个分片的大小、哈希、临时 bucket、object key 和状态。

`document_process_task`：文档处理任务表。

上传成功后创建解析任务占位，核心字段包括 `task_id`、`file_id`、`user_id`、`task_type`、`task_status`、`stage`、`progress`、`retry_count`、`payload_json`、`error_message`、`started_at`、`finished_at`。

### 知识图谱预留

`knowledge_graph_node` 和 `knowledge_graph_edge`：第一阶段作为前端知识图谱概览缓存表，后续复杂图查询可迁移到 Neo4j 或图数据库。

### 日志与 MQ

`gateway_audit_log`：记录所有经过 Gateway 的请求元数据。

核心字段包括 `event_id`、`request_id`、`trace_id`、`user_id`、`username`、`client_ip`、`method`、`path`、`route_id`、`target_service`、`request_kind`、`status_code`、`user_agent`、`error_message`、`occurred_at`。该表不记录请求耗时，耗时由业务日志和 MQ 消费日志承担。

`security_alert_log`：记录无 Token、无效 Token、伪造身份头、限流、Sentinel 阻断等安全事件。

核心字段包括 `event_id`、`request_id`、`trace_id`、`alert_type`、`alert_level`、`user_id`、`client_ip`、`method`、`path`、`message`、`detail_json`、`handled`、`handled_at`、`occurred_at`。

`mq_consume_log`：MQ 消费幂等与耗时表。

使用 `event_id + consumer_group` 唯一索引保证幂等，记录 `topic`、`tag`、`business_key`、`consume_status`、`retry_count`、`consume_started_at`、`consume_finished_at`、`duration_ms`、`error_message`。

`event_outbox`：可靠事件投递表。

用于后续关键事件 Outbox 模式，核心字段包括 `event_id`、`trace_id`、`topic`、`tag`、`business_key`、`payload_json`、`event_status`、`retry_count`、`next_retry_at`、`last_sent_at`、`error_message`。

`business_operation_log`：统一业务操作耗时日志表。

记录用户主动操作、自动查询、MQ 消费、系统任务和内部调用。核心字段包括 `event_id`、`request_id`、`trace_id`、`user_id`、`username`、`source_service`、`module`、`function_name`、`operation_type`、`operation_name`、`trigger_type`、`operation_source`、`user_visible`、`business_key`、`success`、`alert_message`、`request_headers_json`、`method`、`path`、`client_ip`、`user_agent`、`occurred_at`、`completed_at`、`duration_ms`。普通用户日志页只展示最近 5 天、`trigger_type=USER_ACTION`、`user_visible=1` 的记录。

## 五、模块完整实现

### 1. Gateway 服务

Gateway 是唯一公网入口，当前已实现：

```text
Spring Cloud Gateway WebFlux 路由
Nacos 服务发现和 lb:// 服务转发
Spring Security WebFlux 基础安全配置
禁用 CSRF、formLogin、httpBasic、默认 logout
白名单路径放行
JWT accessToken 验签
JWKS 多级缓存：本地快照 -> Caffeine -> Redis -> UserService JWKS
未知 kid 限频远程刷新
fallback 公钥证书兜底
Redis MGET 校验 blacklist / auth session / tokenVersion
Caffeine L1 鉴权缓存
强校验路径绕过 L1 缓存
删除客户端伪造的身份头
注入可信 X-User-*、X-Access-Jti、X-Client-IP、X-Request-Id、X-Trace-Id
注入 X-Gateway-Timestamp 和 X-Gateway-Signature
CORS 白名单配置
Redis 分布式限流，默认关闭
Sentinel Gateway block JSON 处理
Gateway 请求审计和安全告警 RocketMQ 投递
```

Gateway 不持有 JWT 私钥。accessToken 由 UserService 签发，Gateway 通过 JWKS 公钥集验签。refresh 入口经过 Gateway，但实际 refreshToken 校验和新 Token 签发仍由 UserService 完成。

### 2. UserService 用户服务

UserService 当前已实现：

```text
用户注册
用户登录
找回密码验证
重置密码
refreshToken 刷新登录态
退出登录
当前用户资料查询
当前用户资料修改
当前用户资料缓存清理
修改密码并重新签发登录态
会话列表分页
会话 heartbeat
退出指定设备
统一异常响应
登录 IP 限流和用户名失败锁定
同设备会话接管
Redis 实时登录态和黑名单
tokenVersion 批量失效旧 Token
RocketMQ 会话失效、离线事件
多实例 JWT 签发：keyId + 共享受管私钥
内部 JWKS 公钥接口：/internal/auth/jwks
Controller 层 AOP 业务操作日志
```

登录态模型：

```text
accessToken：JWT，放在 Authorization: Bearer
refreshToken：高熵随机串，只返回前端，MySQL 保存 SHA-256 hash
sessionId：刷新和会话管理的稳定业务 ID
accessJti：JWT jti，Gateway 使用它查询 Redis auth session
tokenVersion：修改密码、重置密码、封号后递增，旧 Token 立即失效
```

多实例密钥模型：

```text
所有 UserService 实例加载同一套受管私钥
JWT Header 写入 kid
Gateway 按 kid 从 JWKS 公钥集中选择验签公钥
新旧 kid 可以在轮换期共存
Gateway JWKS 本地缓存过期或遇到未知 kid 时刷新
```

### 3. FileService 文件服务

FileService 当前已打通第一阶段文件闭环：

```text
查询已上传文档列表
普通文件上传
分片上传初始化
分片上传
分片合并
上传状态查询
失败上传清理
可恢复上传会话查询
下载文件
预览文件
删除文件
MinIO 原始文件 bucket 和临时分片 bucket
MySQL 文件元数据、上传会话、分片状态、解析任务占位
Redis 文档库列表版本号缓存
空列表缓存防穿透
短锁回源防击穿
TTL 抖动防雪崩
上传完成、删除后主动失效缓存
上传中断和失败状态处理
过期上传清理任务
Controller 层 AOP 业务操作日志
```

当前规则：

```text
最大文件大小：默认 200MB
普通上传阈值：默认 100MB
分片大小：默认 10MB
正式文件只在 uploadStatus=UPLOADED 后进入文档库列表
上传失败项不写入 document_file
离开页面时单个文件上传请求不再被前端主动中断
```

### 4. LogService 日志服务

LogService 当前已实现：

```text
消费 docnexus_gateway_event:REQUEST_AUDIT
消费 docnexus_gateway_event:SECURITY_ALERT
消费 docnexus_gateway_event:RATE_LIMITED
消费 docnexus_gateway_event:SENTINEL_BLOCK
消费 docnexus_log_event:BUSINESS_OPERATION_LOG
写入 gateway_audit_log
写入 security_alert_log
写入 business_operation_log
写入 mq_consume_log 幂等与耗时
用户操作摘要 Redis 缓存
业务日志落库后按 userId 二次删除摘要缓存
普通用户最近 5 天主动操作分页查询
普通用户最近 5 天主动操作摘要查询
管理员网关审计日志分页查询
管理员安全告警分页查询和已处理标记
```

用户日志页只展示主动业务操作，不展示自动查询、MQ 消费、Gateway 原始审计和安全告警详情。AOP 注解加在 Controller 方法上，后端以注解的 `triggerType` 和 `userVisible` 判断是否展示给普通用户。

### 5. Common 公共模块

公共模块当前提供：

```text
ApiResponse<T>
PageResponse<T>
统一响应码
业务异常
Redis Key 工具
用户、登录、会话、文件、日志 DTO / VO
MQ Topic 和 Tag 常量
Gateway 审计事件 DTO
安全告警事件 DTO
业务操作日志事件 DTO
@BusinessOperationLog 注解
```

新增业务接口时优先复用公共模块，不要在各服务里重复硬编码 Topic、Tag、响应结构和日志事件结构。

### 6. Frontend 正式前端

正式前端当前实现：

```text
登录页
注册页
工作台首页
文档库页面
用户资料页
修改资料弹窗
修改密码弹窗
用户会话列表
用户日志页面
路由守卫
登录态校验
Axios 请求拦截和响应拦截
Token / refreshToken / sessionId 本地管理
真实后端数据请求
用户主动操作后清理日志统计缓存
```

用户日志页面已替代原 AI 日志页面，保留绿色风格。页面通过后端接口获取最近 5 天用户主动业务操作，饼图统计成功/失败数量和不同业务功能数量，列表支持成功/失败筛选与 10、20、50 条分页。

### 7. new_front 静态演示前端

`new_front` 是独立静态演示目录，用于在后端未部署时查看原项目页面形态和跳转。它通过本地 mock 返回数据，不作为真实联调入口。真实业务开发优先修改 `frontend`。

### 8. DocumentService 文档服务

文档服务当前是微服务骨架，已具备 Nacos 注册、健康检查和公共模块依赖。后续用于承接：

```text
文档解析任务
Word / PPT 生成任务
交付文件记录
任务进度查询
Python Agent 回调
生成物下载状态
```

## 六、配置示例

各服务只提交示例配置，真实配置复制后在本地填写：

```text
docnexus-gateway-service/src/main/resources/application.example.yml
docnexus-user-service/src/main/resources/application.example.yml
docnexus-file-service/src/main/resources/application.example.yml
docnexus-log-service/src/main/resources/application.example.yml
docnexus-document-service/src/main/resources/application.example.yml
docnexus-common/src/main/resources/application.example.yml
docnexus-api/src/main/resources/application.example.properties
frontend/.env.example
```

关键配置项：

```text
MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
NACOS_SERVER_ADDR / NACOS_USERNAME / NACOS_PASSWORD
ROCKETMQ_NAMESRV_ADDR
MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY
JWT_ISSUER / JWT_KEY_ID
JWT_PRIVATE_KEYSTORE_LOCATION / JWT_PRIVATE_STORE_PASSWORD
JWT_JWKS_URI / JWT_JWKS_CACHE_TTL_SECONDS
GATEWAY_INTERNAL_SIGN_SECRET
GATEWAY_CORS_ENABLED
GATEWAY_REDIS_RATE_LIMIT_ENABLED
GATEWAY_SENTINEL_ENABLED
VITE_API_TARGET
```

## 七、本地运行

后端编译：

```powershell
cd D:\DocAI\My_DocAI_Cloud
mvn.cmd -DskipTests compile
```

单服务启动示例：

```powershell
mvn.cmd -pl docnexus-user-service -am spring-boot:run
mvn.cmd -pl docnexus-file-service -am spring-boot:run
mvn.cmd -pl docnexus-log-service -am spring-boot:run
mvn.cmd -pl docnexus-gateway-service -am spring-boot:run
```

正式前端：

```powershell
cd D:\DocAI\My_DocAI_Cloud\frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5181
```

静态演示前端：

```powershell
cd D:\DocAI\My_DocAI_Cloud\new_front
npm install
npm run dev -- --host 127.0.0.1 --port 5179
```

## 八、当前已实现功能清单

```text
1. 微服务父工程和统一依赖管理。
2. Gateway 统一入口、路由、CORS、JWT/JWKS 验签、Redis 实时鉴权。
3. Gateway Caffeine 本地鉴权缓存和 Redis 分布式限流。
4. Gateway Sentinel 接入和统一阻断响应。
5. Gateway 请求审计、安全告警、限流告警事件投递。
6. UserService 注册、登录、refresh、退出、资料、改密、会话管理。
7. UserService 多实例 JWT 签发和 JWKS 公钥接口。
8. UserService Redis 登录态、黑名单、tokenVersion、presence、heartbeat。
9. UserService RocketMQ 会话事件异步落库。
10. FileService 普通上传、分片上传、下载、预览、删除。
11. FileService MinIO 对象存储和 MySQL 文件元数据。
12. FileService Redis 文件列表缓存，并处理穿透、击穿、雪崩。
13. FileService 上传失败、中断、可恢复上传会话和过期清理。
14. LogService Gateway 审计、安全告警、业务操作、MQ 消费日志落库。
15. LogService 普通用户最近 5 天主动业务操作统计和分页查询。
16. Controller AOP 记录用户主动操作、自动查询、MQ 消费和系统任务耗时。
17. 前端接入真实后端登录、用户资料、文档库、用户日志。
18. 用户修改资料和修改密码前端无变化不发请求。
19. 用户主动业务操作后清理日志统计缓存，LogService 落库后再次失效缓存。
20. new_front 提供无后端静态演示页面。
```

## 九、后续目标

近期目标：

```text
1. 完善下游服务 X-Gateway-Signature 拦截校验。
2. 把 UserService 历史密码数据完整迁移到 BCrypt。
3. 完成 RocketMQ 关键业务事件 Outbox 补偿任务。
4. 增加 DLQ 查看、重投、忽略和人工处理能力。
5. 完善 FileService 文件解析事件，上传成功后异步创建解析任务。
6. 完善 DocumentService 文档解析、生成和交付任务接口。
```

中期目标：

```text
1. 拆出独立 Auth-Service，UserService 专注用户资料和账号域。
2. 接入 Python Agent，完成文档解析、切片、RAG 检索和摘要生成。
3. 接入 Word / PPT 生成与修改能力。
4. 建立知识库、学习室、文档工厂和交付中心的真实业务闭环。
5. 建立管理员日志后台，区分普通用户日志和管理员审计日志。
6. 接入链路追踪、指标监控、日志检索和告警面板。
```

长期目标：

```text
1. 多租户空间和项目权限模型。
2. 分库分表或一服务一库。
3. Redis、RocketMQ、MinIO、MySQL 的生产级高可用部署。
4. KMS/HSM 托管 JWT 私钥和密钥轮换。
5. Agent 工具编排、引用审阅、格式审查和交付质量评估。
6. 企业级部署脚本、容器化、灰度发布和自动化压测。
```
