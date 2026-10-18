# Gemini AI 助手交付与验证

实现和配置说明见 [ai-service/README.md](../ai-service/README.md)。本文件描述已实现功能，未提交的 `ai-integration-proposal.md` 仍属于原有建议稿。

## 已实现

- 独立 FastAPI 服务：配置、Pydantic 请求/工具/结果模型、官方 google-genai 手动 Function Calling、HTTPX 固定内部工具访问、健康检查、限流、并发与用量限制、超时和错误分类。
- Java `/api/ai/chat` 验证现有登录；`/internal/ai/tools` 使用服务 Token + 绑定会话及页面范围的短期随机凭据。Java 聚合完整托盘台账、复用生产任务 Service，Python 无数据库连接。
- Vue 仓库“问仓库 / 总结当前范围”、任务“AI 分析 / 任务总结”，右侧抽屉跟随筛选，直接显示批次数量、展开货位、待盘点、来源时间和原记录链接。模型不能提供链接；前端由记录 ID 生成已有路由。
- 只读，没有修改库存工具。Gemini Key 仅在 Python 读取；本地启动脚本仅向 Java 传递独立内部 Token。Compose 不开放 AI 服务端口。
- 最终解释采用可验证片段选择：Gemini 根据工具结果挑选说明重点，程序仅展示已验证的业务规则和数量，过滤任意模型自由文本。每条问题重新查询，不把旧对话数据当当前库存。

## 口径依据

当前 `warehouseSummary.ts` 与 `docs/warehouse.md` 是台账汇总依据。Java 测试复用了前端同样的 12000 + 8000 = 20000、另一批 50000、多物料、身份歧义、混合单位、空数量与大整数样例。

生产计算复用 `ProductionTaskService`，以 `docs/production-yield.md` 为最新说明。`production-task-output.md` 中早期的计划回仓百分比只适用于未配置新包装口径的旧任务。AI 不重算历史记录或自行替换分母。

## 2026-10-18 验证记录

| 检查 | 实际结果 |
| --- | --- |
| Python pytest | 30 项通过；Gemini 全部 Mock，包含真实 SDK + Mock HTTP 的完整工具往返 |
| Java | 完整回归 112 项通过；新增任务投影测试后 AI 相关 10 项通过，累计 113 项，0 失败/错误 |
| 前端 Node 测试 | 42 项通过 |
| Vue TypeScript + Vite 生产构建 | 通过 |
| 前端 Lint | 项目没有配置 Lint 脚本，未虚报执行 |
| Compose | 独立 Docker Compose v2.39.4，`--env-file .env.example config --quiet` 通过 |
| 浏览器 | 隔离模拟 API：桌面与 390×844 手机抽屉、范围、直接批次、货位展开、托盘详情跳转、AI 503 提示；修复了手机滚动条导致的 15px 偏移 |
| UI 规范检查 | 新增组件标签、原生 dialog 焦点约束、键盘按钮/summary、状态播报、溢出、移动宽度已检查 |
| Git | 提交前检查差异与忽略规则；原有 application.yml、bug/、提案文档不纳入本次提交 |

首次 `mvn test` 因 Maven 的 JAVA_HOME 使用旧 JDK 失败，改用机器已安装的 Java 21 后通过。测试均使用隔离 H2 或 Mock，没有在用户业务库写入数据。

浏览器可复现隔离环境（先构建前端，从项目根目录运行）：

```powershell
.\ai-service\.venv\Scripts\python -m uvicorn ui_fixture:app --app-dir ai-service/tests --host 127.0.0.1 --port 5189
```

打开 `http://127.0.0.1:5189/warehouse`；这是固定的假数据，输入“失败”可验证错误提示，不能作为真实 Gemini 连通证明。

## 仍需部署者验证

未填写真实 Gemini Key，未调用实际 Gemini API，不消耗实际配额。需填写配置并在公司允许传输数据后按 README 执行真实小范围查询。

本机没有 Docker 引擎，已做 Compose 配置校验，但未执行容器构建/启动或服务器部署。服务器执行 `docker compose up -d --build` 后检查健康状态和登录查询。

当前系统为单 backend + 单 AI 服务。内存凭据与限流适用于此部署；未来扩容多副本需增加共享预算及请求路由设计。超过数据长度预算时明确要求缩小范围，库存总项数仍覆盖完整查询范围。任何后续写入能力都必须另行实现人工确认流程。
