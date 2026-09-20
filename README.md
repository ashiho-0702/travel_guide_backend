# AI 旅行规划小程序（后端）

文旅赛道竞赛作品。用户输入**目的地城市 / 开始日期 / 天数 / 人数 / 预算 / 兴趣偏好 / 体力 / 交通方式 / 其他需求**，AI 结合真实口碑（小红书、B 站等）与本地知识库，自动生成「每日行程 + 地图路线」攻略；到景点后支持**语音导游**（定位/拍照识别景点 → AI 讲解 → 语音问答）。

> 核心主题：**微信生态能力与 AI 能力有机结合**

## 功能特性

- **AI 攻略生成**：7 步工作流编排（校验 → RAG 检索 → Agent 搜集 → 生成 → 反思校验 → 地理编码 → 持久化+摄入），支持 SSE 流式输出
- **RAG 检索增强**：攻略与讲解双向量库，按城市/景点元数据过滤，生成结果反哺知识库（越用越聪明）
- **智能体（Agent）**：ReAct 循环 + Function Calling，自主调用「联网搜索 / 知识库检索 / 地理编码」三个工具
- **语音导游**：定位或拍照识别景点，AI 生成讲解词并语音播报；支持多轮语音问答（带会话记忆）
- **用户记忆**：记住历史偏好，个性化推荐
- **增长运营**：积分体系 + 等级会员 + 邀请裂变（签到/生成攻略/邀请得积分）
- **微信生态**：登录授权、分享、订阅消息（生成完成提醒）、小程序码触达
- **头像存储**：阿里云 OSS 存储，返回公网 URL

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 4.0.8（Java 17）+ MyBatis + MySQL + Redis |
| 前端 | 微信小程序（原生，独立仓库，由队友负责） |
| 大模型 | DeepSeek（生成 / Function Calling / 流式） |
| 向量检索 | Zilliz Cloud（托管 Milvus）+ 硅基流动 BAAI/bge-m3 embedding |
| 视觉识别 | 通义千问 qwen-vl-plus（拍照识景点） |
| 语音 | 百度智能云（ASR / TTS） |
| 搜索 / 地图 | 博查搜索、腾讯地图 WebService |
| 对象存储 | 阿里云 OSS |
| 微信 | 登录 code2session、订阅消息、小程序码 |

## 目录结构

```
miniProgram/
├── travel_guide/                 # Spring Boot 后端
│   ├── src/main/java/com/study/travel_guide/
│   │   ├── controller/           # 接口层（Auth/Trip/Guide/Voice/User/Admin）
│   │   ├── service/
│   │   │   ├── workflow/         # 攻略生成工作流（7 步）
│   │   │   ├── agent/            # 智能体（ReAct + 工具）
│   │   │   ├── rag/              # RAG（embedding/向量存储/检索/摄入）
│   │   │   ├── guide/            # 语音导游（识别/讲解/问答/知识库）
│   │   │   ├── voice/            # 百度 ASR/TTS
│   │   │   ├── wechat/           # 微信（access_token/订阅/小程序码）
│   │   │   ├── qwen/             # 通义千问识图
│   │   │   ├── memory/           # 用户记忆
│   │   │   └── growth/           # 增长运营（积分/签到/等级/邀请）
│   │   ├── mapper/               # MyBatis 数据访问
│   │   ├── config/               # 配置（JWT/CORS/OSS/RestClient）
│   │   └── common/               # 通用（Result/BizException/JwtUtil）
│   └── src/main/resources/
│       ├── application.yml        # 真实配置（已 gitignore）
│       ├── application.yml.example # 配置模板
│       └── db/schema.sql          # 建库建表脚本
└── docs/
    └── 功能与接口文档.md          # 完整接口文档（产品/前端/后端）
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 1. 配置

```bash
cd travel_guide
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

编辑 `application.yml`，填入各第三方服务的密钥（见下方「配置项说明」）。

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### 3. 启动

```bash
mvn spring-boot:run
# 或打包后运行
mvn clean package -DskipTests && java -jar target/travel_guide-0.0.1-SNAPSHOT.jar
```

启动后服务监听 `http://localhost:8080`。

### 4. （可选）灌入讲解知识库

首次使用语音导游前，调用一次种子接口，灌入 14 个著名景点的讲解素材：

```bash
curl -X POST http://localhost:8080/api/admin/seed-attractions \
  -H "Authorization: Bearer <token>"
```

## 配置项说明

所有配置在 `application.yml`，模板见 `application.yml.example`：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | MySQL 连接 |
| `spring.data.redis.*` | Redis 连接 |
| `deepseek.api-key` / `model` | DeepSeek 大模型 |
| `bocha.api-key` | 博查联网搜索 |
| `tencent.map-key` | 腾讯地图（地理编码/附近搜索） |
| `wechat.appid` / `secret` | 微信小程序 |
| `wechat.subscribe-template-id` | 订阅消息模板 ID |
| `jwt.secret` / `expire-hours` | JWT 签名密钥与有效期 |
| `vectorstore.*` | Zilliz 向量库 |
| `embedding.*` | 硅基流动 embedding |
| `workflow.reflect` | 反思校验开关（关掉省一次 LLM 调用） |
| `qwen.*` | 通义千问识图（base-url 需带业务空间 ID） |
| `baidu.api-key` / `secret-key` | 百度语音 ASR/TTS |
| `aliyun.oss.*` | 阿里云 OSS（头像存储，bucket 需公共读） |

## 接口概览

完整接口文档见 [docs/功能与接口文档.md](docs/功能与接口文档.md)，涵盖 21 个接口（登录 / 攻略 / 语音导游 / 语音 / 用户 / 增长 / 管理）。

核心接口：

| 模块 | 接口 |
|------|------|
| 认证 | `POST /api/auth/login` |
| 攻略 | `POST /api/trip/generate` · `POST /api/trip/generate/stream`（SSE） |
| 攻略 | `GET /api/trip/{id}` · `GET /api/trip/list` · `DELETE /api/trip/{id}` |
| 分享 | `POST /api/trip/{id}/share` · `GET /api/trip/public/{token}` · `GET /api/trip/{id}/qrcode` |
| 导游 | `POST /api/guide/identify` · `POST /api/guide/narrate` · `POST /api/guide/chat` |
| 语音 | `POST /api/voice/asr` · `POST /api/voice/tts` |
| 用户 | `POST /api/user/profile` |
| 增长 | `POST /api/growth/sign-in` · `GET /api/growth/summary` · `GET /api/growth/points` · `GET /api/growth/invite-info` |

## 关键约束（个人主体）

小程序为**个人主体**，存在以下硬性限制：

- ❌ 无微信支付（需商户号）
- ❌ 不能用微信同声传译插件（ASR/TTS 改用百度智能云）
- ⚠️ 类目只能选「工具-信息查询」（不能选旅游）
- ⚠️ 订阅消息仅「一次性」（授权一次发一条）

## 相关文档

- [功能与接口文档](docs/功能与接口文档.md) —— 完整业务说明 + 接口规范 + 联调指南
- [CLAUDE.md](CLAUDE.md) —— 项目开发约定（面向 AI 协作）

## License

竞赛作品，仅供学习交流。
