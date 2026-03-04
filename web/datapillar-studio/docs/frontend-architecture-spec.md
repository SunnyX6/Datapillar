# Datapillar Studio Front-end development architecture specifications(Has landed)

## 1.Document nature

- This document is the long-term development specification for the current warehouse,Not a refactoring plan.- Effective scope:`src/**` with `tests/**`.- core principles:Single responsibility,One-way dependency,Compatibility is prohibited,Cross-layer chaos is prohibited.## 2.Overall architectural principles

- Not reserved `shared` Directory.- do not retain any `lib` Directory(top level and feature Prohibited inside).- `features` only allowed `state/hooks/ui/utils` Four categories of subdirectories(`index.ts` Optional).- `features` prohibited `api/service/lib` Directory.- only `services/**` Can be called directly `api/**`.- Global status only puts `src/state/**`;The business status is only displayed `src/features/*/state/**`.- No compatibility layer is introduced,Dont write legacy Migrate hidden code.## 3.Hierarchical responsibilities

### app
- Keep only initialization files:- `src/app/i18n.tsx`
 - `src/app/auth.tsx`
 - `src/app/theme.tsx`
- Not established `provider/` Directory.### router
- Responsible for routing list,Route construction,guard chain.- Routing a single source of truth:`src/router/routeManifest.ts`.- The layout orchestration container is placed in `src/router/containers/**`.### pages
- Only do routing entrance shell and assembly.- Disable business orchestration.- Direct connection prohibited `services/api`.### layouts
- Only shell display and regional arrangement.- Allow global reading UI Status(Topic/language/Search display status).- Disable business process orchestration,Disable interface calls,Disable routing policy determination.### features
- business implementation layer,only allowed `state/hooks/ui/utils`.- `utils` Must be a pure function,Disallow side effects and interface calls.### services
- Responsible for business interface calls,Parameter assembly,Result processing,wrong semantics.- Business contract types are unified `src/services/types/**`.### api
- Only the connection layer and protocol layer:- `client`
 - `request`
 - `interceptors`
 - `errorNormalizer`
 - `endpoints`
 - `types`(basic response protocol)
- Disable business process orchestration.## 4.Directory specifications

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
 index.ts (Optional)
 state/ (on demand)
 hooks/ (on demand)
 ui/ (required)
 utils/ (on demand)
```

## 5.Status management specifications

- `src/state/**` Only allow global shared state:- `authStore`
 - `setupStore`
 - `themeStore`
 - `i18nStore`
 - `layoutStore`
 - `searchStore`
- Business domain status must be placed in their respective feature:- `src/features/workflow/state/**`
 - `src/features/governance/state/**`
- prohibit business store flow back to `src/state/**`.## 6.Router normative

- `routeManifest` Each route must be declared:- `path`
 - `lazy`(Except for ingress routes)
 - `requireSetup`
 - `requireAuth`
 - `requiredMenuPath`(When permission is required)
 - `entryPriority`(Entrance candidate)
- Fixed guard chain:- `BootstrapGate -> SetupGate -> AuthGate -> PermissionBoundary`
- Unified jump without permission `/403`.- `MainLayout` The business orchestration logic consists of `MainLayoutContainer` Inject,not written in layout within component.## 7.Depends on direction(force)

`app/router/containers/pages/layouts/features -> services -> api -> utils/config`

supplement:- `components/ui -> hooks/utils/config/design-tokens`
- `components/common -> components/ui + hooks/utils/config`
- `api` Dependence is prohibited `services/features/layouts/pages/router/app`
- `services` Dependence is prohibited `features/layouts/pages/router/app`

## 8.ESLint access control(Enabled)

- `pages` Import prohibited `@/app/*`,`@/router/*`,`@/api/*`,`@/services/*`.- `layouts` Import prohibited `@/app/*`,`@/router/*`,`@/pages/*`,`@/features/**`,`@/api/**`.- `layouts` Importing business is prohibited `@/services/*`(only allowed `@/services/types/*` with `@/services/menuPermissionService`).- `features` Import prohibited `@/app/*`,`@/router/*`,`@/pages/*`,`@/layouts/navigation/*`,`@/layouts/MainLayout*`.- `features/**/utils/**` Import prohibited `@/api/*`,`@/services/*`.- `services` Import prohibited `@/app/*`,`@/router/*`,`@/pages/*`,`@/layouts/*`,`@/features/*`.- `api` Import prohibited `@/services/*`,`@/features/*`,`@/layouts/*`,`@/pages/*`,`@/router/*`,`@/app/*`,`@/components/*`.- `src/state/**` Allow only global state files;Non-whitelisted files directly report an error.- `features/**` prohibited from `@/state` Import business store/type(workflow/governance business status).## 9.Naming convention

- React component file:`PascalCase.tsx`
- Ordinary module file:`camelCase.ts`
- DTO File:`*Dto.ts`
- directory name:`lowercase` or `lower_snake_case`
- Page entry:`index.tsx`
- prohibited:`kebab-case` file name

## 10.Quality access control

- Must pass before submitting:- `npm run type-check`
 - `npm run lint`
 - `npm run test`
- Any new code must not introduce compatibility cover-up logic.## 11.Current implementation facts(code verification)

- `src/features/**` down `api/service/lib` The number of directories is 0.- `src/pages/**` in `@/services` with `@/api` The number of direct connections is 0.- `src/state/**` Currently only contains global state files.- `src/router/buildRoutes.tsx` Already used `MainLayoutContainer`,Not directly mounted `MainLayout`.- current `type-check/lint/test` All passed.
