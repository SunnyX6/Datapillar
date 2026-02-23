# Web 前端基准设计与规范（草案 v0.2）

## 1. 背景
当前 Web 前端已具备设计系统与响应式基础，但断点与布局尺寸的权威来源分散，存在重复与漂移风险。本文档用于统一基准规则、明确范围，确保不改交互、不改行为。

## 2. 现状评估与基准结论
### 2.1 现状评估（Web 端）
优势：
- 已具备 Token 与 ClassMap 思路（`typography.ts`、`dimensions.ts`）。
- 关键布局组件已有基座（`AppLayout`、`ResponsiveContainer`）。
- ESLint 已限制硬编码像素与不合规断点。

问题：
- 断点在 CSS/JS 存在重复定义，容易漂移。
- 面板/菜单/表格列宽在组件内散落硬编码，导致重复与不一致。
- 大屏适配策略不统一，易出现“宽度过窄”的体验问题。

### 2.2 是否可作为今后基准
结论：**具备基础，但需要完成本次规范收敛后才可作为稳定基准**。本次收敛完成后，新增功能必须遵循本文档，方可作为新人开发的统一标准。

## 3. 范围与非范围
- 范围：Web 前端（React + Vite + Tailwind v4 + Zustand）。
- 非范围：App 端、mock 数据层、后端接口改造。
- 约束：不改变交互/路由/页面结构；不新增框架；只做规范收敛。

## 4. 设计原则
- 单一来源：断点、尺寸等基准只允许一个权威来源。
- 可复用：尺寸、字体、间距必须通过 Token/ClassMap 复用。
- 可审计：任何布局规则可追溯到 Token 或统一类。
- 不改交互：只改底层规范，不调整用户行为。

## 5. 现有基线（沿用）
- 断点：PC 专用断点定义在 `@theme`。`src/index.css`
- Typography/Dimensions Token：`src/design-tokens/typography.ts`、`src/design-tokens/dimensions.ts`
- 布局基座：`AppLayout`、`ResponsiveContainer`、`AdaptiveGrid`
- 基础组件：`Button`、`Card`、`Modal`、`Table`
- ESLint：禁止硬编码尺寸与不合规断点

## 6. 核心规范

### 6.1 断点单一来源（强制）
- 唯一权威：`@theme` 中的 `--breakpoint-*`。
- JS 只允许读取 CSS 变量，不允许硬编码断点数字。
- SSR/测试环境可使用兜底值，但仅限一个集中模块。

### 6.2 尺寸与 ClassMap（强制）
- 布局宽度必须走 `dimensions.ts` 的 ClassMap。
- 自定义响应式宽度类必须在 `index.css + dimensions.ts` 同步登记。
- 布局级固定宽度类（面板/菜单/表格列/装饰）必须使用 ClassMap；组件内部局部控件宽度作为后续阶段收敛。
- 面板/菜单/表格列/装饰尺寸必须分别使用对应 ClassMap（`panelWidthClassMap` / `menuWidthClassMap` / `tableColumnWidthClassMap` / `surfaceSizeClassMap`）。

### 6.3 响应式使用约定
- 页面级布局必须使用 `AppLayout/ResponsiveContainer`。
- 组件级响应式使用 `@container` 断点。
- 禁止 `xs/sm` 断点（PC-only）。
- 大屏增宽从 **1920px** 开始。

### 6.4 基础组件唯一入口
- Button/Card/Modal/Table 作为统一入口。
- 新样式仅通过 `variant/size` 扩展，不自建风格体系。

### 6.5 数据/状态边界
- API 客户端在 `src/lib/api`。
- 业务封装在 `src/services`。
- 组件不直接调用 axios。
- Zustand 只存跨页面状态。

## 7. 相似实现样本（≥3）
为保证模式统一，本次参考以下相似实现：
- 菜单宽度：`TopNav` / `TableOverview` / `StackTaskManager`
- 面板宽度：`WikiView` / `DataTypeExplorer` / `CollaborationSidebar`
- 表格列宽：`WordRootExplorer` / `MetricExplorer` / `MetricFormRight`

## 8. 本次实施范围（严格执行）
仅实施以下内容，其余为后续阶段，不在本次改动：
1. 新增断点读取工具（CSS 变量 → JS）。
2. 改造 `useResponsive/useBreakpoint` 使用断点读取工具。
3. `PC_BREAKPOINTS` 改为语义映射，移除断点数字重复。
4. 补充断点读取工具的单元测试。
5. 新增面板/菜单/表格列/装饰尺寸 ClassMap，并统一入口。
6. 将关键页面的固定宽度替换为 ClassMap（不改交互）。
7. 协作工单列表大屏宽度从 1920px 开始放大。

## 9. 不改交互保证
- 不调整路由、页面结构、交互流程。
- 断点数值与现有 `@theme` 一致。
- 仅替换断点来源，不改变计算逻辑。
- 仅替换 className，不改变交互状态与事件绑定。

## 10. 验收标准
- 断点数字只存在于 `@theme` 与断点读取工具的兜底值中。
- `useResponsive/useBreakpoint` 不再硬编码断点。
- 1920/2560 视口下页面行为与现状一致。
- `npm run lint` 通过。

---

## 11. 前端请求统一改造方案（v1.0）

### 11.1 现状诊断（请求层）
- 请求栈分裂：`axios` 主客户端、`fetchWithAuthRetry`、`gravitino` 独立客户端三套逻辑并存，入口不统一。
- 错误处理重复且信息丢失：多处手写 `extractErrorMessage + throw new Error(...)`，将结构化错误打平成字符串。
- 返回语义不一致：有的接口抛异常，有的接口吞错并返回业务对象（如 `success: false`），调用方心智负担高。
- 协议处理不一致：有的接口走标准 `ApiResponse`，有的接口 `validateResponse: false` 走裸响应。
- 特殊通道各写各的：上传、SSE、健康检查在不同模块自行维护，缺少统一规范。
- 现有可复用模式未推广：`studioCommon` 已经定义了分页与参数规范，但只在部分服务使用。

### 11.2 改造目标
- 单一请求入口：业务层仅调用统一 Request SDK，不直接接触 `axios/fetch`。
- 单一错误模型：所有失败统一归一为结构化错误（`status/code/message/requestId/traceId/retryable`）。
- 单一返回契约：明确“抛异常模式”与“Result 模式”的使用边界，禁止混用。
- 单一特殊通道规范：Upload/SSE/Health 走统一扩展适配，不再散落实现。

### 11.3 最终目录结构（强制）

```text
src/
  lib/
    api/
      client.ts
      request.ts
      endpoints.ts
      index.ts

  services/
    authService.ts
    setupService.ts
    healthService.ts
    studioCommon.ts
    studioProjectService.ts
    studioWorkflowService.ts
    studioTenantAdminService.ts
    studioLlmService.ts
    sqlService.ts
    knowledgeWikiService.ts
    knowledgeGraphService.ts
    aiWorkflowService.ts
    oneMetaService.ts
    oneMetaSemanticService.ts
    metricAIService.ts

  types/
    api.ts
    auth.ts
    setup.ts
    studio/
      project.ts
      workflow.ts
      tenant.ts
      llm.ts
    ai/
      knowledge.ts
      workflow.ts
      metric.ts
    onemeta/
      metadata.ts
      semantic.ts
```

### 11.4 文件命名规范（强制）
- `src/lib/api` 仅允许通用短名文件：`client.ts`、`request.ts`、`endpoints.ts`、`index.ts`。
- `src/services` 统一 `*Service.ts` 命名；通用工具保留 `studioCommon.ts` 这类 `*Common.ts`。
- `src/types/api.ts` 仅放传输层通用契约；业务 DTO 必须放 `src/types/<domain>`。
- 禁止新增 `contracts` 目录，避免无意义中间层。

### 11.5 各目录职责

#### `src/lib/api`
- `client.ts`：HTTP 基座（axios 实例、拦截器、CSRF、401 刷新、错误中心接入）。
- `request.ts`：统一请求入口（JSON/RAW/UPLOAD/SSE 的调用收口）。
- `endpoints.ts`：统一管理 API 基础路径与资源路径（含动态路径构造函数），禁止散落硬编码 URL。
- `index.ts`：统一导出 API 基础能力。

#### `src/services`
- 仅做业务语义封装：参数组装、响应映射、领域函数命名。
- `authService.ts`、`setupService.ts` 作为认证与初始化领域 API 的唯一入口。
- 不直接创建 axios 实例，不直接调用裸 `fetch`，不做全局错误分流决策。

#### `src/types`
- `api.ts`：通用协议与错误契约（`ApiResponse`、`ErrorResponse`、`ApiError`、类型守卫）。
- `auth.ts/setup.ts`：认证与初始化相关 DTO。
- `studio/project.ts`：项目域 DTO，包含项目列表分页参数与分页结果契约（`limit/offset/maxLimit`）。
- `studio/workflow.ts`：工作流域 DTO，包含工作流列表、运行列表、DAG 版本列表的分页参数与结果契约。
- `studio/*`、`ai/*`、`onemeta/*`：按前端领域拆分后端 DTO，禁止混在一个大文件。

### 11.6 分层依赖约束（强制）
- 依赖方向：`pages/layouts/stores -> services -> lib/api -> error-center`。
- 禁止反向依赖：`lib/api` 不得依赖 `services/pages/layouts`。
- 禁止跨层直连：页面与 store 不得直接 `axios/fetch` 调后端。

### 11.7 代码结构治理红线
- `src/services` 禁止 `axios.create`。
- `src/services` 禁止直接裸 `fetch(`（SSE 白名单由 `request.ts` 统一出口处理）。
- 禁止服务层继续使用 `throw new Error(extractErrorMessage(...))` 这类字符串打平逻辑。
- 禁止将所有后端契约塞入单个 `types/api.ts`。

### 11.8 实施阶段

#### 阶段 1：基座收敛
- 固化 `client.ts/request.ts/endpoints.ts` 四件套。
- 将路径常量迁移到 `endpoints.ts`，清理散落硬编码。

#### 阶段 2：类型收敛
- `types/api.ts` 只保留通用协议。
- 将领域 DTO 拆分到 `types/auth.ts`、`types/setup.ts`、`types/studio/*`、`types/ai/*`、`types/onemeta/*`。

#### 阶段 3：服务层迁移
- 按 `services/*Service.ts` 逐个迁移到统一请求入口。
- 移除重复错误提取与重复 `requireApiData`。

#### 阶段 4：特殊通道统一
- Upload、SSE、Health 检查统一从 `request.ts` 暴露调用，不允许各页面私自实现。

#### 阶段 5：验收
- 请求入口统一到 `lib/api/request.ts`。
- 契约类型完成领域拆分，`types/api.ts` 不再膨胀。
- 同类错误在不同页面行为一致，可追踪 `requestId/traceId`。
