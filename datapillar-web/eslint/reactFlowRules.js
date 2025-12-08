const TARGET_HOOKS = new Set(['useEffect', 'useLayoutEffect'])
const BANNED_DEPENDENCIES = new Set([
  'fitView',
  'setCenter',
  'setViewport',
  'getViewport',
  'zoomIn',
  'zoomOut'
])
const STORE_HOOK_PATTERN = /^use[A-Z][A-Za-z0-9]*Store$/

const returnsObjectLiteral = (fn) => {
  if (!fn) {
    return false
  }

  if (fn.type === 'ArrowFunctionExpression' && fn.body.type === 'ObjectExpression') {
    return true
  }

  const body = fn.body && fn.body.type === 'BlockStatement' ? fn.body.body : null
  if (!body) {
    return false
  }
  return body.some(
    (statement) =>
      statement.type === 'ReturnStatement' &&
      statement.argument &&
      statement.argument.type === 'ObjectExpression'
  )
}

const reactFlowRules = {
  rules: {
    'no-reactflow-effect-deps': {
      meta: {
        type: 'problem',
        docs: {
          description: '禁止在 useEffect/useLayoutEffect 依赖数组中直接引用 React Flow 实例方法，避免 viewport 循环更新',
          recommended: false
        },
        schema: []
      },
      create(context) {
        return {
          CallExpression(node) {
            if (node.callee.type !== 'Identifier' || !TARGET_HOOKS.has(node.callee.name)) {
              return
            }

            if (node.arguments.length < 2) {
              return
            }

            const deps = node.arguments[1]
            if (!deps || deps.type !== 'ArrayExpression') {
              return
            }

            deps.elements.forEach((element) => {
              if (!element || element.type !== 'Identifier') {
                return
              }

              if (BANNED_DEPENDENCIES.has(element.name)) {
                context.report({
                  node: element,
                  message: `禁止在 ${node.callee.name} 的依赖数组中直接引用 React Flow 方法 "${element.name}"，请通过 useRef 缓存后再使用，以避免无限更新。`
                })
              }
            })
          }
        }
      }
    },
    'no-zustand-object-selector': {
      meta: {
        type: 'problem',
        docs: {
          description: '禁止 useXStore Selector 返回对象字面量，避免生成不稳定引用引发无限更新',
          recommended: false
        },
        schema: []
      },
      create(context) {
        return {
          CallExpression(node) {
            if (node.callee.type !== 'Identifier' || !STORE_HOOK_PATTERN.test(node.callee.name)) {
              return
            }

            const selector = node.arguments[0]
            if (
              !selector ||
              (selector.type !== 'ArrowFunctionExpression' &&
                selector.type !== 'FunctionExpression')
            ) {
              return
            }

            if (!returnsObjectLiteral(selector)) {
              return
            }

            context.report({
              node: selector,
              message:
                'Zustand selector 不能直接返回对象字面量，请拆分为多个稳定 selector 或使用自定义 Hook。'
            })
          }
        }
      }
    }
  }
}

export default reactFlowRules
