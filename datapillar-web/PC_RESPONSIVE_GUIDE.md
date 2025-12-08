# PC 端响应式开发指南（1080p - 4K）

> **核心理念**：基于项目现有模式（状态驱动 + ClassMap），只关注 PC 端分辨率。

---

## 🎯 项目现有的三大响应式模式

### **模式 1：状态驱动的样式切换**（推荐）

**参考**：`src/layouts/navigation/Sidebar.tsx:29`

```tsx
import { sidebarWidthClassMap, sidebarPaddingClassMap } from '@/design-tokens/dimensions'
import { useLayoutStore } from '@/stores'

// ✅ 正确：基于全局状态驱动样式切换
const collapsed = useLayoutStore(state => state.isSidebarCollapsed)
const sidebarWidth = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
const sectionPadding = collapsed ? sidebarPaddingClassMap.collapsed : sidebarPaddingClassMap.normal

<aside className={`${sidebarWidth} ${sectionPadding} transition-[width] duration-300`}>
  侧边栏内容
</aside>
```

**为什么这样做**：
1. ✅ 集中式状态管理（Zustand + localStorage 持久化）
2. ✅ 主内容区自动适配（使用 `flex-1`）
3. ✅ 平滑过渡动画（`transition-[width] duration-300`）
4. ✅ 所有尺寸通过 ClassMap 管理，统一修改

---

### **模式 2：Container Queries**（局部响应式）

**参考**：`src/layouts/navigation/TopNav.tsx:115`

```tsx
// ✅ 正确：使用 @container 查询
<div className="@container relative h-14 w-full">
  {/* 容器宽度 >= 1280px 时显示 */}
  <div className="hidden @xl:flex items-center gap-2">
    组织名称
  </div>

  {/* 容器宽度 >= 1280px 时内联显示 */}
  <span className="hidden @xl:inline">Governance</span>
</div>
```

**为什么这样做**：
1. ✅ 根据**父容器宽度**（而非屏幕宽度）响应
2. ✅ 适合侧边栏展开/收起后的布局调整
3. ✅ 比 media query 更灵活

**Container Query 断点**：
- `@md`: 容器 >= 768px
- `@lg`: 容器 >= 1024px
- `@xl`: 容器 >= 1280px
- `@2xl`: 容器 >= 1536px

---

### **模式 3：ClassMap 参数化**（预定义样式）

**参考**：`src/layouts/responsive/AppLayout.tsx:25`

```tsx
import { paddingClassMap, gapClassMap } from '@/design-tokens/dimensions'

// ✅ 正确：使用 ClassMap 参数化
<div className={paddingClassMap.md}>
  内容（24px/32px 内边距，随断点自适应）
</div>

<div className={`grid ${gapClassMap.lg}`}>
  栅格布局（32px 间距）
</div>
```

**为什么这样做**：
1. ✅ 统一管理响应式类名
2. ✅ TypeScript 类型安全
3. ✅ 易于维护和修改

---

## 📐 PC 端尺寸规范（1080p - 4K）

### **侧边栏尺寸**（状态驱动模式）

```tsx
import {
  sidebarWidthClassMap,
  sidebarPaddingClassMap,
  sidebarSpacingClassMap
} from '@/design-tokens/dimensions'

const collapsed = useLayoutStore(state => state.isSidebarCollapsed)

// 宽度
const width = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
// collapsed: 72px, normal: 240px, wide: 320px

// 内边距
const padding = collapsed ? sidebarPaddingClassMap.collapsed : sidebarPaddingClassMap.normal
// collapsed: px-2, normal: px-4

// 间距
const spacing = collapsed ? sidebarSpacingClassMap.collapsed : sidebarSpacingClassMap.normal
// collapsed: space-y-1.5, normal: space-y-8
```

### **卡片/模态框宽度**（固定尺寸）

```tsx
import { cardWidthClassMap, modalWidthClassMap } from '@/design-tokens/dimensions'

// ✅ PC 端不需要 w-full（不会全屏）
<div className={cardWidthClassMap.normal}>卡片（448px）</div>
<div className={modalWidthClassMap.large}>模态框（600px）</div>
```

**可选值**：
- `narrow`: 384px
- `normal`: 448px（最常用）
- `medium`: 512px
- `wide`: 672px
- `extraWide`: 896px

### **容器高度**

```tsx
import { containerHeightClassMap } from '@/design-tokens/dimensions'

// ✅ PC 端使用 dvh（动态视口高度）
<div className={containerHeightClassMap.fullscreen}>全屏容器（100dvh）</div>
<div className={containerHeightClassMap.minFullscreen}>最小全屏（min-h-dvh）</div>
```

### **内容最大宽度**

```tsx
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'

// ✅ Dashboard 默认使用 extraWide（1600px）
<div className={contentMaxWidthClassMap.extraWide}>
  Dashboard 内容
</div>

// ✅ 4K 显示器可使用 full（不限制宽度）
<div className={contentMaxWidthClassMap.full}>
  4K 全宽内容
</div>
```

### **图标尺寸**

```tsx
import { iconSizeToken } from '@/design-tokens/dimensions'
import { Workflow } from 'lucide-react'

// ✅ 导航栏图标（15px）
<Workflow size={iconSizeToken.normal} />

// ✅ TopNav 图标（14px）
<Search size={iconSizeToken.small} />

// ✅ Brand Logo（32px）
<BrandLogo size={iconSizeToken.logo} />
```

---

## ✅ 完整使用示例

### **示例 1：创建新的侧边栏组件**

```tsx
import { sidebarWidthClassMap, sidebarPaddingClassMap, iconSizeToken } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { useLayoutStore } from '@/stores'

export function CustomSidebar() {
  const collapsed = useLayoutStore(state => state.isSidebarCollapsed)

  // 状态驱动的样式切换
  const sidebarWidth = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
  const sectionPadding = collapsed ? sidebarPaddingClassMap.collapsed : sidebarPaddingClassMap.normal

  return (
    <aside className={`${sidebarWidth} ${sectionPadding} transition-[width] duration-300`}>
      <nav className={TYPOGRAPHY.body}>
        <WorkflowIcon size={iconSizeToken.normal} />
        {!collapsed && <span>工作流</span>}
      </nav>
    </aside>
  )
}
```

### **示例 2：创建响应式卡片**

```tsx
import { cardWidthClassMap, radiusClassMap, paddingClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'

export function DataCard() {
  return (
    <div className={`${cardWidthClassMap.normal} ${radiusClassMap.extraLarge} ${paddingClassMap.md}`}>
      <h3 className={TYPOGRAPHY.heading}>卡片标题</h3>
      <p className={TYPOGRAPHY.body}>卡片内容</p>
    </div>
  )
}
```

### **示例 3：使用 Container Queries**

```tsx
export function AdaptiveHeader() {
  return (
    <div className="@container w-full">
      {/* 容器宽度 >= 1280px 时显示 */}
      <div className="hidden @xl:flex items-center gap-4">
        <OrgSelector />
        <UserProfile />
      </div>

      {/* 容器宽度 < 1280px 时显示 */}
      <div className="@xl:hidden">
        <MobileMenu />
      </div>
    </div>
  )
}
```

---

## 🚫 禁止的写法

### ❌ 禁止 1：硬编码尺寸

```tsx
// ❌ 错误：硬编码固定尺寸
<div className="w-[240px] h-[600px]">内容</div>

// ✅ 正确：使用 ClassMap
import { sidebarWidthClassMap, containerHeightClassMap } from '@/design-tokens/dimensions'
<div className={`${sidebarWidthClassMap.normal} ${containerHeightClassMap.extraTall}`}>内容</div>
```

### ❌ 禁止 2：使用移动端断点

```tsx
// ❌ 错误：使用 xs、sm 断点（PC 端不需要）
<div className="w-full sm:w-80 lg:w-96">内容</div>

// ✅ 正确：PC 端直接使用固定宽度
import { sidebarWidthClassMap } from '@/design-tokens/dimensions'
<div className={sidebarWidthClassMap.wide}>内容</div>
```

### ❌ 禁止 3：重复的条件样式逻辑

```tsx
// ❌ 错误：每次都写重复的条件逻辑
const width = collapsed ? 'w-[72px]' : 'w-[240px]'
const padding = collapsed ? 'px-2' : 'px-4'
const spacing = collapsed ? 'space-y-1.5' : 'space-y-8'

// ✅ 正确：使用 ClassMap 集中管理
import { sidebarWidthClassMap, sidebarPaddingClassMap, sidebarSpacingClassMap } from '@/design-tokens/dimensions'
const width = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
const padding = collapsed ? sidebarPaddingClassMap.collapsed : sidebarPaddingClassMap.normal
const spacing = collapsed ? sidebarSpacingClassMap.collapsed : sidebarSpacingClassMap.normal
```

---

## 🎯 开发检查清单

**每次开发新功能时，确保**：

- [ ] 使用 ClassMap 管理所有尺寸（不硬编码）
- [ ] 使用状态驱动模式（参考 Sidebar）
- [ ] 使用 Container Queries 处理局部响应式
- [ ] 不使用移动端断点（xs、sm）
- [ ] 测试 1920p、2K、4K 分辨率
- [ ] 运行 `npm run lint` 检查 ESLint 错误

---

## 🔧 PC 端专用断点（仅供参考）

```tsx
export const PC_BREAKPOINTS = {
  '2k': 1440,    // 2K/QHD 起步（2560x1440 对应的布局宽度门槛）
  fhd: 1920,     // 1080p：1920x1080（最常见基线）
  qhd: 2560,     // 2.5K：2560x1440
  '4k': 3840     // 4K：3840x2160
}
```

**注意**：PC 端主要使用 Tailwind 默认断点（lg、xl、2xl），以及 Container Queries（@xl、@2xl）。

---

## 📚 快速参考

### 导入路径

```tsx
// 尺寸 ClassMap
import {
  sidebarWidthClassMap,
  cardWidthClassMap,
  modalWidthClassMap,
  containerHeightClassMap,
  contentMaxWidthClassMap,
  iconSizeToken,
  paddingClassMap,
  gapClassMap,
  radiusClassMap
} from '@/design-tokens/dimensions'

// 字体 Token
import { TYPOGRAPHY } from '@/design-tokens/typography'

// 布局状态
import { useLayoutStore } from '@/stores'
```

### 常用代码片段

```tsx
// 侧边栏状态驱动
const collapsed = useLayoutStore(state => state.isSidebarCollapsed)
const width = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal

// Container Query
<div className="@container">
  <div className="hidden @xl:flex">内容</div>
</div>

// ClassMap 参数化
<div className={paddingClassMap.md}>
  <div className={gapClassMap.lg}>
    <div className={radiusClassMap.extraLarge}>
      内容
    </div>
  </div>
</div>
```

---

**最后更新**: 2025-01-XX
