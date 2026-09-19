# CLAUDE.md

AI 旅行规划微信小程序（文旅赛道竞赛作品）。用户输入目的地城市/开始日期/天数/人数/预算/兴趣偏好/体力/交通方式，AI 结合真实口碑 + 本地知识库生成攻略；并支持景点语音讲解与语音问答。

## 技术栈

- 前端：微信小程序（原生，由队友负责）
- 后端：Spring Boot 4（Java 17）+ MyBatis + MySQL，代码在 `travel_guide/`
- AI：DeepSeek（生成/Function Calling/流式）、博查搜索、硅基流动 embedding、Zilliz Cloud（托管 Milvus）、通义千问 qwen-vl-plus（识图）
- 地图：腾讯地图

## 关键约束（必读）

1. **小程序是【个人主体】**：微信同声传译插件(WechatSI)不支持个人主体，所以 **ASR/TTS 不能用微信插件**，必须改用第三方（讯飞/腾讯云/百度等）。后续语音方案都据此出。
2. 真实密钥在 `travel_guide/src/main/resources/application.yml`（已被 .gitignore 忽略），模板见 `application.yml.example`。
3. 后端用 Jackson 3（`tools.jackson.*`，不是 `com.fasterxml.*`）。

## 竞赛评审维度（2026-09-18，官方）

核心主题：**微信生态能力与 AI 能力有机结合**。五大类：①微信生态基础（小程序/登录/支付/订阅消息/分享/二维码）②业务能力（地图/位置/音视频/OCR/内容安全）③AI能力融合（大模型/RAG/Agent/工作流/语音图像识别）④增长运营（会员/社交传播/裂变/留存）⑤工程工具（MCP/联调/监控/A/B测试）。

当前已覆盖 AI 能力融合；微信生态（分享/订阅消息）和增长运营是薄弱点，后续功能优先往这两块补强。

## 后端模块

- 攻略生成：`service/workflow/TripWorkflowService`（7 步工作流）+ `service/agent/`（智能体）+ `service/rag/`（RAG）
- 语音导游：`service/guide/`（识别/讲解/对话/种子灌入）+ `service/qwen/QwenVlService`（识图）
- 接口：`/api/auth`、`/api/trip`、`/api/guide`、`/api/admin`
