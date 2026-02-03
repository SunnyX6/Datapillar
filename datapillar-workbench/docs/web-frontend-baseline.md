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
