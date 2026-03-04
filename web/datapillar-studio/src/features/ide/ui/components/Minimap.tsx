/**
 * Customize Minimap components
 * Support Chinese display,syntax highlighting,Click to jump,Drag and scroll
 */

import { useEffect,useRef,useState,useCallback } from 'react'
import type * as Monaco from 'monaco-editor'
import { useIsDark } from '@/state/themeStore'

interface MinimapProps {
 /** Monaco Editor Example */
 editor:Monaco.editor.IStandaloneCodeEditor | null
 /** Monaco module reference */
 monaco:typeof Monaco | null
 /** Width,Default 80px */
 width?: number
 /** Zoom ratio,Default 0.12 */
 scale?: number
 /** font,Default system Chinese font */
 fontFamily?: string
 /** font size(before scaling),Default 12px */
 fontSize?: number
}

/** Token Type to color mapping - light theme */
const TOKEN_COLOR_MAP_LIGHT:Record<string,string> = {
 'keyword':'#0000ff','keyword.sql':'#0000ff','string':'#a31515','string.sql':'#a31515','comment':'#008000','comment.sql':'#008000','number':'#098658','number.sql':'#098658','operator':'#000000','operator.sql':'#000000','delimiter':'#000000','identifier':'#001080','type':'#267f99','predefined':'#795e26','predefined.sql':'#795e26',}

/** Token Type to color mapping - dark theme */
const TOKEN_COLOR_MAP_DARK:Record<string,string> = {
 'keyword':'#569cd6','keyword.sql':'#569cd6','string':'#ce9178','string.sql':'#ce9178','comment':'#6a9955','comment.sql':'#6a9955','number':'#b5cea8','number.sql':'#b5cea8','operator':'#d4d4d4','operator.sql':'#d4d4d4','delimiter':'#d4d4d4','identifier':'#9cdcfe','type':'#4ec9b0','predefined':'#dcdcaa','predefined.sql':'#dcdcaa',}

/** Get token color */
function getTokenColor(tokenType:string,isDark:boolean):string {
 const colorMap = isDark?TOKEN_COLOR_MAP_DARK:TOKEN_COLOR_MAP_LIGHT
 const defaultColor = isDark?'#d4d4d4':'#000000'

 // exact match
 if (colorMap[tokenType]) {
 return colorMap[tokenType]
 }
 // prefix matching
 for (const [key,color] of Object.entries(colorMap)) {
 if (tokenType.startsWith(key)) {
 return color
 }
 }
 return defaultColor
}

export function Minimap({
 editor,monaco,width = 120,scale = 0.15,fontFamily = "Menlo,'PingFang SC','Microsoft YaHei',monospace",fontSize = 12,}:MinimapProps) {
 const isDark = useIsDark()
 const containerRef = useRef<HTMLDivElement>(null)
 const codeRef = useRef<HTMLDivElement>(null)
 const viewportRef = useRef<HTMLDivElement>(null)
 const [isDragging,setIsDragging] = useState(false)
 const [lines,setLines] = useState<{ tokens:{ text:string;color:string }[] }[]>([])
 const [viewportTop,setViewportTop] = useState(0)
 const [viewportHeight,setViewportHeight] = useState(0)
 const [scrollTop,setScrollTop] = useState(0)
 const dragStartY = useRef(0)
 const dragStartScrollTop = useRef(0)

 // Calculate actual row height
 const lineHeight = fontSize * scale

 // Default colors based on theme
 const defaultColor = isDark?'#d4d4d4':'#000000'

 // Updated code content and syntax highlighting
 const updateContent = useCallback(() => {
 if (!editor ||!monaco) return

 const model = editor.getModel()
 if (!model) return

 const lineCount = model.getLineCount()
 const languageId = model.getLanguageId()
 const newLines:{ tokens:{ text:string;color:string }[] }[] = []

 for (let i = 1;i <= lineCount;i++) {
 const lineContent = model.getLineContent(i)
 const lineTokens:{ text:string;color:string }[] = []

 try {
 // use Monaco of tokenize API
 const tokens = monaco.editor.tokenize(lineContent,languageId)
 if (tokens.length > 0 && tokens[0].length > 0) {
 let lastOffset = 0
 for (let j = 0;j < tokens[0].length;j++) {
 const token = tokens[0][j]
 const nextOffset = j + 1 < tokens[0].length?tokens[0][j + 1].offset:lineContent.length
 const tokenText = lineContent.substring(token.offset,nextOffset)
 const color = getTokenColor(token.type,isDark)
 lineTokens.push({ text:tokenText,color })
 lastOffset = nextOffset
 }
 // If there is any remaining text
 if (lastOffset < lineContent.length) {
 lineTokens.push({ text:lineContent.substring(lastOffset),color:defaultColor })
 }
 } else {
 // None token,Use original text
 lineTokens.push({ text:lineContent || ' ',color:defaultColor })
 }
 } catch {
 // tokenize failed,Use original text
 lineTokens.push({ text:lineContent || ' ',color:defaultColor })
 }

 newLines.push({ tokens:lineTokens.length > 0?lineTokens:[{ text:' ',color:defaultColor }] })
 }

 setLines(newLines)
 },[editor,monaco,isDark,defaultColor])

 // Update viewport position and minimap scroll
 const updateViewport = useCallback(() => {
 if (!editor ||!containerRef.current) return

 const editorScrollTop = editor.getScrollTop()
 const layoutInfo = editor.getLayoutInfo()
 const editorLineHeight = Number(editor.getOption(monaco?.editor.EditorOption.lineHeight?? 66)) || 16
 const model = editor.getModel()
 if (!model) return

 const totalLines = model.getLineCount()
 const totalMinimapHeight = totalLines * lineHeight
 const containerHeight = containerRef.current.clientHeight

 // Calculate the visible area in minimap height and position in
 const visibleLines = layoutInfo.height / editorLineHeight
 const scrolledLines = editorScrollTop / editorLineHeight
 const viewportH = visibleLines * lineHeight
 const viewportT = scrolledLines * lineHeight

 setViewportHeight(viewportH)

 // if minimap Content height is less than container height,No scrolling required
 if (totalMinimapHeight <= containerHeight) {
 setViewportTop(viewportT)
 setScrollTop(0)
 } else {
 // Need to scroll minimap,Keeps the viewport indicator centered in the visible area
 const maxScroll = totalMinimapHeight - containerHeight
 const editorMaxScroll = editor.getScrollHeight() - layoutInfo.height

 // Calculated on a pro-rata basis minimap scroll position
 const scrollRatio = editorMaxScroll > 0?editorScrollTop / editorMaxScroll:0
 const minimapScroll = scrollRatio * maxScroll

 setScrollTop(minimapScroll)
 setViewportTop(viewportT)
 }
 },[editor,monaco,lineHeight])

 // Monitor editor content changes
 useEffect(() => {
 if (!editor) return

 // use requestAnimationFrame Delay initial update,avoid effect mid-sync setState
 const frameId = requestAnimationFrame(() => updateContent())

 // Monitor content changes
 const contentDisposable = editor.onDidChangeModelContent(() => {
 updateContent()
 })

 // monitor model switch(tab switch)
 const modelDisposable = editor.onDidChangeModel(() => {
 // Clear old content first,avoid flickering
 setLines([])
 setScrollTop(0)
 setViewportTop(0)
 // Update with new content
 requestAnimationFrame(() => {
 updateContent()
 updateViewport()
 })
 })

 return () => {
 cancelAnimationFrame(frameId)
 contentDisposable.dispose()
 modelDisposable.dispose()
 }
 },[editor,updateContent,updateViewport])

 // Monitor theme changes,Re-render syntax highlighting
 useEffect(() => {
 if (editor) {
 const frameId = requestAnimationFrame(() => updateContent())
 return () => cancelAnimationFrame(frameId)
 }
 },[isDark,editor,updateContent])

 // Listen to editor scrolling
 useEffect(() => {
 if (!editor) return

 // use requestAnimationFrame Delay initial update,avoid effect mid-sync setState
 const frameId = requestAnimationFrame(() => updateViewport())
 const disposable = editor.onDidScrollChange(() => {
 if (!isDragging) {
 updateViewport()
 }
 })

 return () => {
 cancelAnimationFrame(frameId)
 disposable.dispose()
 }
 },[editor,updateViewport,isDragging])

 // Monitor editor layout changes
 useEffect(() => {
 if (!editor) return

 const disposable = editor.onDidLayoutChange(() => {
 updateViewport()
 })

 return () => disposable.dispose()
 },[editor,updateViewport])

 // Wheel event handling(vertical scroll editor,Horizontal scrolling minimap)
 useEffect(() => {
 if (!editor ||!containerRef.current) return

 const container = containerRef.current
 const handleWheel = (e:WheelEvent) => {
 e.preventDefault()
 e.stopPropagation()

 // Shift + roller or Horizontal scrolling:Horizontal scrolling minimap
 if (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) {
 const delta = e.shiftKey?e.deltaY:e.deltaX
 container.scrollLeft += delta
 } else {
 // scroll vertically:scrolling editor
 const currentScrollTop = editor.getScrollTop()
 editor.setScrollTop(currentScrollTop + e.deltaY)
 }
 }

 container.addEventListener('wheel',handleWheel,{ passive:false })
 return () => container.removeEventListener('wheel',handleWheel)
 },[editor])

 // Click to jump
 const handleClick = useCallback((e:React.MouseEvent) => {
 if (!editor ||!containerRef.current || isDragging) return

 const rect = containerRef.current.getBoundingClientRect()
 // The click position needs to be added minimap the scroll offset of,then divide by scale get actual location
 const clickY = (e.clientY - rect.top + scrollTop) / scale
 const targetLine = Math.floor(clickY / fontSize) + 1

 editor.revealLineInCenter(targetLine)
 },[editor,isDragging,scrollTop,scale,fontSize])

 // Drag and drop to start
 const handleMouseDown = useCallback((e:React.MouseEvent) => {
 if (!editor ||!viewportRef.current) return

 const rect = viewportRef.current.getBoundingClientRect()
 // Check if click on viewport within
 if (e.clientY >= rect.top && e.clientY <= rect.bottom) {
 e.preventDefault()
 setIsDragging(true)
 dragStartY.current = e.clientY
 dragStartScrollTop.current = editor.getScrollTop()
 document.body.style.userSelect = 'none'
 document.body.style.cursor = 'grabbing'
 }
 },[editor])

 // Drag and move
 useEffect(() => {
 if (!isDragging ||!editor) return

 const handleMouseMove = (e:MouseEvent) => {
 const deltaY = e.clientY - dragStartY.current
 const editorLineHeight = Number(editor.getOption(monaco?.editor.EditorOption.lineHeight?? 66)) || 16
 // will minimap The moving distance is converted into the editor scroll distance
 const scrollDelta = (deltaY / lineHeight) * editorLineHeight
 const newScrollTop = dragStartScrollTop.current + scrollDelta
 editor.setScrollTop(newScrollTop)
 }

 const handleMouseUp = () => {
 setIsDragging(false)
 document.body.style.userSelect = ''
 document.body.style.cursor = ''
 }

 window.addEventListener('mousemove',handleMouseMove)
 window.addEventListener('mouseup',handleMouseUp)

 return () => {
 window.removeEventListener('mousemove',handleMouseMove)
 window.removeEventListener('mouseup',handleMouseUp)
 }
 },[isDragging,editor,monaco,lineHeight])

 // Show placeholder when not ready
 if (!editor ||!monaco) {
 return (<div
 className="relative bg-slate-50/80 dark:bg-slate-800/80 border-l border-slate-200/60 dark:border-slate-700/60 shrink-0 w-[var(--minimap-width)]"
 style={{ '--minimap-width':`${width}px` } as React.CSSProperties}
 />)
 }

 return (<div
 ref={containerRef}
 className="relative overflow-y-hidden overflow-x-auto bg-slate-50/80 dark:bg-slate-800/80 border-l border-slate-200/60 dark:border-slate-700/60 shrink-0 scrollbar-hide w-[var(--minimap-width)]"
 style={{ '--minimap-width':`${width}px` } as React.CSSProperties}
 onClick={handleClick}
 onMouseDown={handleMouseDown}
 >
 {/* Code content - use transform Implement scrolling */}
 <div
 ref={codeRef}
 className="relative select-none w-max minimap-code"
 style={{
 '--minimap-font-size':`${fontSize}px`,'--minimap-scale':scale,'--minimap-scroll':`${-scrollTop}px`,transform:`translateY(var(--minimap-scroll)) scale(var(--minimap-scale))`,transformOrigin:'top left',fontFamily,} as React.CSSProperties}
 >
 {lines.map((line,i) => (<div key={i} className="minimap-line">
 {line.tokens.map((token,j) => (<span key={j} style={{ color:token.color }}>
 {token.text}
 </span>))}
 </div>))}
 </div>

 {/* Visible area indicator - Show only when there is content */}
 {lines.length > 0 && lines.some(l => l.tokens.some(t => t.text.trim()!== '')) && (<div
 ref={viewportRef}
 className="absolute left-0 right-0 bg-slate-400/20 dark:bg-slate-500/30 border-y border-slate-400/30 dark:border-slate-500/40 cursor-grab active:cursor-grabbing top-[var(--viewport-top)] h-[var(--viewport-height)]"
 style={{
 '--viewport-top':`${viewportTop - scrollTop}px`,'--viewport-height':`${Math.max(viewportHeight,20)}px`,} as React.CSSProperties}
 />)}
 </div>)
}
