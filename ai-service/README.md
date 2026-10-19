# Gemini 只读业务助手

Vue → Spring Boot `/api/ai/chat` → FastAPI `/chat` → Gemini。Python 工具只能请求固定的 Spring Boot `/internal/ai/tools`；不连接 MySQL。Java 不读取或转发 Gemini Key。

## 本地 Windows PowerShell

先启动现有 MySQL。保留现有数据库设置。要求 Java 21、Maven、Node.js 22.18+、Python 3.12+。

在项目根目录执行（已有 `.env` 时不要覆盖）：

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

在 **`ai-service/.env`** 填 `GEMINI_API_KEY` 和 `AI_INTERNAL_TOKEN`，后者使用至少 32 字符的随机串，可用密码管理器生成；不能使用 Gemini Key 代替。环境变量优先于 `.env`，Python 从服务目录绝对路径加载，不依赖启动目录。无需在 `application.yml` 保存 Key。

先在根目录另开终端启动 Java（脚本只读取内部 Token，不把 Gemini Key 注入 Java）：

```powershell
# 如 Maven 未使用 Java 21，先将 JAVA_HOME 指向自己的 JDK 21 安装目录。
.\ai-service\start-backend.ps1
```

从仓库根目录或 backend 目录直接运行 Java / Maven 时，后端也会从本地 `ai-service/.env` 读取内部 Token；环境变量 `AI_INTERNAL_TOKEN` 优先。IDE 的工作目录请设为仓库根目录或 backend。Gemini Key 仍只由 Python 读取。Token 缺失、AI 地址配置错误或 Python 离线都不会阻止普通业务启动；只在提问时显示 AI 错误。修改 Token 后需重启 Java 和 Python。

再在已激活 Python 虚拟环境的终端执行：

```powershell
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000 --no-access-log
```

前端另开终端：

```powershell
cd frontend
npm ci
npm run dev
```

Vue 通过现有 Vite `/api` 代理调用 Java；无需配置 Key。登录后到仓库管理点击“问仓库”或“总结当前范围”，任务详情点击“AI 分析”或“任务总结”。切换页面筛选会清空旧对话；全仓查询需在页面显式清除筛选。每次提问是独立的实时查询，请带上物料名或批号，不依赖上一条回复中的数量。

健康检查不调用 Gemini、不消耗配额：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

`status=ok` 表示服务存活；`ready=false` 表示关闭或缺少配置。未填 Key 不影响普通仓库功能。真实连通性手动测试：先确认公司允许这些数据发送给 Google，登录网页、选择一个小范围货位，输入“当前范围有哪些物料？”，核对批次、数量、待盘点与页面物料汇总一致，再点具体批次核对托盘入口。此操作会调用真实 API；自动化测试全部使用 Mock。

## Docker 服务器

在已有根目录 **`.env`** 末尾补充以下字段，不覆盖数据库、上传目录、账号等原配置：

```env
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.1-flash-lite
AI_ENABLED=true
AI_INTERNAL_TOKEN=
```

服务器不需要 `ai-service/.env`。Compose 只向 Python 注入 Key；内部 Token 注入两端。Python 使用 `http://backend:8080`，Java 使用 `http://ai-service:8000`。AI 容器不映射主机端口，沿用现有 Nginx，无新增反向代理，也不作为 backend/frontend 启动依赖。

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose exec ai-service python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health').read().decode())"
```

不要在共享日志中运行会输出所有展开变量的 `docker compose config`。修改配置后重新创建对应容器。关闭 AI：设置 `AI_ENABLED=false` 后执行 `docker compose up -d`。

## API、权限和数据口径

- `/api/ai/chat`：只接受已登录会话；现有系统只有一个管理员身份，没有另造角色模型。Java 限制全局每分钟 10 次、同时 2 次，防止通过创建会话绕过限流。
- `/internal/ai/tools`：要求恒定时间校验的独立 Bearer Token，以及 Java 在已登录请求中生成的随机短期 ticket。ticket 保存真实页面范围、绑定会话、90 秒过期、请求结束撤销、最多 6 次查询；退出登录立即失效。凭据不发给模型，不发给浏览器。
- 六个固定工具：`search_products`（只查台账中已录入身份）、`get_warehouse_material_summary`、`get_batch_locations`、`get_pending_inventory`、`get_production_task_details`、`get_movement_summary`。库存工具与任务工具按页面隔离；额外位置/关键词条件只能与原范围取交集，不能替换原范围。
- 库存对完整台账聚合，不使用分页，不由流转推算。精确名称及产品 ID / 手工编码归类，歧义身份不合并。批号缺失逐行保留，原料批号仅追溯。数量按单位独立累计，未知与未填单位不计入确认总量。大整数用十进制字符串传输。
- 生产任务直接调用现有 `ProductionTaskService.detail`，不重写发退料、历史包装或完成度算法。作废记录保留追溯入口但不计入统计。发送白名单字段，不发送备注、照片、人员信息或无关任务。
- Gemini 官方 SDK 手动 Function Calling，禁用 SDK 自动执行；Pydantic 严格校验参数，只能访问固定工具端点。保留模型返回的 thought signature 与 function-call ID。没有 Google Search、Code Execution、SQL、任意 URL 或写入工具。
- 为保证数字不被编造，模型最终只选择服务端核验的解释片段 ID。自由文本、伪造数字和链接不会展示；前端卡片直接使用已校验的 Java 结果。模型负责工具选择与解释重点，数值和业务规则由程序确定。这是第一版的有意约束。
- 单请求最多 4 轮、6 次工具调用；Gemini 单次 25 秒、整请求 70 秒、单次输出 1200 tokens、累计返回用量上限 20000 tokens。503 等短暂服务错误最多重试一次；429、403 不自动重试，不切换模型。上限可由 Settings 对应环境变量调低。进程限流适用于当前单实例部署，多副本需另做共享预算。
- 库存明细超过 22000 字符时返回完整范围的物料/托盘/待盘点总项数，并明确标记明细省略，要求指定物料或缩小范围；不会把局部明细当全仓总量。任务结果超过 24000 字符拒绝并要求缩小问题范围。多轮累计上下文上限 40000 字符。查询为空、未知数量和查询失败明确区分。
- 日志只记耗时、Token 用量、调用次数和错误类型；不记录问题、业务返回、API Key、内部 Token、ticket。不要在生产开启 HTTP debug 日志。

## 费用与公司数据

首次启用真实业务查询前，由公司确认允许使用的 Gemini 项目和数据政策。免费额度受 AI Studio 项目、模型和地区实际限制约束，没有无限免费保证，不会自动开通付费。Google 的免费服务条款可能允许将输入和输出用于产品改进并由人工审阅；不要未经授权传输敏感业务数据。付费服务的数据使用规则不同，仍需审核保留和滥用监测条款。

官方依据：[Function Calling](https://ai.google.dev/gemini-api/docs/function-calling)、[SDK](https://github.com/googleapis/python-genai)、[服务条款](https://ai.google.dev/gemini-api/terms)、[数据保留说明](https://ai.google.dev/gemini-api/docs/zdr)。部署时重新核对项目可用模型与最新条款；模型不可用会明确报错，不自动升级或替换。

## 测试

```powershell
cd ai-service
.\.venv\Scripts\python -m pytest -q
cd ../backend
mvn test
cd ../frontend
npm test
npm run build
cd ..
docker compose config --quiet
```

Python 测试不依赖真实 Key，不访问 Gemini。Java 用单元/MockMvc 与既有隔离 H2 集成测试，覆盖台账聚合和现有任务计算；前端复用相同仓库样例验证身份、批次、货位、待盘点和单位。
