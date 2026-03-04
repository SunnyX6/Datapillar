/**
 * Font specification system
 *
 * Based on src/index.css Fonts defined in CSS variable
 * provide TypeScript Type support and autocompletion
 *
 * Usage:* ```tsx
 * import { TYPOGRAPHY } from '@/design-tokens/typography'
 *
 * // Use predefined class names
 * <h1 className={TYPOGRAPHY.title}>Title</h1>
 * <p className={TYPOGRAPHY.body}>Text</p>
 * <span className={TYPOGRAPHY.caption}>Description text</span>
 * ```
 *
 * CSS variable definition(src/index.css):* ```css
 *:root {
 * --font-display:700 28px/34px var(--font-family-sans);* --font-title:700 22px/28px var(--font-family-sans);* --font-heading:600 18px/24px var(--font-family-sans);* --font-subtitle:600 16px/22px var(--font-family-sans);* --font-body:500 14px/20px var(--font-family-sans);* --font-body-sm:500 13px/18px var(--font-family-sans);* --font-caption:500 12px/16px var(--font-family-sans);* --font-micro:600 10px/14px var(--font-family-sans);* }
 * ```
 */

/**
 * Font class name mapping
 * All class names are present src/index.css in definition
 */
export const TYPOGRAPHY = {
 /** Maximum title:28px / 700 Bold / row height 34px */
 display:'text-display',/** headline:26px / 700 Bold / row height 32px */
 displaySm:'text-display-sm',/** Title:22px / 700 Bold / row height 28px */
 title:'text-title',/** Subtitle:18px / 600 semi-thick / row height 24px */
 heading:'text-heading',/** subtitle:16px / 600 semi-thick / row height 22px */
 subtitle:'text-subtitle',/** Text(Most commonly used):14px / 500 medium / row height 20px */
 body:'text-body',/** small text:13px / 500 medium / row height 18px */
 bodySm:'text-body-sm',/** Super small text:12.5px / 500 medium / row height 17px(between bodySm with caption between) */
 bodyXs:'text-body-xs',/** Description text:12px / 500 medium / row height 16px */
 caption:'text-caption',/** Very small text:10px / 600 semi-thick / row height 14px */
 micro:'text-micro',/** law/logo:11px / 600 / row height 14px */
 legal:'text-legal',/** Extra small logo:9px / 600 / row height 12px */
 nano:'text-nano',/** Right-click menu title:8px / 600 / row height 10px(>=1920:9px / 11px) */
 contextMenuTitle:'context-menu-title',/** minimal logo:8px / 600 / row height 10px */
 tiny:'text-tiny',/** minimal logo:7px / 600 / row height 9px */
 mini:'text-mini'
} as const

/**
 * Font size(numerical value,for dynamic calculations or style Properties)
 */
export const FONT_SIZE = {
 display:28,displaySm:26,title:22,heading:18,subtitle:16,body:14,bodySm:13,bodyXs:12.5,caption:12,micro:10,legal:11,nano:9,contextMenuTitle:8,tiny:8,mini:7
} as const

/**
 * Font line height(numerical value)
 */
export const LINE_HEIGHT = {
 display:34,title:28,heading:24,subtitle:22,body:20,bodySm:18,bodyXs:17,caption:16,micro:14,legal:14,nano:12,contextMenuTitle:10,tiny:10,mini:9
} as const

/**
 * Font weight
 */
export const FONT_WEIGHT = {
 display:700,title:700,heading:600,subtitle:600,body:500,bodySm:500,bodyXs:500,caption:500,micro:600,legal:600,nano:600,contextMenuTitle:600,tiny:600,mini:600
} as const

/**
 * Recommended font usage scenarios
 */
export const TYPOGRAPHY_USE_CASES = {
 /** Page main title */
 pageTitle:TYPOGRAPHY.display,/** card title,Modal box title */
 cardTitle:TYPOGRAPHY.title,/** Section title,Subtitle */
 sectionTitle:TYPOGRAPHY.heading,/** subtitle,descriptive title */
 subtitle:TYPOGRAPHY.subtitle,/** Text,form tag */
 body:TYPOGRAPHY.body,/** secondary text,Auxiliary text */
 bodySecondary:TYPOGRAPHY.bodySm,/** Prompt text,Description text */
 hint:TYPOGRAPHY.caption,/** label,badge,corner mark */
 badge:TYPOGRAPHY.micro,/** Right-click menu title */
 contextMenuTitle:TYPOGRAPHY.contextMenuTitle
} as const

/**
 * Responsive font combination(Suitable for scenarios where different fonts need to be used on different screen sizes)
 *
 * Naming convention:* - Use semantic naming,Describe usage scenarios rather than specific dimensions
 * - Format:{scene}{Optional modifiers}
 */
export const RESPONSIVE_TYPOGRAPHY = {
 /** Responsive page title:narrow screen 22px,Desktop 28px */
 pageTitle:'text-title @md:text-display',/** Responsive headline:narrow screen 24px,Desktop 30px(Dashboard main title) */
 displayTitle:'text-2xl @md:text-3xl',/** Responsive card title:narrow screen 18px,Desktop 22px */
 cardTitle:'text-heading @md:text-title',/** Responsive section title:narrow screen 14px,Desktop 16px */
 sectionTitle:'text-sm @md:text-base',/** Responsive subtitle:narrow screen 12px,Desktop 14px */
 subtitle:'text-xs @md:text-sm',/** Responsive body:narrow screen 13px,Desktop 14px */
 body:'text-body-sm @md:text-body',/** Responsive indicator value:narrow screen 24px,Desktop 26px */
 metricValue:'text-2xl @md:text-display-sm',/** Responsive label text:narrow screen 13px,Desktop 14px */
 label:'text-body-sm @md:text-sm',/** Responsive badge/label:narrow screen 11px,Desktop 12px */
 badge:'text-legal @md:text-xs',/** Responsive header/Timestamp:narrow screen 10px,Desktop 11px */
 tableHeader:'text-micro @md:text-legal',/** Responsive legend:narrow screen 10px,Desktop 12px */
 legend:'text-micro @md:text-xs',/** Responsive minimal label:narrow screen 9px,Desktop 10px */
 tag:'text-nano @md:text-micro'
} as const

/**
 * Type export(used for TypeScript type inference)
 */
export type TypographyClass = typeof TYPOGRAPHY[keyof typeof TYPOGRAPHY]
export type TypographyUseCase = keyof typeof TYPOGRAPHY_USE_CASES
export type ResponsiveTypography = keyof typeof RESPONSIVE_TYPOGRAPHY
