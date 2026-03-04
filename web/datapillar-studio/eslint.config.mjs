import js from '@eslint/js'
import importPlugin from 'eslint-plugin-import'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

const deprecatedDimensionImport = {
 name:'@/design-tokens/dimensions',importNames:['LAYOUT_DIMENSIONS'],message:'❌ Deprecated LAYOUT_DIMENSIONS,Please use dimensions.ts in ClassMap(cardWidthClassMap / modalWidthClassMap / contentMaxWidthClassMap / paddingClassMap / gapClassMap / sidebarWidthClassMap Wait).'
}

const baseRestrictedPatterns = ['@/lib/*','@/shared/*','@/features/**/api/**','@/features/**/service/**','@/features/**/lib/**','@/router/access/routeAccess','@/router/routes/*','@/stores/*','@/contexts/*','@/types/*',]

function createRestrictedImports(extraPatterns = [],extraPaths = [],extraPatternObjects = []) {
 const patternGroup = [...baseRestrictedPatterns,...extraPatterns]
 const patterns = [...(patternGroup.length > 0?[{ group:patternGroup }]:[]),...extraPatternObjects,]

 return ['error',{
 paths:[deprecatedDimensionImport,...extraPaths],patterns,}]
}

export default tseslint.config({ ignores:['dist','node_modules','*.config.js','*.config.ts','examples'] },{
 extends:[js.configs.recommended,...tseslint.configs.recommended],files:['**/*.{ts,tsx}'],languageOptions:{
 ecmaVersion:2020,globals:globals.browser,},plugins:{
 import:importPlugin,'react-hooks':reactHooks,'react-refresh':reactRefresh,},rules:{...reactHooks.configs.recommended.rules,'react-refresh/only-export-components':['warn',{ allowConstantExport:true },],'@typescript-eslint/no-explicit-any':'warn','@typescript-eslint/no-unused-vars':['warn',{
 argsIgnorePattern:'^_',varsIgnorePattern:'^_'
 }],'import/no-cycle':['error',{ maxDepth:1 }],'import/no-self-import':'error','no-restricted-imports':createRestrictedImports(),// ==================== Responsive enforcement rules ====================
 // target:Only focus on business logic during development,Responsive automatic adaptation
 'no-restricted-syntax':['error',// rules 1:prohibited style Property hardcoded dimensions
 {
 selector:'JSXAttribute[name.name="style"] ObjectExpression Property[key.name=/^(width|height|maxWidth|maxHeight|minWidth|minHeight)$/]',message:'❌ prohibited from style Medium hardcoded size!\n\nPlease use:\n 1.LAYOUT_DIMENSIONS Token(Recommended)\n import { LAYOUT_DIMENSIONS } from "@/design-tokens/dimensions"\n className={LAYOUT_DIMENSIONS.sidebar.responsive}\n\n 2.Tailwind Responsive class name:\n className="w-full sm:w-80 lg:w-96"\n\n 3.Responsive Hook:\n const { getSidebarWidth } = useResponsive()\n style={{ width:getSidebarWidth() }}'
 },// rules 2:prohibited style Property hardcoded spacing
 {
 selector:'JSXAttribute[name.name="style"] ObjectExpression Property[key.name=/^(padding|margin|gap|paddingTop|paddingBottom|paddingLeft|paddingRight|marginTop|marginBottom|marginLeft|marginRight)$/][value.raw]',message:'❌ prohibited from style Medium hardcoded spacing!\n\nPlease use Tailwind responsive spacing:\n className="px-4 sm:px-6 lg:px-8 py-6"'
 },// rules 3:prohibited style Medium hardcoded font size
 {
 selector:'JSXAttribute[name.name="style"] ObjectExpression Property[key.name="fontSize"]',message:'❌ prohibited from style Medium hardcoded fontSize!\n\n✅ please use font Token:\n - text-display (28px) - Maximum title\n - text-title (22px) - Title\n - text-heading (18px) - Subtitle\n - text-subtitle (16px) - subtitle\n - text-body (14px) - Text\n - text-body-sm (13px) - small text\n - text-caption (12px) - Description text\n - text-micro (10px) - Very small text'
 },// rules 4:prohibited from className Use any pixel size class in(w-[...px]/p-[...px]/gap-[...px] Wait)
 // exclude:CSS variable var(--...),viewport units vh/vw/dvh/dvw,Percentage %
 // exclude:Positioning attribute top/bottom/left/right/inset(animation/layout specific)
 // exclude:Extremely small size min-h-[18px] Wait(Minimum height of inline elements)
 {
 selector:'JSXAttribute[name.name="className"] Literal[value=/\\b(?:w|max-w|min-w|gap|p[trblxy]?|m[trblxy]?|space-[xy])-\\[\\d+(?:\\.\\d+)?px\\]/]',message:'❌ prohibited from className Medium hardcoded pixel size(w-[...px]/gap-[...px]/p-[...px]).\n\n✅ Please use dimensions.ts in ClassMap/Token:\n - width and height:contentMaxWidthClassMap / containerHeightClassMap / cardWidthClassMap / modalWidthClassMap\n - spacing:paddingClassMap / gapClassMap / radiusClassMap\n - sidebar:sidebarWidthClassMap / sidebarPaddingClassMap / sidebarSpacingClassMap'
 },{
 selector:'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\b(?:w|max-w|min-w|gap|p[trblxy]?|m[trblxy]?|space-[xy])-\\[\\d+(?:\\.\\d+)?px\\]/]',message:'❌ prohibited from className Medium hardcoded pixel size(w-[...px]/gap-[...px]/p-[...px]).\n\n✅ Please use dimensions.ts in ClassMap/Token:\n - width and height:contentMaxWidthClassMap / containerHeightClassMap / cardWidthClassMap / modalWidthClassMap\n - spacing:paddingClassMap / gapClassMap / radiusClassMap\n - sidebar:sidebarWidthClassMap / sidebarPaddingClassMap / sidebarSpacingClassMap'
 },// rules 5:Disable the use of mobile breakpoints xs/sm(PC end is not needed)
 {
 selector:'JSXAttribute[name.name="className"] Literal[value=/\\b(?:xs|sm):/]',message:'❌ prohibited from className Using mobile breakpoints in xs:/sm:.PC Please use @container(@md/@lg/@xl/@2xl)or dimensions.ts of ClassMap.'
 },{
 selector:'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\b(?:xs|sm):/]',message:'❌ prohibited from className Using mobile breakpoints in xs:/sm:.PC Please use @container(@md/@lg/@xl/@2xl)or dimensions.ts of ClassMap.'
 },// rules 6:prohibited text-[...] Any font class,Mandatory use TYPOGRAPHY Token
 {
 selector:'JSXAttribute[name.name="className"] Literal[value=/\\btext-\\[/]',message:'❌ prohibited text-[...] any font size,Please use TYPOGRAPHY Token(text-display / text-title / text-heading / text-body / text-body-sm Wait).'
 },{
 selector:'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\btext-\\[/]',message:'❌ prohibited text-[...] any font size,Please use TYPOGRAPHY Token(text-display / text-title / text-heading / text-body / text-body-sm Wait).'
 }]
 },},{
 files:['src/pages/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/api/*','@/services/*'])
 }
 },{
 files:['src/layouts/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/pages/*','@/features/**','@/api/**'],[],[{
 regex:'^@/services/(?!types/|menuPermissionService$).+',message:'❌ layouts can only rely on services type definition or menuPermissionService,Disable access to business service calls.'
 }])
 }
 },{
 files:['src/state/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/features/*'])
 }
 },{
 files:['src/state/**/*.{ts,tsx}'],ignores:['src/state/authStore.ts','src/state/setupStore.ts','src/state/themeStore.ts','src/state/i18nStore.ts','src/state/layoutStore.ts','src/state/searchStore.ts','src/state/index.ts',],rules:{
 'no-restricted-syntax':['error',{
 selector:'Program',message:'❌ src/state Only allow global sharing of state files.The business module status must be placed in src/features/*/state.'
 }]
 }
 },{
 files:['src/features/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/pages/*','@/layouts/navigation/*','@/layouts/MainLayout','@/layouts/MainLayout/*'],[{
 name:'@/state',importNames:['useWorkflowStudioStore','useWorkflowStudioCacheStore','useComponentStore','useMetadataStore','useKnowledgeGraphStore','useSemanticStatsStore','AgentActivity','ProcessActivity','ChatMessage'],message:'❌ Please check the business status from the corresponding feature of state Directory import,not allowed from @/state import.'
 }])
 }
 },{
 files:['src/features/**/utils/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/api/*','@/services/*'])
 }
 },{
 files:['src/services/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/pages/*','@/layouts/*','@/features/*'])
 }
 },{
 files:['src/api/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/pages/*','@/layouts/*','@/features/*','@/services/*','@/components/*'])
 }
 },{
 files:['src/utils/**/*.{ts,tsx}','src/hooks/**/*.{ts,tsx}','src/config/**/*.{ts,tsx}','src/design-tokens/**/*.{ts,tsx}'],rules:{
 'no-restricted-imports':createRestrictedImports(['@/app/*','@/router/*','@/pages/*','@/layouts/*','@/features/*','@/services/*','@/api/*','@/components/*'])
 }
 },)
