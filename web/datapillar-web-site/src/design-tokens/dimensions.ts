import type { BreakpointKey } from './breakpoints'

/**
 * PC 端尺寸规范系统（1080p - 4K）
 *
 * 设计原则：
 * 1. 基于项目现有的 ClassMap 模式
 * 2. 状态驱动的样式切换（参考 Sidebar）
 * 3. 只考虑 PC 端分辨率（1080p/1440p/1920p/4K）
 *
 * 使用方式：
 * ```tsx
 * import { sidebarWidthClassMap } from '@/design-tokens/dimensions'
 *
 * // 状态驱动模式（参考 Sidebar）
 * const sidebarWidth = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
 * <aside className={sidebarWidth} />
 * ```
 */

/**
 * 侧边栏宽度 ClassMap（状态驱动模式）
 * 参考：src/layouts/navigation/Sidebar.tsx:29
 *
 * ⚠️  禁止直接硬编码 w-[72px]、w-[240px]
 * ✅  必须使用这个 ClassMap
 */
export const sidebarWidthClassMap = {
  /** 收起状态：72px（与现有 Sidebar 一致）*/
  collapsed: 'w-[72px]',

  /** 标准宽度：响应式（240px → 280px @ 1920px → 320px @ 2560px）*/
  normal: 'w-sidebar-responsive',

  /** 宽屏：320px（适合 4K 显示器）*/
  wide: 'w-80'
} as const

/**
 * 侧边栏内边距 ClassMap（状态驱动模式）
 * 参考：src/layouts/navigation/Sidebar.tsx:30
 */
export const sidebarPaddingClassMap = {
  /** 收起状态 */
  collapsed: 'px-2',

  /** 正常状态 */
  normal: 'px-4'
} as const

/**
 * 侧边栏间距 ClassMap（状态驱动模式）
 * 参考：src/layouts/navigation/Sidebar.tsx:31
 */
export const sidebarSpacingClassMap = {
  /** 收起状态 */
  collapsed: 'space-y-1.5',

  /** 正常状态 */
  normal: 'space-y-8'
} as const

/**
 * 卡片/模态框宽度 ClassMap（PC 端）
 * 适用场景：卡片、模态框、弹窗、表单容器
 *
 * ⚠️  PC 端不需要 w-full（不会全屏显示）
 * ✅  直接使用固定最大宽度即可
 */
export const cardWidthClassMap = {
  /** 窄卡片：384px（适合简单表单）*/
  narrow: 'max-w-sm',

  /** 紧凑卡片：448px（适合搜索框等中等宽度场景）*/
  compact: 'max-w-[28rem]',

  /** 半宽卡片：500px（适合搜索框等场景）*/
  half: 'max-w-[31.25rem]',

  /** 标准卡片：448px（最常用）*/
  normal: 'max-w-md',

  /** 中等卡片：512px */
  medium: 'max-w-lg',

  /** 宽卡片：672px（适合复杂表单）*/
  wide: 'max-w-2xl',

  /** 加宽卡片：720px（适合长列表/检索结果）*/
  superWide: 'max-w-[45rem]',

  /** 超宽卡片：896px */
  extraWide: 'max-w-4xl'
} as const

/**
 * 容器高度 ClassMap（PC 端）
 * 适用场景：页面容器、内容区域、滚动容器
 *
 * 参考：src/layouts/responsive/AppLayout.tsx:59（使用 min-h-dvh）
 */
export const containerHeightClassMap = {
  /** 紧凑：240px（适合 Dashboard 卡片）*/
  compact: 'min-h-60',

  /** 标准：360px（最常用）*/
  normal: 'min-h-90',

  /** 高：480px（适合详情页）*/
  tall: 'min-h-[480px]',

  /** 超高：600px */
  extraTall: 'min-h-[600px]',

  /** 全屏：100dvh（PC 端使用 dvh 即可）*/
  fullscreen: 'h-dvh',

  /** 最小全屏：至少占满屏幕，内容超出可滚动 */
  minFullscreen: 'min-h-dvh'
} as const

/**
 * 内容区域最大宽度 ClassMap（PC 端）
 * 适用场景：页面主内容区、表单、列表
 *
 * 参考：src/layouts/responsive/AppLayout.tsx:49（默认 max-w-[1600px]）
 */
export const contentMaxWidthClassMap = {
  /** 阅读宽度：640px（适合长文本阅读）*/
  reading: 'max-w-2xl',

  /** 标准内容宽度：1024px */
  normal: 'max-w-4xl',

  /** 宽屏：1280px */
  wide: 'max-w-6xl',

  /** 超宽：1600px（Dashboard 常用，与 AppLayout 默认值一致）*/
  extraWide: 'max-w-[1600px]',

  /** 特宽：1920px 起扩展（营销/大屏展示）*/
  ultraWide: 'max-w-7xl xl:max-w-[1920px]',

  /** 全宽：不限制最大宽度（适合 4K 显示器）*/
  full: 'max-w-none'
} as const

/**
 * 图标尺寸 Token（数值类型，用于 lucide-react）
 * 适用场景：按钮图标、导航图标、装饰图标
 *
 * 参考：src/layouts/navigation/Sidebar.tsx:67（使用 size={15}）
 */
export const iconSizeToken = {
  /** 极小：12px */
  tiny: 12,

  /** 小：14px（TopNav 常用）*/
  small: 14,

  /** 标准：15px（Sidebar 导航项常用）*/
  normal: 15,

  /** 中等：16px */
  medium: 16,

  /** 大：18px */
  large: 18,

  /** 超大：20px */
  extraLarge: 20,

  /** 巨大：24px */
  huge: 24,

  /** Logo：32px（BrandLogo）*/
  logo: 32
} as const

/**
 * 图标容器尺寸 ClassMap（正方形容器）
 * 适用场景：大型装饰图标容器、表头图标、资产图标等
 *
 * ⚠️ 禁止直接硬编码 w-[72px] h-[72px]
 * ✅ 使用此 ClassMap 的 size-* 类
 */
export const iconContainerSizeClassMap = {
  /** 小：48px */
  small: 'size-12',

  /** 标准：64px */
  normal: 'size-16',

  /** 大：72px（表头大图标常用）*/
  large: 'size-[72px]',

  /** 超大：96px */
  extraLarge: 'size-24'
} as const

export type IconContainerSize = keyof typeof iconContainerSizeClassMap

/**
 * 模态框宽度 ClassMap（PC 端）
 * 适用场景：对话框、弹窗
 *
 * ⚠️  PC 端模态框不需要 w-full（不会全屏）
 * ✅  直接使用固定宽度 + 最大高度限制
 */
export const modalWidthClassMap = {
  /** 迷你模态：400px（简单表单弹窗，如新建单位/修饰符）*/
  mini: 'max-w-[400px]',

  /** 紧凑模态：560px（元数据等表单弹窗默认）*/
  small: 'max-w-[560px]',

  /** 标准模态：640px */
  normal: 'max-w-[640px]',

  /** 大模态：720px */
  large: 'max-w-[720px]',

  /** 超大模态：840px */
  extraLarge: 'max-w-[840px]',

  /** 特大模态：1000px（适合复杂表单）*/
  huge: 'max-w-[1000px] xl:max-w-[1120px] 2xl:max-w-[1280px]',

  /** 响应式模态：大屏幕自动变宽（560px → 680px → 800px）*/
  responsive: 'max-w-[560px] @xl:max-w-[680px] @2xl:max-w-[800px]'
} as const

/**
 * 模态框高度限制 ClassMap（PC 端）
 */
export const modalHeightClassMap = {
  /** 自动高度：根据内容自适应 */
  auto: 'h-auto',

  /** 限制最大高度：90vh（避免超出屏幕）*/
  limited: 'max-h-[90vh]',

  /** 固定高度：占满屏幕 */
  fullscreen: 'h-screen'
} as const

/**
 * 按钮尺寸 ClassMap（PC 端）
 * 适用场景：按钮、操作项
 *
 * ⚠️  按钮尺寸包含高度 + 内边距 + 字体大小，需要配合 TYPOGRAPHY 使用
 */
export const buttonSizeClassMap = {
  /** 极小按钮（用于角落操作、标签按钮等） */
  tiny: 'px-2 py-1 text-micro',

  /** 紧凑按钮（比 tiny 更“可点”，但字体仍保持 micro） */
  compact: 'px-3 py-1.5 text-micro',

  /** 小按钮 */
  small: 'px-3 py-1.5 text-body-sm',

  /** 标准按钮 */
  normal: 'px-4 py-2 text-body',

  /** 大按钮 */
  large: 'px-6 py-2.5 text-body',

  /** 小图标按钮（正方形）*/
  iconSm: 'size-8',

  /** 图标按钮（正方形）*/
  icon: 'size-10'
} as const

/**
 * 圆角尺寸 ClassMap（PC 端）
 * 适用场景：卡片、按钮、输入框
 *
 * 参考：项目普遍使用 rounded-xl、rounded-2xl、rounded-lg
 */
export const radiusClassMap = {
  /** 无圆角 */
  none: 'rounded-none',

  /** 极小圆角：2px */
  tiny: 'rounded-sm',

  /** 小圆角：4px */
  small: 'rounded',

  /** 标准圆角：6px */
  normal: 'rounded-md',

  /** 大圆角：8px（按钮常用）*/
  large: 'rounded-lg',

  /** 超大圆角：12px（卡片常用）*/
  extraLarge: 'rounded-xl',

  /** 超超大圆角：16px（模态框常用）*/
  xxl: 'rounded-2xl',

  /** 完整圆角（头像、徽章）*/
  full: 'rounded-full'
} as const

/**
 * 内边距 ClassMap（PC 端，参考 AppLayout）
 * 适用场景：页面容器、卡片内边距
 *
 * 参考：src/layouts/responsive/AppLayout.tsx:25-30
 */
export const paddingClassMap = {
  /** 无内边距 */
  none: '',

  /** 小内边距：16px/24px */
  sm: 'px-4 py-6 lg:px-6 lg:py-8',

  /** 标准内边距：24px/32px（最常用）*/
  md: 'px-6 py-8 lg:px-10 lg:py-12',

  /** 大内边距：32px/40px */
  lg: 'px-8 py-10 lg:px-12 lg:py-16'
} as const

/**
 * 栅格间距 ClassMap（PC 端）
 * 适用场景：grid、flex 布局的间距
 *
 * 参考：src/layouts/responsive/AdaptiveGrid.tsx:13-19
 */
export const gapClassMap = {
  /** 无间距 */
  none: 'gap-0',

  /** 极小：12px */
  xs: 'gap-3',

  /** 小：16px */
  sm: 'gap-4',

  /** 标准：24px（最常用）*/
  md: 'gap-6',

  /** 大：32px */
  lg: 'gap-8'
} as const

/**
 * PC 端断点语义映射（不再重复定义像素值）
 * 具体像素值以 @theme 中的 --breakpoint-* 为准
 */
export const PC_BREAKPOINTS = {
  /** 2K/QHD：语义对应 lg */
  '2k': 'lg',

  /** 1080p：语义对应 xl */
  fhd: 'xl',

  /** 2560p：语义对应 2xl */
  qhd: '2xl',

  /** 4K：语义对应 3xl */
  '4k': '3xl'
} as const satisfies Record<string, BreakpointKey>

/**
 * TypeScript 类型导出
 */
export type SidebarWidth = keyof typeof sidebarWidthClassMap
export type SidebarPadding = keyof typeof sidebarPaddingClassMap
export type CardWidth = keyof typeof cardWidthClassMap
export type ContainerHeight = keyof typeof containerHeightClassMap
export type ContentMaxWidth = keyof typeof contentMaxWidthClassMap
export type IconSize = keyof typeof iconSizeToken
export type ModalWidth = keyof typeof modalWidthClassMap
export type ModalHeight = keyof typeof modalHeightClassMap
export type ButtonSize = keyof typeof buttonSizeClassMap
export type Radius = keyof typeof radiusClassMap
export type Padding = keyof typeof paddingClassMap
export type Gap = keyof typeof gapClassMap

/**
 * 进度条宽度 ClassMap（用于 Dashboard 视觉占比）
 * 仅用于表示相对填充程度，避免硬编码百分比
 */
export const progressWidthClassMap = {
  /** 低占比（约 60%）*/
  low: 'w-3/5',

  /** 中占比（约 75%）*/
  medium: 'w-3/4',

  /** 高占比（约 92%）*/
  high: 'w-11/12'
} as const

export type ProgressWidth = keyof typeof progressWidthClassMap

/**
 * 面板宽度 ClassMap（窄侧栏/信息卡）
 */
export const panelWidthClassMap = {
  /** 轨道：48px（折叠侧栏/工具栏） */
  rail: 'w-12',

  /** 紧凑面板：240px */
  compact: 'w-60',

  /** 中等面板：256px */
  medium: 'w-64',

  /** 宽面板：288px */
  wide: 'w-72',

  /** 窄面板：320px */
  narrow: 'w-80 max-w-80',

  /** 标准面板：384px */
  normal: 'w-96 max-w-96',

  /** 紧凑面板（响应式）：240px → 288px */
  compactResponsive: 'w-60 lg:w-72',

  /** 中等面板（响应式）：256px → 320px */
  mediumResponsive: 'w-64 lg:w-80',

  /** 协作工单列表宽度（响应式） */
  collaborationList: 'w-collaboration-list-responsive',

  /** 响应式面板：大屏幕自动变宽（320px → 400px → 480px → 560px）
   * 定义在 index.css 中的 .w-panel-responsive
   */
  responsive: 'w-panel-responsive'
} as const

/**
 * 面板高度限制 ClassMap
 */
export const panelHeightClassMap = {
  /** 中等高度：≥360px，PC 端常用 */
  medium: 'min-h-[360px]',

  /** 高度受限：≥360px，最高 55vh/60vh */
  limited: 'min-h-[360px] max-h-[55vh] xl:max-h-[60vh]'
} as const

/**
 * 抽屉宽度 ClassMap（详情侧栏）
 */
export const drawerWidthClassMap = {
  /** 响应式抽屉：480px → 540px → 600px → 680px */
  responsive: 'w-drawer-responsive',

  /** 加宽抽屉：680px → 760px → 900px → 980px */
  wide: 'w-drawer-wide'
} as const

/**
 * 菜单/弹层宽度 ClassMap
 */
export const menuWidthClassMap = {
  /** 极小：144px */
  compact: 'w-36',

  /** 小：160px */
  small: 'w-40',

  /** 中：176px */
  medium: 'w-44',

  /** 大：192px */
  large: 'w-48',

  /** 加大：208px */
  xlarge: 'w-52',

  /** 超大：224px */
  xxlarge: 'w-56',

  /** 超超大：256px */
  xxxlarge: 'w-64',

  /** 宽：288px */
  wide: 'w-72',

  /** 超宽：384px */
  extraWide: 'w-96'
} as const

/**
 * 表格列宽 ClassMap（固定列宽）
 */
export const tableColumnWidthClassMap = {
  /** 极窄：56px */
  xs: 'w-14',

  /** 窄：64px */
  sm: 'w-16',

  /** 小：80px */
  md: 'w-20',

  /** 中：96px */
  lg: 'w-24',

  /** 加宽：128px */
  xl: 'w-32',

  /** 大：160px */
  '2xl': 'w-40',

  /** 特大：176px */
  '3xl': 'w-44',

  /** 超大：208px */
  '4xl': 'w-52',

  /** 极宽：224px */
  '5xl': 'w-56',

  /** 超极宽：256px */
  '6xl': 'w-64'
} as const

/**
 * 装饰性尺寸 ClassMap（非布局容器）
 */
export const surfaceSizeClassMap = {
  /** 小：128px */
  sm: 'w-32 h-32',

  /** 中：160px */
  md: 'w-40 h-40',

  /** 大：192px */
  lg: 'w-48 h-48',

  /** 超大：256px */
  xl: 'w-64 h-64',

  /** 背景光晕（响应式） */
  glow: 'w-48 h-48 @md:w-56 @md:h-56 @lg:w-64 @lg:h-64'
} as const

/**
 * 聊天气泡宽度 ClassMap
 */
export const messageWidthClassMap = {
  /** 默认最大占 80% 宽度 */
  default: 'max-w-[80%]'
} as const

/**
 * 输入框容器宽度 ClassMap（PC 端）
 * 适用场景：页面底部输入框、搜索框容器、AI 对话输入区
 *
 * ⚠️ 禁止用 modalWidthClassMap 设置输入框宽度（语义不符）
 * ✅ 使用此 ClassMap 设置输入框容器的固定宽度
 *
 * 参考现有实现：
 * - TopNav 搜索框：w-56 (224px)
 * - 登录页演示输入框：500px
 * - 知识图谱底部输入框：600px
 */
export const inputContainerWidthClassMap = {
  /** 紧凑：224px（适合顶部导航搜索框，参考 TopNav.tsx）*/
  compact: 'w-56',

  /** 标准：500px（适合登录页演示输入框）*/
  normal: 'w-[500px]',

  /** 宽：600px（适合知识图谱底部输入框）*/
  wide: 'w-[600px]',

  /** 全宽：撑满父容器（适合聊天面板，参考 Chat.tsx）*/
  full: 'w-full'
} as const

export type PanelWidth = keyof typeof panelWidthClassMap
export type PanelHeight = keyof typeof panelHeightClassMap
export type DrawerWidth = keyof typeof drawerWidthClassMap
export type MenuWidth = keyof typeof menuWidthClassMap
export type TableColumnWidth = keyof typeof tableColumnWidthClassMap
export type SurfaceSize = keyof typeof surfaceSizeClassMap
export type MessageWidth = keyof typeof messageWidthClassMap
export type InputContainerWidth = keyof typeof inputContainerWidthClassMap

/**
 * 网格列数 ClassMap（PC 端）
 * 适用场景：Dashboard、列表页面
 */
export const gridColsClassMap = {
  /** 12列网格（最灵活，推荐使用） */
  base: 'grid grid-cols-12'
} as const

/**
 * 响应式列跨度 ClassMap（基于 12 列网格）
 * 适用场景：Dashboard 卡片、面板布局
 *
 * 命名规范：{窄屏列数}to{宽屏列数}
 */
export const colSpanClassMap = {
  /** 全宽：始终占满 12 列 */
  full: 'col-span-12',

  /** 1→2→3 列布局：窄屏 1 列，中屏 2 列，宽屏 3 列（MetricCard 常用） */
  responsive3: 'col-span-12 @md:col-span-6 @lg:col-span-4',

  /** 1→2 列布局：窄屏 1 列，宽屏 2 列 */
  responsive2: 'col-span-12 @lg:col-span-6',

  /** 左侧面板：窄屏全宽，宽屏 5/12，超宽 4/12 */
  leftPanel: 'col-span-12 @lg:col-span-5 @xl:col-span-4',

  /** 右侧面板：窄屏全宽，宽屏 7/12，超宽 8/12 */
  rightPanel: 'col-span-12 @lg:col-span-7 @xl:col-span-8'
} as const

/**
 * 网格行高 ClassMap（PC 端）
 * 适用场景：Dashboard 卡片高度
 */
export const autoRowsClassMap = {
  /** 自适应行高（最小 0，等高） */
  equal: 'auto-rows-[minmax(0,1fr)]',

  /** Dashboard 卡片行高：窄屏 240px，宽屏 280px */
  dashboard: 'auto-rows-[minmax(240px,1fr)] @lg:auto-rows-[minmax(280px,1fr)]'
} as const

export type GridCols = keyof typeof gridColsClassMap
export type ColSpan = keyof typeof colSpanClassMap
export type AutoRows = keyof typeof autoRowsClassMap

/**
 * 渐变边框内边距 ClassMap
 * 用于创建渐变边框效果：外层 gradient + padding，内层白色背景
 *
 * 使用方式：
 * ```tsx
 * <div className="bg-gradient-to-tr from-indigo-500 to-purple-600 p-[1.5px] rounded-full">
 *   <div className="bg-white rounded-full">内容</div>
 * </div>
 * ```
 */
export const gradientBorderClassMap = {
  /** 极细边框：1px */
  thin: 'p-px',

  /** 细边框：1.5px（头像常用） */
  normal: 'p-[1.5px]',

  /** 标准边框：2px（大头像常用） */
  medium: 'p-[2px]'
} as const

export type GradientBorder = keyof typeof gradientBorderClassMap
