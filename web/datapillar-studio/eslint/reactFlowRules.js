const TARGET_HOOKS = new Set(['useEffect','useLayoutEffect'])
const BANNED_DEPENDENCIES = new Set(['fitView','setCenter','setViewport','getViewport','zoomIn','zoomOut'])
const STORE_HOOK_PATTERN = /^use[A-Z][A-Za-z0-9]*Store$/

const returnsObjectLiteral = (fn) => {
 if (!fn) {
 return false
 }

 if (fn.type === 'ArrowFunctionExpression' && fn.body.type === 'ObjectExpression') {
 return true
 }

 const body = fn.body && fn.body.type === 'BlockStatement'?fn.body.body:null
 if (!body) {
 return false
 }
 return body.some((statement) =>
 statement.type === 'ReturnStatement' &&
 statement.argument &&
 statement.argument.type === 'ObjectExpression')
}

const reactFlowRules = {
 rules:{
 'no-reactflow-effect-deps':{
 meta:{
 type:'problem',docs:{
 description:'prohibited from useEffect/useLayoutEffect Direct reference in dependency array React Flow instance method,avoid viewport Cycle update',recommended:false
 },schema:[]
 },create(context) {
 return {
 CallExpression(node) {
 if (node.callee.type!== 'Identifier' ||!TARGET_HOOKS.has(node.callee.name)) {
 return
 }

 if (node.arguments.length < 2) {
 return
 }

 const deps = node.arguments[1]
 if (!deps || deps.type!== 'ArrayExpression') {
 return
 }

 deps.elements.forEach((element) => {
 if (!element || element.type!== 'Identifier') {
 return
 }

 if (BANNED_DEPENDENCIES.has(element.name)) {
 context.report({
 node:element,message:`prohibited from ${node.callee.name} directly referenced in the dependency array of React Flow method "${element.name}",Please pass useRef Cache and use again,to avoid infinite updates.`
 })
 }
 })
 }
 }
 }
 },'no-zustand-object-selector':{
 meta:{
 type:'problem',docs:{
 description:'prohibited useXStore Selector Return object literal,Avoid generating unstable references that trigger infinite updates',recommended:false
 },schema:[]
 },create(context) {
 return {
 CallExpression(node) {
 if (node.callee.type!== 'Identifier' ||!STORE_HOOK_PATTERN.test(node.callee.name)) {
 return
 }

 const selector = node.arguments[0]
 if (!selector ||
 (selector.type!== 'ArrowFunctionExpression' &&
 selector.type!== 'FunctionExpression')) {
 return
 }

 if (!returnsObjectLiteral(selector)) {
 return
 }

 context.report({
 node:selector,message:'Zustand selector Object literals cannot be returned directly,Please split into multiple stable selector or use custom Hook.'
 })
 }
 }
 }
 }
 }
}

export default reactFlowRules
