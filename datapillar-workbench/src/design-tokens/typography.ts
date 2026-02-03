/**
 * 字体规范系统
 *
 * 基于 src/index.css 中定义的字体 CSS 变量
 * 提供 TypeScript 类型支持和自动补全
 *
 * 使用方式：
 * ```tsx
 * import { TYPOGRAPHY } from '@/design-tokens/typography'
 *
 * // 使用预定义的类名
 * <h1 className={TYPOGRAPHY.title}>标题</h1>
 * <p className={TYPOGRAPHY.body}>正文</p>
 * <span className={TYPOGRAPHY.caption}>说明文字</span>
 * ```
 *
 * CSS 变量定义（src/index.css）：
 * ```css
 * :root {
 *   --font-display: 700 28px/34px var(--font-family-sans);
 *   --font-title: 700 22px/28px var(--font-family-sans);
 *   --font-heading: 600 18px/24px var(--font-family-sans);
 *   --font-subtitle: 600 16px/22px var(--font-family-sans);
 *   --font-body: 500 14px/20px var(--font-family-sans);
 *   --font-body-sm: 500 13px/18px var(--font-family-sans);
 *   --font-caption: 500 12px/16px var(--font-family-sans);
 *   --font-micro: 600 10px/14px var(--font-family-sans);
 * }
 * ```
 */

/**
 * 字体类名映射
 * 所有类名都已在 src/index.css 中定义
 */
export const TYPOGRAPHY = {
  /** 最大标题：28px / 700 粗体 / 行高 34px */
  display: 'text-display',

  /** 大标题：26px / 700 粗体 / 行高 32px */
  displaySm: 'text-display-sm',

  /** 标题：22px / 700 粗体 / 行高 28px */
  title: 'text-title',

  /** 小标题：18px / 600 半粗 / 行高 24px */
  heading: 'text-heading',

  /** 副标题：16px / 600 半粗 / 行高 22px */
  subtitle: 'text-subtitle',

  /** 正文（最常用）：14px / 500 中等 / 行高 20px */
  body: 'text-body',

  /** 小正文：13px / 500 中等 / 行高 18px */
  bodySm: 'text-body-sm',

  /** 超小正文：12.5px / 500 中等 / 行高 17px（介于 bodySm 与 caption 之间） */
  bodyXs: 'text-body-xs',

  /** 说明文字：12px / 500 中等 / 行高 16px */
  caption: 'text-caption',

  /** 极小文字：10px / 600 半粗 / 行高 14px */
  micro: 'text-micro',

  /** 法律/徽标：11px / 600 / 行高 14px */
  legal: 'text-legal',

  /** 超小徽标：9px / 600 / 行高 12px */
  nano: 'text-nano',

  /** 右键菜单标题：8px / 600 / 行高 10px（>=1920：9px / 11px） */
  contextMenuTitle: 'context-menu-title',

  /** 极微徽标：8px / 600 / 行高 10px */
  tiny: 'text-tiny',

  /** 最小徽标：7px / 600 / 行高 9px */
  mini: 'text-mini'
} as const

/**
 * 字体尺寸（数值，用于动态计算或 style 属性）
 */
export const FONT_SIZE = {
  display: 28,
  displaySm: 26,
  title: 22,
  heading: 18,
  subtitle: 16,
  body: 14,
  bodySm: 13,
  bodyXs: 12.5,
  caption: 12,
  micro: 10,
  legal: 11,
  nano: 9,
  contextMenuTitle: 8,
  tiny: 8,
  mini: 7
} as const

/**
 * 字体行高（数值）
 */
export const LINE_HEIGHT = {
  display: 34,
  title: 28,
  heading: 24,
  subtitle: 22,
  body: 20,
  bodySm: 18,
  bodyXs: 17,
  caption: 16,
  micro: 14,
  legal: 14,
  nano: 12,
  contextMenuTitle: 10,
  tiny: 10,
  mini: 9
} as const

/**
 * 字体粗细
 */
export const FONT_WEIGHT = {
  display: 700,
  title: 700,
  heading: 600,
  subtitle: 600,
  body: 500,
  bodySm: 500,
  bodyXs: 500,
  caption: 500,
  micro: 600,
  legal: 600,
  nano: 600,
  contextMenuTitle: 600,
  tiny: 600,
  mini: 600
} as const

/**
 * 字体使用场景推荐
 */
export const TYPOGRAPHY_USE_CASES = {
  /** 页面主标题 */
  pageTitle: TYPOGRAPHY.display,

  /** 卡片标题、模态框标题 */
  cardTitle: TYPOGRAPHY.title,

  /** 分区标题、小标题 */
  sectionTitle: TYPOGRAPHY.heading,

  /** 副标题、说明性标题 */
  subtitle: TYPOGRAPHY.subtitle,

  /** 正文、表单标签 */
  body: TYPOGRAPHY.body,

  /** 次要正文、辅助文字 */
  bodySecondary: TYPOGRAPHY.bodySm,

  /** 提示文字、说明文字 */
  hint: TYPOGRAPHY.caption,

  /** 标签、徽章、角标 */
  badge: TYPOGRAPHY.micro,

  /** 右键菜单标题 */
  contextMenuTitle: TYPOGRAPHY.contextMenuTitle
} as const

/**
 * 响应式字体组合（适用于需要在不同屏幕尺寸下使用不同字体的场景）
 *
 * 命名规范：
 * - 使用语义化命名，描述使用场景而非具体尺寸
 * - 格式：{场景}{可选修饰符}
 */
export const RESPONSIVE_TYPOGRAPHY = {
  /** 响应式页面标题：窄屏 22px，桌面 28px */
  pageTitle: 'text-title @md:text-display',

  /** 响应式大标题：窄屏 24px，桌面 30px（Dashboard 主标题） */
  displayTitle: 'text-2xl @md:text-3xl',

  /** 响应式卡片标题：窄屏 18px，桌面 22px */
  cardTitle: 'text-heading @md:text-title',

  /** 响应式分区标题：窄屏 14px，桌面 16px */
  sectionTitle: 'text-sm @md:text-base',

  /** 响应式小标题：窄屏 12px，桌面 14px */
  subtitle: 'text-xs @md:text-sm',

  /** 响应式正文：窄屏 13px，桌面 14px */
  body: 'text-body-sm @md:text-body',

  /** 响应式指标数值：窄屏 24px，桌面 26px */
  metricValue: 'text-2xl @md:text-display-sm',

  /** 响应式标签文字：窄屏 13px，桌面 14px */
  label: 'text-body-sm @md:text-sm',

  /** 响应式徽章/标签：窄屏 11px，桌面 12px */
  badge: 'text-legal @md:text-xs',

  /** 响应式表头/时间戳：窄屏 10px，桌面 11px */
  tableHeader: 'text-micro @md:text-legal',

  /** 响应式图例：窄屏 10px，桌面 12px */
  legend: 'text-micro @md:text-xs',

  /** 响应式极小标签：窄屏 9px，桌面 10px */
  tag: 'text-nano @md:text-micro'
} as const

/**
 * 类型导出（用于 TypeScript 类型推导）
 */
export type TypographyClass = typeof TYPOGRAPHY[keyof typeof TYPOGRAPHY]
export type TypographyUseCase = keyof typeof TYPOGRAPHY_USE_CASES
export type ResponsiveTypography = keyof typeof RESPONSIVE_TYPOGRAPHY
