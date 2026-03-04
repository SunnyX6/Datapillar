import type { BreakpointKey } from './breakpoints'

/**
 * PC end size specification system(1080p - 4K)
 *
 * design principles:* 1.Based on existing project ClassMap mode
 * 2.State-driven style switching(Reference Sidebar)
 * 3.only consider PC end resolution(1080p/1440p/1920p/4K)
 *
 * Usage:* ```tsx
 * import { sidebarWidthClassMap } from '@/design-tokens/dimensions'
 *
 * // state driven model(Reference Sidebar)
 * const sidebarWidth = collapsed?sidebarWidthClassMap.collapsed:sidebarWidthClassMap.normal
 * <aside className={sidebarWidth} />
 * ```
 */

/**
 * sidebar width ClassMap(state driven model)
 * Reference:src/layouts/navigation/Sidebar.tsx:29
 *
 * ⚠️ Direct hardcoding is prohibited w-[72px],w-[240px]
 * ✅ Must use this ClassMap
 */
export const sidebarWidthClassMap = {
 /** Collapse state:72px(with existing Sidebar consistent)*/
 collapsed:'w-[72px]',/** standard width:Responsive(240px → 280px @ 1920px → 320px @ 2560px)*/
 normal:'w-sidebar-responsive',/** widescreen:320px(suitable for 4K monitor)*/
 wide:'w-80'
} as const

/**
 * sidebar padding ClassMap(state driven model)
 * Reference:src/layouts/navigation/Sidebar.tsx:30
 */
export const sidebarPaddingClassMap = {
 /** Collapse state */
 collapsed:'px-2',/** normal state */
 normal:'px-4'
} as const

/**
 * sidebar spacing ClassMap(state driven model)
 * Reference:src/layouts/navigation/Sidebar.tsx:31
 */
export const sidebarSpacingClassMap = {
 /** Collapse state */
 collapsed:'space-y-1.5',/** normal state */
 normal:'space-y-8'
} as const

/**
 * card/Modal width ClassMap(PC end)
 * Applicable scenarios:card,modal box,Pop-up window,form container
 *
 * ⚠️ PC end is not needed w-full(Will not display full screen)
 * ✅ Just use fixed maximum width directly
 */
export const cardWidthClassMap = {
 /** narrow card:384px(Suitable for simple forms)*/
 narrow:'max-w-sm',/** compact card:448px(Suitable for medium-width scenes such as search boxes)*/
 compact:'max-w-[28rem]',/** half width card:500px(Suitable for scenes such as search boxes)*/
 half:'max-w-[31.25rem]',/** Standard card:448px(Most commonly used)*/
 normal:'max-w-md',/** medium card:512px */
 medium:'max-w-lg',/** wide card:672px(Suitable for complex forms)*/
 wide:'max-w-2xl',/** widen card:720px(Good for long lists/Search results)*/
 superWide:'max-w-[45rem]',/** Extra wide card:896px */
 extraWide:'max-w-4xl'
} as const

/**
 * container height ClassMap(PC end)
 * Applicable scenarios:page container,content area,scroll container
 *
 * Reference:src/layouts/responsive/AppLayout.tsx:59(use min-h-dvh)
 */
export const containerHeightClassMap = {
 /** Compact:240px(suitable for Dashboard card)*/
 compact:'min-h-60',/** Standard:360px(Most commonly used)*/
 normal:'min-h-90',/** high:480px(Suitable for details page)*/
 tall:'min-h-[480px]',/** super high:600px */
 extraTall:'min-h-[600px]',/** full screen:100dvh(PC terminal use dvh That's it)*/
 fullscreen:'h-dvh',/** Minimum full screen:At least fill up the screen,Content exceeds scrollability */
 minFullscreen:'min-h-dvh'
} as const

/**
 * Content area maximum width ClassMap(PC end)
 * Applicable scenarios:Main content area of the page,form,list
 *
 * Reference:src/layouts/responsive/AppLayout.tsx:49(Default max-w-[1600px])
 */
export const contentMaxWidthClassMap = {
 /** reading width:640px(Suitable for long text reading)*/
 reading:'max-w-2xl',/** Standard content width:1024px */
 normal:'max-w-4xl',/** widescreen:1280px */
 wide:'max-w-6xl',/** Extra wide:1600px(Dashboard Commonly used,with AppLayout The default value is the same)*/
 extraWide:'max-w-[1600px]',/** full width:No limit on maximum width(suitable for 4K monitor)*/
 full:'max-w-none'
} as const

/**
 * icon size Token(Numeric type,used for lucide-react)
 * Applicable scenarios:button icon,Navigation icon,decorative icons
 *
 * Reference:src/layouts/navigation/Sidebar.tsx:67(use size={15})
 */
export const iconSizeToken = {
 /** extremely small:12px */
 tiny:12,/** small:14px(TopNav Commonly used)*/
 small:14,/** Standard:15px(Sidebar Commonly used navigation items)*/
 normal:15,/** medium:16px */
 medium:16,/** Big:18px */
 large:18,/** Extra large:20px */
 extraLarge:20,/** huge:24px */
 huge:24,/** Logo:32px(BrandLogo)*/
 logo:32
} as const

/**
 * Icon container size ClassMap(square container)
 * Applicable scenarios:Large decorative icon container,header icon,Asset icons etc.*
 * ⚠️ Direct hardcoding is prohibited w-[72px] h-[72px]
 * ✅ use this ClassMap of size-* class
 */
export const iconContainerSizeClassMap = {
 /** small:48px */
 small:'size-12',/** Standard:64px */
 normal:'size-16',/** Big:72px(Commonly used large icons in table headers)*/
 large:'size-[72px]',/** Extra large:96px */
 extraLarge:'size-24'
} as const

export type IconContainerSize = keyof typeof iconContainerSizeClassMap

/**
 * Modal width ClassMap(PC end)
 * Applicable scenarios:dialog box,Pop-up window
 *
 * ⚠️ PC End modal box is not required w-full(Not full screen)
 * ✅ Use fixed width directly + maximum height limit
 */
export const modalWidthClassMap = {
 /** mini modal:400px(Simple form pop-up window,If you build a new unit/modifier)*/
 mini:'max-w-[400px]',/** compact mode:560px(Metadata and other form pop-ups default)*/
 small:'max-w-[560px]',/** standard mode:640px */
 normal:'max-w-[640px]',/** large mode:720px */
 large:'max-w-[720px]',/** Extra large modal:840px */
 extraLarge:'max-w-[840px]',/** extra large mode:1000px(Suitable for complex forms)*/
 huge:'max-w-[1000px] xl:max-w-[1120px] 2xl:max-w-[1280px]',/** Responsive modal:Automatically widen large screen(560px → 680px → 800px)*/
 responsive:'max-w-[560px] @xl:max-w-[680px] @2xl:max-w-[800px]'
} as const

/**
 * Modal box height limit ClassMap(PC end)
 */
export const modalHeightClassMap = {
 /** automatic height:Adaptable to content */
 auto:'h-auto',/** Limit maximum height:90vh(Avoid going beyond the screen)*/
 limited:'max-h-[90vh]',/** fixed height:fill the screen */
 fullscreen:'h-screen'
} as const

/**
 * button size ClassMap(PC end)
 * Applicable scenarios:button,Action items
 *
 * ⚠️ Button dimensions include height + padding + font size,Need to cooperate TYPOGRAPHY use
 */
export const buttonSizeClassMap = {
 /** Tiny button(for corner operations,Label buttons etc.) */
 tiny:'px-2 py-1 text-micro',/** compact button(Than tiny Update"Can be ordered",But the font remains micro) */
 compact:'px-3 py-1.5 text-micro',/** small button */
 small:'px-3 py-1.5 text-body-sm',/** Standard button */
 normal:'px-4 py-2 text-body',/** big button */
 large:'px-6 py-2.5 text-body',/** small icon button(square)*/
 iconSm:'size-8',/** icon button(square)*/
 icon:'size-10'
} as const

/**
 * Fillet size ClassMap(PC end)
 * Applicable scenarios:card,button,Input box
 *
 * Reference:Commonly used projects rounded-xl,rounded-2xl,rounded-lg
 */
export const radiusClassMap = {
 /** No rounded corners */
 none:'rounded-none',/** Minimal rounded corners:2px */
 tiny:'rounded-sm',/** small rounded corners:4px */
 small:'rounded',/** Standard rounded corners:6px */
 normal:'rounded-md',/** Large rounded corners:8px(Commonly used buttons)*/
 large:'rounded-lg',/** Extra large rounded corners:12px(Commonly used cards)*/
 extraLarge:'rounded-xl',/** Extra large rounded corners:16px(Commonly used modal boxes)*/
 xxl:'rounded-2xl',/** full fillet(avatar,badge)*/
 full:'rounded-full'
} as const

/**
 * padding ClassMap(PC end,Reference AppLayout)
 * Applicable scenarios:page container,card padding
 *
 * Reference:src/layouts/responsive/AppLayout.tsx:25-30
 */
export const paddingClassMap = {
 /** no padding */
 none:'',/** small padding:16px/24px */
 sm:'px-4 py-6 lg:px-6 lg:py-8',/** standard padding:24px/32px(Most commonly used)*/
 md:'px-6 py-8 lg:px-10 lg:py-12',/** Large padding:32px/40px */
 lg:'px-8 py-10 lg:px-12 lg:py-16'
} as const

/**
 * grid spacing ClassMap(PC end)
 * Applicable scenarios:grid,flex layout spacing
 *
 * Reference:src/layouts/responsive/AdaptiveGrid.tsx:13-19
 */
export const gapClassMap = {
 /** no spacing */
 none:'gap-0',/** extremely small:12px */
 xs:'gap-3',/** small:16px */
 sm:'gap-4',/** Standard:24px(Most commonly used)*/
 md:'gap-6',/** Big:32px */
 lg:'gap-8'
} as const

/**
 * PC End breakpoint semantic mapping(No more redefining pixel values)
 * The specific pixel value is @theme in --breakpoint-* Subject to
 */
export const PC_BREAKPOINTS = {
 /** 2K/QHD:semantic correspondence lg */
 '2k':'lg',/** 1080p:semantic correspondence xl */
 fhd:'xl',/** 2560p:semantic correspondence 2xl */
 qhd:'2xl',/** 4K:semantic correspondence 3xl */
 '4k':'3xl'
} as const satisfies Record<string,BreakpointKey>

/**
 * TypeScript Type export
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
 * progress bar width ClassMap(used for Dashboard Visual proportion)
 * Only used to indicate relative filling level,Avoid hardcoding percentages
 */
export const progressWidthClassMap = {
 /** Low proportion(approx.60%)*/
 low:'w-3/5',/** Medium proportion(approx.75%)*/
 medium:'w-3/4',/** High proportion(approx.92%)*/
 high:'w-11/12'
} as const

export type ProgressWidth = keyof typeof progressWidthClassMap

/**
 * Panel width ClassMap(narrow sidebar/Information card)
 */
export const panelWidthClassMap = {
 /** Orbit:48px(Collapse sidebar/Toolbar) */
 rail:'w-12',/** compact panel:240px */
 compact:'w-60',/** medium panel:256px */
 medium:'w-64',/** wide panel:288px */
 wide:'w-72',/** narrow panel:320px */
 narrow:'w-80 max-w-80',/** Standard panel:384px */
 normal:'w-96 max-w-96',/** compact panel(Responsive):240px → 288px */
 compactResponsive:'w-60 lg:w-72',/** medium panel(Responsive):256px → 320px */
 mediumResponsive:'w-64 lg:w-80',/** Collaboration ticket list width(Responsive) */
 collaborationList:'w-collaboration-list-responsive',/** Responsive panel:Automatically widen large screen(320px → 400px → 480px → 560px)
 * defined in index.css in.w-panel-responsive
 */
 responsive:'w-panel-responsive'
} as const

/**
 * Panel height limit ClassMap
 */
export const panelHeightClassMap = {
 /** medium height:≥360px,PC Commonly used */
 medium:'min-h-[360px]',/** height restricted:≥360px,highest 55vh/60vh */
 limited:'min-h-[360px] max-h-[55vh] xl:max-h-[60vh]'
} as const

/**
 * Drawer width ClassMap(Details sidebar)
 */
export const drawerWidthClassMap = {
 /** Responsive drawer:480px → 540px → 600px → 680px */
 responsive:'w-drawer-responsive',/** Widen drawers:680px → 760px → 900px → 980px */
 wide:'w-drawer-wide'
} as const

/**
 * menu/Elastic layer width ClassMap
 */
export const menuWidthClassMap = {
 /** extremely small:144px */
 compact:'w-36',/** small:160px */
 small:'w-40',/** in:176px */
 medium:'w-44',/** Big:192px */
 large:'w-48',/** increase:208px */
 xlarge:'w-52',/** Extra large:224px */
 xxlarge:'w-56',/** Super super big:256px */
 xxxlarge:'w-64',/** wide:288px */
 wide:'w-72',/** Extra wide:384px */
 extraWide:'w-96'
} as const

/**
 * Table column width ClassMap(fixed column width)
 */
export const tableColumnWidthClassMap = {
 /** Extremely narrow:56px */
 xs:'w-14',/** narrow:64px */
 sm:'w-16',/** small:80px */
 md:'w-20',/** in:96px */
 lg:'w-24',/** widen:128px */
 xl:'w-32',/** Big:160px */
 '2xl':'w-40',/** extra large:176px */
 '3xl':'w-44',/** Extra large:208px */
 '4xl':'w-52',/** Extremely wide:224px */
 '5xl':'w-56',/** Super wide:256px */
 '6xl':'w-64'
} as const

/**
 * decorative dimensions ClassMap(non-layout container)
 */
export const surfaceSizeClassMap = {
 /** small:128px */
 sm:'w-32 h-32',/** in:160px */
 md:'w-40 h-40',/** Big:192px */
 lg:'w-48 h-48',/** Extra large:256px */
 xl:'w-64 h-64',/** background glow(Responsive) */
 glow:'w-48 h-48 @md:w-56 @md:h-56 @lg:w-64 @lg:h-64'
} as const

/**
 * Chat bubble width ClassMap
 */
export const messageWidthClassMap = {
 /** The default maximum 80% Width */
 default:'max-w-[80%]'
} as const

/**
 * Input box container width ClassMap(PC end)
 * Applicable scenarios:Input box at bottom of page,search box container,AI Dialogue input area
 *
 * ⚠️ prohibited modalWidthClassMap Set input box width(Semantic inconsistency)
 * ✅ use this ClassMap Set the fixed width of the input box container
 *
 * Reference to existing implementation:* - TopNav search box:w-56 (224px)
 * - Login page demo input box:500px
 * - Input box at the bottom of the knowledge graph:600px
 */
export const inputContainerWidthClassMap = {
 /** Compact:224px(Fits top navigation search box,Reference TopNav.tsx)*/
 compact:'w-56',/** Standard:500px(Suitable for login page demonstration input box)*/
 normal:'w-[500px]',/** wide:600px(Suitable for the input box at the bottom of the knowledge graph)*/
 wide:'w-[600px]',/** full width:Fill the parent container(Suitable for chat panels,Reference Chat.tsx)*/
 full:'w-full'
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
 * Number of grid columns ClassMap(PC end)
 * Applicable scenarios:Dashboard,List page
 */
export const gridColsClassMap = {
 /** 12column grid(most flexible,Recommended) */
 base:'grid grid-cols-12'
} as const

/**
 * Responsive column span ClassMap(Based on 12 column grid)
 * Applicable scenarios:Dashboard card,Panel layout
 *
 * Naming convention:{Number of narrow screen columns}to{Number of widescreen columns}
 */
export const colSpanClassMap = {
 /** full width:always occupied 12 Column */
 full:'col-span-12',/** 1→2→3 column layout:narrow screen 1 Column,Center screen 2 Column,widescreen 3 Column(MetricCard Commonly used) */
 responsive3:'col-span-12 @md:col-span-6 @lg:col-span-4',/** 1→2 column layout:narrow screen 1 Column,widescreen 2 Column */
 responsive2:'col-span-12 @lg:col-span-6',/** left panel:narrow screen full width,widescreen 5/12,Extra wide 4/12 */
 leftPanel:'col-span-12 @lg:col-span-5 @xl:col-span-4',/** right panel:narrow screen full width,widescreen 7/12,Extra wide 8/12 */
 rightPanel:'col-span-12 @lg:col-span-7 @xl:col-span-8'
} as const

/**
 * Grid row height ClassMap(PC end)
 * Applicable scenarios:Dashboard card height
 */
export const autoRowsClassMap = {
 /** Adaptive line height(smallest 0,Equal height) */
 equal:'auto-rows-[minmax(0,1fr)]',/** Dashboard Card row height:narrow screen 240px,widescreen 280px */
 dashboard:'auto-rows-[minmax(240px,1fr)] @lg:auto-rows-[minmax(280px,1fr)]'
} as const

export type GridCols = keyof typeof gridColsClassMap
export type ColSpan = keyof typeof colSpanClassMap
export type AutoRows = keyof typeof autoRowsClassMap

/**
 * Gradient border padding ClassMap
 * Used to create a gradient border effect:Outer layer gradient + padding,inner white background
 *
 * Usage:* ```tsx
 * <div className="bg-gradient-to-tr from-indigo-500 to-purple-600 p-[1.5px] rounded-full">
 * <div className="bg-white rounded-full">content</div>
 * </div>
 * ```
 */
export const gradientBorderClassMap = {
 /** Very thin bezel:1px */
 thin:'p-px',/** Thin borders:1.5px(Commonly used avatars) */
 normal:'p-[1.5px]',/** Standard border:2px(Commonly used for big avatars) */
 medium:'p-[2px]'
} as const

export type GradientBorder = keyof typeof gradientBorderClassMap
