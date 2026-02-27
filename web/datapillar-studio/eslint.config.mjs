import js from '@eslint/js'
import importPlugin from 'eslint-plugin-import'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

const deprecatedDimensionImport = {
  name: '@/design-tokens/dimensions',
  importNames: ['LAYOUT_DIMENSIONS'],
  message: '❌ 已废弃 LAYOUT_DIMENSIONS，请使用 dimensions.ts 中的 ClassMap（cardWidthClassMap / modalWidthClassMap / contentMaxWidthClassMap / paddingClassMap / gapClassMap / sidebarWidthClassMap 等）。'
}

const baseRestrictedPatterns = [
  '@/lib/*',
  '@/shared/*',
  '@/features/**/api/**',
  '@/features/**/service/**',
  '@/features/**/lib/**',
  '@/router/access/routeAccess',
  '@/router/routes/*',
  '@/stores/*',
  '@/contexts/*',
  '@/types/*',
]

function createRestrictedImports(extraPatterns = [], extraPaths = [], extraPatternObjects = []) {
  const patternGroup = [...baseRestrictedPatterns, ...extraPatterns]
  const patterns = [
    ...(patternGroup.length > 0 ? [{ group: patternGroup }] : []),
    ...extraPatternObjects,
  ]

  return ['error', {
    paths: [deprecatedDimensionImport, ...extraPaths],
    patterns,
  }]
}

export default tseslint.config(
  { ignores: ['dist', 'node_modules', '*.config.js', '*.config.ts', 'examples'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      import: importPlugin,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_'
      }],
      'import/no-cycle': ['error', { maxDepth: 1 }],
      'import/no-self-import': 'error',
      'no-restricted-imports': createRestrictedImports(),

      // ==================== 响应式强制规则 ====================
      // 目标：开发时只关注业务逻辑，响应式自动适配
      'no-restricted-syntax': [
        'error',
        // 规则 1：禁止 style 属性硬编码尺寸
        {
          selector: 'JSXAttribute[name.name="style"] ObjectExpression Property[key.name=/^(width|height|maxWidth|maxHeight|minWidth|minHeight)$/]',
          message: '❌ 禁止在 style 中硬编码尺寸！\n\n请使用：\n  1. LAYOUT_DIMENSIONS Token（推荐）\n     import { LAYOUT_DIMENSIONS } from "@/design-tokens/dimensions"\n     className={LAYOUT_DIMENSIONS.sidebar.responsive}\n\n  2. Tailwind 响应式类名：\n     className="w-full sm:w-80 lg:w-96"\n\n  3. 响应式 Hook：\n     const { getSidebarWidth } = useResponsive()\n     style={{ width: getSidebarWidth() }}'
        },

        // 规则 2：禁止 style 属性硬编码间距
        {
          selector: 'JSXAttribute[name.name="style"] ObjectExpression Property[key.name=/^(padding|margin|gap|paddingTop|paddingBottom|paddingLeft|paddingRight|marginTop|marginBottom|marginLeft|marginRight)$/][value.raw]',
          message: '❌ 禁止在 style 中硬编码间距！\n\n请使用 Tailwind 响应式间距：\n  className="px-4 sm:px-6 lg:px-8 py-6"'
        },

        // 规则 3：禁止 style 中硬编码字体大小
        {
          selector: 'JSXAttribute[name.name="style"] ObjectExpression Property[key.name="fontSize"]',
          message: '❌ 禁止在 style 中硬编码 fontSize！\n\n✅ 请使用字体 Token：\n  - text-display (28px)  - 最大标题\n  - text-title (22px)    - 标题\n  - text-heading (18px)  - 小标题\n  - text-subtitle (16px) - 副标题\n  - text-body (14px)     - 正文\n  - text-body-sm (13px)  - 小正文\n  - text-caption (12px)  - 说明文字\n  - text-micro (10px)    - 极小文字'
        },

        // 规则 4：禁止在 className 中使用任意像素尺寸类（w-[...px]/p-[...px]/gap-[...px] 等）
        // 排除：CSS 变量 var(--...)、视口单位 vh/vw/dvh/dvw、百分比 %
        // 排除：定位属性 top/bottom/left/right/inset（动画/布局特定）
        // 排除：极小尺寸 min-h-[18px] 等（行内元素最小高度）
        {
          selector: 'JSXAttribute[name.name="className"] Literal[value=/\\b(?:w|max-w|min-w|gap|p[trblxy]?|m[trblxy]?|space-[xy])-\\[\\d+(?:\\.\\d+)?px\\]/]',
          message: '❌ 禁止在 className 中硬编码像素尺寸（w-[...px]/gap-[...px]/p-[...px]）。\n\n✅ 请使用 dimensions.ts 中的 ClassMap/Token：\n  - 宽高：contentMaxWidthClassMap / containerHeightClassMap / cardWidthClassMap / modalWidthClassMap\n  - 间距：paddingClassMap / gapClassMap / radiusClassMap\n  - 侧边栏：sidebarWidthClassMap / sidebarPaddingClassMap / sidebarSpacingClassMap'
        },
        {
          selector: 'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\b(?:w|max-w|min-w|gap|p[trblxy]?|m[trblxy]?|space-[xy])-\\[\\d+(?:\\.\\d+)?px\\]/]',
          message: '❌ 禁止在 className 中硬编码像素尺寸（w-[...px]/gap-[...px]/p-[...px]）。\n\n✅ 请使用 dimensions.ts 中的 ClassMap/Token：\n  - 宽高：contentMaxWidthClassMap / containerHeightClassMap / cardWidthClassMap / modalWidthClassMap\n  - 间距：paddingClassMap / gapClassMap / radiusClassMap\n  - 侧边栏：sidebarWidthClassMap / sidebarPaddingClassMap / sidebarSpacingClassMap'
        },

        // 规则 5：禁止使用移动端断点 xs/sm（PC 端不需要）
        {
          selector: 'JSXAttribute[name.name="className"] Literal[value=/\\b(?:xs|sm):/]',
          message: '❌ 禁止在 className 中使用移动端断点 xs:/sm:。PC 端请使用 @container（@md/@lg/@xl/@2xl）或 dimensions.ts 的 ClassMap。'
        },
        {
          selector: 'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\b(?:xs|sm):/]',
          message: '❌ 禁止在 className 中使用移动端断点 xs:/sm:。PC 端请使用 @container（@md/@lg/@xl/@2xl）或 dimensions.ts 的 ClassMap。'
        },

        // 规则 6：禁止 text-[...] 任意字体类，强制使用 TYPOGRAPHY Token
        {
          selector: 'JSXAttribute[name.name="className"] Literal[value=/\\btext-\\[/]',
          message: '❌ 禁止 text-[...] 任意字体尺寸，请使用 TYPOGRAPHY Token（text-display / text-title / text-heading / text-body / text-body-sm 等）。'
        },
        {
          selector: 'JSXAttribute[name.name="className"] TemplateLiteral TemplateElement[value.raw=/\\btext-\\[/]',
          message: '❌ 禁止 text-[...] 任意字体尺寸，请使用 TYPOGRAPHY Token（text-display / text-title / text-heading / text-body / text-body-sm 等）。'
        }
      ]
    },
  },
  {
    files: ['src/pages/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/app/*', '@/router/*', '@/api/*', '@/services/*'])
    }
  },
  {
    files: ['src/layouts/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports([
        '@/app/*',
        '@/router/*',
        '@/pages/*',
        '@/features/**',
        '@/api/**'
      ], [], [{
        regex: '^@/services/(?!types/|menuPermissionService$).+',
        message: '❌ layouts 只能依赖 services 的类型定义或 menuPermissionService，禁止接入业务服务调用。'
      }])
    }
  },
  {
    files: ['src/state/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/features/*'])
    }
  },
  {
    files: ['src/state/**/*.{ts,tsx}'],
    ignores: [
      'src/state/authStore.ts',
      'src/state/setupStore.ts',
      'src/state/themeStore.ts',
      'src/state/i18nStore.ts',
      'src/state/layoutStore.ts',
      'src/state/searchStore.ts',
      'src/state/index.ts',
    ],
    rules: {
      'no-restricted-syntax': ['error', {
        selector: 'Program',
        message: '❌ src/state 只允许全局共享状态文件。业务模块状态必须放到 src/features/*/state。'
      }]
    }
  },
  {
    files: ['src/features/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports([
        '@/app/*',
        '@/router/*',
        '@/pages/*',
        '@/layouts/navigation/*',
        '@/layouts/MainLayout',
        '@/layouts/MainLayout/*'
      ], [
        {
          name: '@/state',
          importNames: [
            'useWorkflowStudioStore',
            'useWorkflowStudioCacheStore',
            'useComponentStore',
            'useMetadataStore',
            'useKnowledgeGraphStore',
            'useSemanticStatsStore',
            'AgentActivity',
            'ProcessActivity',
            'ChatMessage'
          ],
          message: '❌ 业务状态请从对应 feature 的 state 目录导入，不允许从 @/state 导入。'
        }
      ])
    }
  },
  {
    files: ['src/features/**/utils/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/api/*', '@/services/*'])
    }
  },
  {
    files: ['src/services/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/app/*', '@/router/*', '@/pages/*', '@/layouts/*', '@/features/*'])
    }
  },
  {
    files: ['src/api/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/app/*', '@/router/*', '@/pages/*', '@/layouts/*', '@/features/*', '@/services/*', '@/components/*'])
    }
  },
  {
    files: ['src/utils/**/*.{ts,tsx}', 'src/hooks/**/*.{ts,tsx}', 'src/config/**/*.{ts,tsx}', 'src/design-tokens/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': createRestrictedImports(['@/app/*', '@/router/*', '@/pages/*', '@/layouts/*', '@/features/*', '@/services/*', '@/api/*', '@/components/*'])
    }
  },
)
