# 文枢智能 DocNexus Cloud

DocNexus Cloud 是一个面向资料上传、文档解析、知识理解、AI 辅助写作和文档交付的微服务工作台。当前项目已经形成前后端分离、Gateway 统一入口、Redis 实时状态、RocketMQ 异步事件、MinIO 对象存储和 MySQL 最终事实数据的基础架构。

当前重点能力：

```text
用户登录与多会话
 -> 文档库上传资料
 -> MinIO 保存原始文件
 -> 用户手动触发解析任务
 -> RocketMQ 异步排队
 -> OnlyOffice 原格式在线编辑
 -> 保存后覆盖当前 MinIO 对象
 -> 用户日志可追踪主动操作
```

当前开发状态：

```text
正在开发 AI 相关能力，目前正在完整知识库构建。
重点推进文档解析、上传元信息、父子分块、混合检索、知识图谱和 Agent 全链路追踪。
```

## 一、项目结构

| 目录 | 说明 |
| --- | --- |
| `frontend` | 正式 Vue 前端，连接真实 Gateway 和微服务 |
| `new_front` | 只读参考/静态演示前端，不作为真实联调入口 |
| `docnexus-gateway-service` | API 网关，负责路由、JWT/JWKS 验签、限流、审计、可信请求头注入 |
| `docnexus-user-service` | 用户、登录、refreshToken、多会话、在线状态、JWKS 公钥接口 |
| `docnexus-file-service` | 文件上传、分片续传、MinIO、文档库、OnlyOffice、手动解析请求 |
| `docnexus-log-service` | Gateway 审计、业务操作日志、MQ 消费日志、用户可见日志查询 |
| `docnexus-document-service` | 文档任务服务骨架，后续承接生成、交付和任务编排 |
| `docnexus-common` | 统一响应、异常、常量、MQ Topic、日志注解和公共 DTO |
| `docnexus-api` | 服务间接口契约预留 |
| `docnexus-ai-service` | Python AI 服务骨架，后续承接解析、RAG、Agent 和文档生成 |
| `deploy` | 基础设施部署示例，例如 OnlyOffice Document Server |
| `docs/sql` | MySQL 初始化和迁移脚本 |

## 二、技术栈

后端：

```text
Java 17
Spring Boot 3.5.x
Spring Cloud 2025.x
Spring Cloud Alibaba / Nacos / Sentinel
Spring Cloud Gateway WebFlux
MyBatis
MySQL 8.x
Redis
RocketMQ
MinIO Java SDK
OnlyOffice Docs / Document Server
```

前端：

```text
Vue 3
Vite
Vue Router
Element Plus
Axios
DOMPurify
OnlyOffice DocsAPI 前端嵌入
```

## 三、核心功能

### 1. 认证与网关

- Gateway 是统一 API 入口，所有前端请求默认走 `/api/**`。
- UserService 签发 JWT，Gateway 通过 JWKS 公钥验签，不持有私钥。
- Gateway 会删除客户端伪造的身份头，并注入可信的 `X-User-Id`、`X-Access-Jti`、`X-Trace-Id` 等请求头。
- Redis 保存 accessToken 登录态、黑名单、tokenVersion、presence 和 heartbeat 状态。
- 支持同设备会话接管、多会话在线状态、refreshToken 刷新、退出指定设备。
- Gateway 审计、安全告警和业务日志通过 RocketMQ 异步写入 LogService。

### 2. 文档库与上传

- 文档库标题统一为“文档库”，只展示真实 `document_file` 记录。
- 上传后只显示“已上传”，不会默认解析，也不会默认加入知识库。
- 上传格式当前支持：`pdf`、`txt`、`docx`、`pptx`。历史扩展格式可保留元数据，但进入在线编辑前需要后续转换能力。
- 当前安全策略不支持图片、视频、Excel、外链 URL、图床 URL 或远程链接导入。
- 小于 `5MB` 的文件直传；大于等于 `5MB` 的文件分片上传，分片大小默认 `10MB`，必须满足 MinIO/S3 非末尾分片不小于 `5MiB`。
- 分片上传支持断点续传，上传会话和已上传分片记录在 MySQL，临时状态写入 Redis。
- 上传失败后前端提供重新上传和移除；继续添加文件时会清理失败会话、Redis 临时项和 MinIO 临时分片。

### 3. 手动解析

- 用户点击文档卡片三点菜单中的“解析”后，才创建解析任务并发送 MQ。
- 解析请求使用 `userId + fileId` 做权限隔离，并使用 Redis 锁避免同一文件重复提交。
- `PENDING / PROCESSING / SUCCESS` 状态下不允许重复解析。
- 解析失败后只允许一次重新解析，再次失败后前端提示“请稍后再试”。
- 解析 MQ Consumer 默认关闭，后续 AI 服务解析接口完成后再开启。
- Consumer 预留 OpenFeign 调用 AI 服务 `POST /api/agent/documents/parse`，并配置 RocketMQ 失败重试和死信队列。

### 4. OnlyOffice 原格式编辑

- `docx / pptx / txt` 使用 OnlyOffice Docs 原格式在线打开、编辑和保存。
- `pdf` 当前只提供只读预览，不展示保存入口。
- 文档编辑页左侧为 OnlyOffice 编辑区，右侧 AI 灵感助理默认展开，支持收起和拖拽调整宽度。
- 点击“手动保存”时，前端先判断是否有编辑动作：
  - 未编辑：提示“用户未更改，无需保存”。
  - 已编辑：调用后端 forcesave，等待 OnlyOffice callback 确认保存完成后提示“已保存成功”。
- 保存成功后覆盖写回 `document_file.bucket_name/object_key` 指向的原 MinIO 对象，不生成新的对象地址。
- `current_version` 保留为乐观锁版本号，保存 SQL 必须带 `user_id + file_id + current_version + deleted=0` 条件，成功后递增版本号。
- 下载始终下载当前 MinIO 对象；如果页面存在未保存修改，前端提示“检测到当前文档有更新，请保存后再下载”。

### 5. 缓存与日志

- 文件列表、单文件元数据、用户资料、会话列表等均有 Redis 缓存。
- 打开编辑页、下载、预览、OnlyOffice config/source 等首次查询文件元数据后，会写入单文件缓存。
- 写操作成功后刷新对应单文件缓存。
- 只有 UserService 判定“某用户所有会话都离线”后，才发送离线事件；FileService 消费后用 Lua 原子删除该用户登记过的文件缓存。
- 用户点击上传、下载、删除、打开编辑页、解析、重新解析、保存等操作都会记录用户可见业务日志。
- 普通用户日志页只展示最近 5 天、`trigger_type=USER_ACTION` 且 `user_visible=1` 的业务操作。

## 四、配置示例

本仓库只上传示例配置，真实配置必须复制后本地填写，并通过环境变量、配置中心或 Secret 注入。

允许上传的示例文件：

```text
frontend/.env.example
docnexus-ai-service/.env.example
docnexus-user-service/src/main/resources/application.example.yml
docnexus-gateway-service/src/main/resources/application.example.yml
docnexus-file-service/src/main/resources/application.example.yml
docnexus-log-service/src/main/resources/application.example.yml
docnexus-document-service/src/main/resources/application.example.yml
docnexus-common/src/main/resources/application.example.yml
docnexus-api/src/main/resources/application.example.properties
```

禁止上传的真实配置和敏感文件：

```text
application.yml / application.yaml / application.properties
.env / .env.local / .env.*
JWT 私钥、公钥证书、keystore
MinIO 密钥
MySQL / Redis / Nacos / RocketMQ 真实密码或公网地址
OpenAI 或其它模型 API Key
*.pem / *.p12 / *.jks / *.key / *.crt / *.cer
```

关键环境变量示例：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
NACOS_SERVER_ADDR
NACOS_USERNAME
NACOS_PASSWORD
ROCKETMQ_NAMESRV_ADDR
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
JWT_ISSUER
JWT_KEY_ID
JWT_PRIVATE_KEYSTORE_LOCATION
JWT_PRIVATE_STORE_PASSWORD
JWT_PRIVATE_KEY_PASSWORD
JWT_JWKS_URI
GATEWAY_INTERNAL_SIGN_SECRET
ONLYOFFICE_PUBLIC_URL
ONLYOFFICE_INTERNAL_URL
ONLYOFFICE_CALLBACK_BASE_URL
ONLYOFFICE_JWT_SECRET
FILE_INTERNAL_CALLBACK_TOKEN
AI_SERVICE_BASE_URL
FILE_PARSE_CONSUMER_ENABLED
VITE_API_TARGET
```

## 五、OnlyOffice 部署提示

OnlyOffice 部署示例位于：

```text
deploy/onlyoffice/docker-compose.yml
```

需要保证三类地址配置正确：

```text
ONLYOFFICE_PUBLIC_URL：浏览器访问 Document Server 的地址
ONLYOFFICE_INTERNAL_URL：FileService 调用 OnlyOffice Command Service 的地址
ONLYOFFICE_CALLBACK_BASE_URL：OnlyOffice 回调 Gateway/FileService 的地址
```

OnlyOffice 的 JWT Secret 必须和 Document Server 配置一致。中文字体乱码或版式偏移通常不是前端 CSS 问题，需要在 Document Server 所在机器安装文档使用的字体，并刷新字体缓存和 OnlyOffice 字体索引。

OnlyOffice Document Server Community 版采用 AGPL-3.0，生产使用前需要确认许可证、品牌展示和并发连接限制符合使用场景。

## 六、数据库脚本

新库建议执行：

```text
docs/sql/docnexus_cloud_init.sql
docs/sql/file_service_upload_v1.sql
docs/sql/log_service_v1.sql
docs/sql/auth_session_fix.sql
docs/sql/auth_session_mq_update.sql
docs/sql/auth_session_performance_indexes.sql
docs/sql/auth_session_device_takeover_indexes.sql
```

如果开发库中文件服务相关表被手动删除，可以执行：

```text
docs/sql/file_service_rebuild_current.sql
```

该脚本只使用 `CREATE TABLE IF NOT EXISTS`，不主动删除已有数据。

## 七、本地运行

后端编译：

```powershell
cd D:\DocAI\My_DocAI_Cloud
mvn.cmd -DskipTests compile
```

启动单个服务：

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
npm run dev -- --host 127.0.0.1 --port 5173
```

浏览器访问：

```text
http://127.0.0.1:5173
```

## 八、GitHub 上传规则

上传仓库前必须确认：

```text
只上传根目录 ReadMe.md，其它 Markdown 文档默认不上传
只上传 application.example.* 和 .env.example
不上传真实配置、密钥、证书、日志、运行时文件、构建产物和依赖目录
不上传 target、dist、node_modules、.venv、__pycache__
不上传 new_front，当前正式前端只使用 frontend
不上传本地 OnlyOffice 字体、MinIO 数据、RocketMQ 数据、Nacos 数据
```

上传前建议执行：

```powershell
git status --ignored --short
git ls-files "*.md"
git ls-files "*application.yml" "*application.properties" ".env*" "*.p12" "*.jks" "*.pem" "*.key" "*.crt" "*.cer"
git diff --cached --name-only
git diff --cached --check
```

如果误暂存了不该上传的文件，只能从索引移除，不删除本地文件：

```powershell
git restore --staged <path>
git rm --cached <path>
```

## 九、下一阶段计划

近期优先级：

```text
1. 完成下游服务 X-Gateway-Signature 拦截校验。
2. 完成 AI 服务文档解析接口，并开启 FileService 解析 MQ Consumer。
3. 增加解析 DLQ 查看、重投、忽略和人工处理页面。
4. 完善 OnlyOffice 字体部署、健康检查和保存链路监控。
5. 完成 DocumentService 文档生成、交付和引用审阅接口。
6. 将关键 MQ 事件升级为 Outbox 可靠投递。
```

中长期目标：

```text
多租户项目空间
RAG 检索和引用审阅
AI Agent 直接修改 Word / PPT
Office 文档生成与交付中心
生产级高可用部署
链路追踪、指标监控和告警
```
