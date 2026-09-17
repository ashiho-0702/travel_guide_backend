# CLAUDE.md

AI 旅行规划微信小程序（文旅赛道竞赛作品）。用户输入城市/喜好/预算/天数/体力，AI 结合真实口碑 + 本地知识库生成攻略；并支持景点语音讲解与语音问答。

## 技术栈

- 前端：微信小程序（原生，由队友负责）
- 后端：Spring Boot 4（Java 17）+ MyBatis + MySQL，代码在 `travel_guide/`
- AI：DeepSeek（生成/Function Calling/流式）、博查搜索、硅基流动 embedding、Zilliz Cloud（托管 Milvus）、通义千问 qwen-vl-plus（识图）
- 地图：腾讯地图

## 关键约束（必读）

1. **小程序是【个人主体】**：微信同声传译插件(WechatSI)不支持个人主体，所以 **ASR/TTS 不能用微信插件**，必须改用第三方（讯飞/腾讯云/百度等）。后续语音方案都据此出。
2. 真实密钥在 `travel_guide/src/main/resources/application.yml`（已被 .gitignore 忽略），模板见 `application.yml.example`。
3. 后端用 Jackson 3（`tools.jackson.*`，不是 `com.fasterxml.*`）。

## 后端模块

- 攻略生成：`service/workflow/TripWorkflowService`（7 步工作流）+ `service/agent/`（智能体）+ `service/rag/`（RAG）
- 语音导游：`service/guide/`（识别/讲解/对话/种子灌入）+ `service/qwen/QwenVlService`（识图）
- 接口：`/api/auth`、`/api/trip`、`/api/guide`、`/api/admin`
