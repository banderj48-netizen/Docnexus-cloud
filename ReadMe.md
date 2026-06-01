# 文枢智能 DocNexus Cloud

## 项目定位

文枢智能 DocNexus Cloud 是一个面向资料理解、知识检索、AI 学习交流、文档生成与交付的微服务工作台。

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

当前仓库采用前后端分离和微服务架构。前端使用 Vue，后端使用 Spring Cloud Alibaba，AI 能力预留 Python Agent 服务。

## 总体架构规划

```text
frontend
  Vue 企业级工作台界面

docnexus-gateway-service
  API 网关、路由转发、JWT 验签、Redis 鉴权、Caffeine 本地鉴权缓存

docnexus-user-service
  用户注册、登录、刷新、退出、资料管理、密码管理、会话管理

docnexus-file-service
  文件上传、对象存储、文件元数据、下载与删除能力规划

docnexus-document-service
  文档任务、生成记录、交付文件状态能力规划

docnexus-ai-service
  Python Agent、RAG、文档解析、Office 生成与大模型调用能力规划

docnexus-common
  公共 DTO、VO、常量、异常、Redis Key、上下文工具

docnexus-api
  后续服务间接口契约预留模块
```

基础设施规划：

```text
MySQL：用户、会话、文件、任务、聊天、文档元数据等最终业务数据
Redis：登录态、黑名单、tokenVersion、在线状态、热点缓存、限流
RocketMQ：会话失效、离线落库、文件解析、文档生成等异步事件
Nacos：服务注册发现与后续配置管理
MinIO：原始文件、解析结果、生成文档和导出文件
Gateway：统一入口、鉴权、路由、跨域和后续限流
```

## 当前已实现能力

### 微服务父工程

父工程已经包含：

```text
docnexus-common
docnexus-gateway-service
docnexus-user-service
docnexus-file-service
docnexus-document-service
docnexus-api
```

父工程统一管理 Spring Boot、Spring Cloud、Spring Cloud Alibaba、MyBatis、RocketMQ、Caffeine 等依赖版本，子模块尽量不重复写版本。

### 网关服务

网关已实现：

```text
Nacos 服务发现
/api/auth/** 和 /api/users/** 转发到 user-service
/api/files/** 转发到 file-service
/api/documents/** 转发到 document-service
JWT 公钥验签
Redis MGET 校验 blacklist / auth session / tokenVersion
Caffeine L1 本地鉴权缓存
强校验路径绕过本地缓存
可信用户头注入：X-User-Id / X-Username / X-User-Role / X-Access-Jti
可信客户端 IP 注入
```

普通接口鉴权链路：

```text
请求进入 Gateway
 -> 白名单直接放行
 -> 普通接口查 Caffeine 本地缓存
 -> 命中后 0 Redis、0 JWT 验签
 -> 未命中时 JWT 验签
 -> Redis MGET 校验登录态
 -> 校验成功写入 Caffeine，TTL 默认 3 秒
 -> 注入可信用户头并转发
```

这个设计目标是在单 Gateway 实例上支撑 5000 RPS 级别普通接口鉴权。高风险接口仍每次查 Redis，优先保证安全。

### 用户服务

用户服务已实现：

```text
注册
登录
找回密码校验
重置密码
修改密码
刷新 accessToken
主动退出登录
当前用户资料查询和修改
当前用户会话列表
当前会话 heartbeat
指定会话退出
统一异常响应
登录 IP 限流和用户名失败次数锁定
```

认证模型：

```text
accessToken：JWT，前端访问接口时携带
auth:session:{jti}：Redis 实时登录态，Gateway 鉴权使用
auth:blacklist:{jti}：accessToken 黑名单，退出和接管后立即生效
auth:session:revoked:{sessionId}：refreshToken 级会话黑名单，防止退出后旧 refreshToken 续签
refreshToken：只返回前端，数据库只保存 SHA-256 hash
tokenVersion：修改密码、重置密码、封号等场景批量失效旧 token
user_session：MySQL 会话最终事实和审计记录
```

会话能力：

```text
同一用户可以多设备在线
同一 userId + deviceId 只保留一个 ACTIVE 会话
同设备新浏览器登录会接管旧 session
旧 accessJti 立即进入 Redis blacklist
退出、过期、改密码通过 RocketMQ 异步更新 MySQL
heartbeat 只写 Redis presence，不高频写 MySQL
会话列表优先使用 Redis 版本号分页缓存
```

退出登录时会立即处理：

```text
DEL auth:session:{accessJti}
PSETEX auth:blacklist:{accessJti}
PSETEX auth:session:revoked:{sessionId}
DEL auth:presence:{sessionId}
ZREM auth:presence:lastseen sessionId
DEL auth:heartbeat:session:{sessionId}
DEL auth:user:profile:{userId}
DEL auth:user:login:{username}
INCR auth:user:sessions:version:{userId}
```

Redis 组合操作使用 Lua 保证原子性。MySQL 由 RocketMQ Consumer 幂等落库，降低高并发退出时的数据库写入压力。

### 前端

前端已具备工作台雏形：

```text
登录 / 注册
工作台
知识库页面
学习室
文档工厂
交付中心
AI 日志
AI 待办
账号中心
路由守卫和登录态校验
同设备接管提示
```

前端统一通过 `/api` 请求网关，不直接访问内部微服务。

### Python Agent 服务

`docnexus-ai-service` 当前是 Python 服务骨架，已经预留：

```text
服务配置
FastAPI 入口
Nacos 注册逻辑
pyproject.toml / uv.lock
```

后续用于承载 RAG、文档解析、Word/PPT 生成、Agent 工具编排和大模型调用。

## 配置与敏感文件说明

真实配置文件不提交到 GitHub，仓库只提交示例文件。

使用方式：

```text
复制 application.example.yml 为 application.yml
复制 application.example.properties 为 application.properties
按自己的环境填写 MySQL、Redis、Nacos、RocketMQ、JWT 等配置
```

JWT 私钥库、公钥证书也不提交：

```text
docnexus-user-service/src/main/resources/jwt-private.p12
docnexus-gateway-service/src/main/resources/jwt-public.cer
```

仓库中提供占位说明：

```text
docnexus-user-service/src/main/resources/jwt-private.example.txt
docnexus-gateway-service/src/main/resources/jwt-public.example.txt
```

## 本地运行参考

前置依赖：

```text
JDK 17
Maven 3.9+
Node.js 18+
MySQL
Redis
Nacos
RocketMQ
```

后端编译：

```powershell
cd D:\DocAI\My_DocAI_Cloud
mvn.cmd -pl docnexus-user-service -am -DskipTests compile
mvn.cmd -pl docnexus-gateway-service -am -DskipTests compile
```

前端运行：

```powershell
cd D:\DocAI\My_DocAI_Cloud\frontend
npm install
npm run dev
```

Python Agent 运行：

```powershell
cd D:\DocAI\My_DocAI_Cloud\docnexus-ai-service
uv sync
uv run python -m app.main
```

## 后续规划

### 文件服务

```text
接入 MinIO
实现文件上传、下载、删除
保存文件元数据
实现用户文件权限隔离
为解析任务预留 parse_status / index_status
```

### 文档任务服务

```text
建立文档生成任务表
接收 AI Agent 生成结果
维护任务状态、进度、失败原因
支持 Word / PPT 交付文件下载
```

### AI Agent

```text
文档解析
文本切片
向量化
RAG 检索
学习问答
Word 改写
PPT 生成
引用审阅
```

### 企业级增强

```text
密码存储升级为 BCrypt
RocketMQ Outbox 事件表兜底
Gateway 本地缓存广播失效
Redis 拆分认证库和普通缓存库
MySQL 主从和读写分离
审计日志和管理员强制下线
接口级权限模型
压测和可观测性面板
```

## 当前状态

当前阶段已经完成微服务父工程、网关服务和用户服务的核心能力。项目还处于持续开发期，文件、文档、AI 能力仍在规划和骨架阶段。

