# Datapillar Studio 前端开发架构规范（已落地）

## 1. 文档性质

- 本文档是当前仓库的长期开发规范，不是重构计划。
- 生效范围：`src/**` 与 `tests/**`。
- 核心原则：职责单一、依赖单向、禁止兼容兜底、禁止跨层乱调。

## 2. 总体架构原则

- 不保留 `shared` 目录。
- 不保留任何 `lib` 目录（顶层和 feature 内都禁止）。
- `features` 只允许 `state/hooks/ui/utils` 四类子目录（`index.ts` 可选）。
- `features` 禁止出现 `api/service/lib` 目录。
- 仅 `services/**` 可以直接调用 `api/**`。
- 全局状态只放 `src/state/**`；业务状态只放 `src/features/*/state/**`。
- 不引入兼容层，不写 legacy 迁移兜底代码。

## 3. 分层职责

### app
- 仅保留初始化文件：
  - `src/app/i18n.tsx`
  - `src/app/auth.tsx`
  - `src/app/theme.tsx`
- 不建立 `provider/` 目录。

### router
- 负责路由清单、路由构建、守卫链。
- 路由单一真相源：`src/router/routeManifest.ts`。
- 布局编排容器放在 `src/router/containers/**`。

### pages
- 只做路由入口薄壳与装配。
- 禁止业务编排。
- 禁止直连 `services/api`。

### layouts
- 只做壳层展示与区域编排。
- 允许读取全局 UI 状态（主题/语言/搜索展示态）。
- 禁止业务流程编排、禁止接口调用、禁止路由策略判定。

### features
- 业务实现层，只允许 `state/hooks/ui/utils`。
- `utils` 必须是纯函数，禁止副作用与接口调用。

### services
- 负责业务接口调用、参数组装、结果处理、错误语义化。
- 业务契约类型统一放 `src/services/types/**`。

### api
- 只做连接层与协议层：
  - `client`
  - `request`
  - `interceptors`
  - `errorNormalizer`
  - `endpoints`
  - `types`（基础响应协议）
- 禁止业务流程编排。

## 4. 目录规范

```text
src/
  app/
    i18n.tsx
    auth.tsx
    theme.tsx

  state/
    authStore.ts
    setupStore.ts
    themeStore.ts
    i18nStore.ts
    layoutStore.ts
    searchStore.ts
    index.ts

  router/
    routeManifest.ts
    buildRoutes.tsx
    containers/
      MainLayoutContainer.tsx
    guards/
      BootstrapGate.tsx
      SetupGate.tsx
      AuthGate.tsx
      PermissionBoundary.tsx
    access/
      menuAccess.ts

  pages/
    */index.tsx

  layouts/
    MainLayout.tsx
    navigation/
      Sidebar.tsx
      TopNav.tsx
      ExpandToggle.tsx
    responsive/
      *.tsx

  features/
    <feature-name>/
      index.ts (可选)
      state/ (按需)
      hooks/ (按需)
      ui/ (必需)
      utils/ (按需)
```

## 5. 状态管理规范

- `src/state/**` 只允许全局共享状态：
  - `authStore`
  - `setupStore`
  - `themeStore`
  - `i18nStore`
  - `layoutStore`
  - `searchStore`
- 业务域状态必须放到各自 feature：
  - `src/features/workflow/state/**`
  - `src/features/governance/state/**`
- 禁止把业务 store 回流到 `src/state/**`。

## 6. Router 规范

- `routeManifest` 每条路由必须声明：
  - `path`
  - `lazy`（入口路由除外）
  - `requireSetup`
  - `requireAuth`
  - `requiredMenuPath`（需要权限时）
  - `entryPriority`（入口候选时）
- 固定守卫链：
  - `BootstrapGate -> SetupGate -> AuthGate -> PermissionBoundary`
- 无权限统一跳转 `/403`。
- `MainLayout` 的业务编排逻辑由 `MainLayoutContainer` 注入，不写在 layout 组件内。

## 7. 依赖方向（强制）

`app/router/containers/pages/layouts/features -> services -> api -> utils/config`

补充：
- `components/ui -> hooks/utils/config/design-tokens`
- `components/common -> components/ui + hooks/utils/config`
- `api` 禁止依赖 `services/features/layouts/pages/router/app`
- `services` 禁止依赖 `features/layouts/pages/router/app`

## 8. ESLint 门禁（已启用）

- `pages` 禁止导入 `@/app/*`、`@/router/*`、`@/api/*`、`@/services/*`。
- `layouts` 禁止导入 `@/app/*`、`@/router/*`、`@/pages/*`、`@/features/**`、`@/api/**`。
- `layouts` 禁止导入业务 `@/services/*`（仅允许 `@/services/types/*` 与 `@/services/menuPermissionService`）。
- `features` 禁止导入 `@/app/*`、`@/router/*`、`@/pages/*`、`@/layouts/navigation/*`、`@/layouts/MainLayout*`。
- `features/**/utils/**` 禁止导入 `@/api/*`、`@/services/*`。
- `services` 禁止导入 `@/app/*`、`@/router/*`、`@/pages/*`、`@/layouts/*`、`@/features/*`。
- `api` 禁止导入 `@/services/*`、`@/features/*`、`@/layouts/*`、`@/pages/*`、`@/router/*`、`@/app/*`、`@/components/*`。
- `src/state/**` 仅允许全局状态文件；非白名单文件直接报错。
- `features/**` 禁止从 `@/state` 导入业务 store/type（workflow/governance 业务状态）。

## 9. 命名规范

- React 组件文件：`PascalCase.tsx`
- 普通模块文件：`camelCase.ts`
- DTO 文件：`*Dto.ts`
- 目录名：`lowercase` 或 `lower_snake_case`
- 页面入口：`index.tsx`
- 禁止：`kebab-case` 文件名

## 10. 质量门禁

- 提交前必须通过：
  - `npm run type-check`
  - `npm run lint`
  - `npm run test`
- 任何新增代码不得引入兼容兜底逻辑。

## 11. 当前落地事实（代码核验）

- `src/features/**` 下 `api/service/lib` 目录数量为 0。
- `src/pages/**` 中 `@/services` 与 `@/api` 直连数量为 0。
- `src/state/**` 当前只包含全局状态文件。
- `src/router/buildRoutes.tsx` 已使用 `MainLayoutContainer`，非直接挂载 `MainLayout`。
- 当前 `type-check/lint/test` 全部通过。
