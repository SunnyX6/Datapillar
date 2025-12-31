/**
 * 自定义 Minimap 组件
 * 支持中文显示、语法高亮、点击跳转、拖拽滚动
 */

import { useEffect, useRef, useState, useCallback } from 'react'
import type * as Monaco from 'monaco-editor'
import { useIsDark } from '@/stores/themeStore'

interface MinimapProps {
  /** Monaco Editor 实例 */
  editor: Monaco.editor.IStandaloneCodeEditor | null
  /** Monaco 模块引用 */
  monaco: typeof Monaco | null
  /** 宽度，默认 80px */
  width?: number
  /** 缩放比例，默认 0.12 */
  scale?: number
  /** 字体，默认系统中文字体 */
  fontFamily?: string
  /** 字体大小（缩放前），默认 12px */
  fontSize?: number
}

/** Token 类型到颜色的映射 - 浅色主题 */
const TOKEN_COLOR_MAP_LIGHT: Record<string, string> = {
  'keyword': '#0000ff',
  'keyword.sql': '#0000ff',
  'string': '#a31515',
  'string.sql': '#a31515',
  'comment': '#008000',
  'comment.sql': '#008000',
  'number': '#098658',
  'number.sql': '#098658',
  'operator': '#000000',
  'operator.sql': '#000000',
  'delimiter': '#000000',
  'identifier': '#001080',
  'type': '#267f99',
  'predefined': '#795e26',
  'predefined.sql': '#795e26',
}

/** Token 类型到颜色的映射 - 深色主题 */
const TOKEN_COLOR_MAP_DARK: Record<string, string> = {
  'keyword': '#569cd6',
  'keyword.sql': '#569cd6',
  'string': '#ce9178',
  'string.sql': '#ce9178',
  'comment': '#6a9955',
  'comment.sql': '#6a9955',
  'number': '#b5cea8',
  'number.sql': '#b5cea8',
  'operator': '#d4d4d4',
  'operator.sql': '#d4d4d4',
  'delimiter': '#d4d4d4',
  'identifier': '#9cdcfe',
  'type': '#4ec9b0',
  'predefined': '#dcdcaa',
  'predefined.sql': '#dcdcaa',
}

/** 获取 token 颜色 */
function getTokenColor(tokenType: string, isDark: boolean): string {
  const colorMap = isDark ? TOKEN_COLOR_MAP_DARK : TOKEN_COLOR_MAP_LIGHT
  const defaultColor = isDark ? '#d4d4d4' : '#000000'

  // 精确匹配
  if (colorMap[tokenType]) {
    return colorMap[tokenType]
  }
  // 前缀匹配
  for (const [key, color] of Object.entries(colorMap)) {
    if (tokenType.startsWith(key)) {
      return color
    }
  }
  return defaultColor
}

export function Minimap({
  editor,
  monaco,
  width = 120,
  scale = 0.15,
  fontFamily = "Menlo, 'PingFang SC', 'Microsoft YaHei', monospace",
  fontSize = 12,
}: MinimapProps) {
  const isDark = useIsDark()
  const containerRef = useRef<HTMLDivElement>(null)
  const codeRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [lines, setLines] = useState<{ tokens: { text: string; color: string }[] }[]>([])
  const [viewportTop, setViewportTop] = useState(0)
  const [viewportHeight, setViewportHeight] = useState(0)
  const [scrollTop, setScrollTop] = useState(0)
  const dragStartY = useRef(0)
  const dragStartScrollTop = useRef(0)

  // 计算实际行高
  const lineHeight = fontSize * scale

  // 默认颜色根据主题
  const defaultColor = isDark ? '#d4d4d4' : '#000000'

  // 更新代码内容和语法高亮
  const updateContent = useCallback(() => {
    if (!editor || !monaco) return

    const model = editor.getModel()
    if (!model) return

    const lineCount = model.getLineCount()
    const languageId = model.getLanguageId()
    const newLines: { tokens: { text: string; color: string }[] }[] = []

    for (let i = 1; i <= lineCount; i++) {
      const lineContent = model.getLineContent(i)
      const lineTokens: { text: string; color: string }[] = []

      try {
        // 使用 Monaco 的 tokenize API
        const tokens = monaco.editor.tokenize(lineContent, languageId)
        if (tokens.length > 0 && tokens[0].length > 0) {
          let lastOffset = 0
          for (let j = 0; j < tokens[0].length; j++) {
            const token = tokens[0][j]
            const nextOffset = j + 1 < tokens[0].length ? tokens[0][j + 1].offset : lineContent.length
            const tokenText = lineContent.substring(token.offset, nextOffset)
            const color = getTokenColor(token.type, isDark)
            lineTokens.push({ text: tokenText, color })
            lastOffset = nextOffset
          }
          // 如果还有剩余文本
          if (lastOffset < lineContent.length) {
            lineTokens.push({ text: lineContent.substring(lastOffset), color: defaultColor })
          }
        } else {
          // 无 token，使用原始文本
          lineTokens.push({ text: lineContent || ' ', color: defaultColor })
        }
      } catch {
        // tokenize 失败，使用原始文本
        lineTokens.push({ text: lineContent || ' ', color: defaultColor })
      }

      newLines.push({ tokens: lineTokens.length > 0 ? lineTokens : [{ text: ' ', color: defaultColor }] })
    }

    setLines(newLines)
  }, [editor, monaco, isDark, defaultColor])

  // 更新视口位置和 minimap 滚动
  const updateViewport = useCallback(() => {
    if (!editor || !containerRef.current) return

    const editorScrollTop = editor.getScrollTop()
    const layoutInfo = editor.getLayoutInfo()
    const editorLineHeight = editor.getOption(monaco?.editor.EditorOption.lineHeight ?? 66)
    const model = editor.getModel()
    if (!model) return

    const totalLines = model.getLineCount()
    const totalMinimapHeight = totalLines * lineHeight
    const containerHeight = containerRef.current.clientHeight

    // 计算可视区域在 minimap 中的高度和位置
    const visibleLines = layoutInfo.height / editorLineHeight
    const scrolledLines = editorScrollTop / editorLineHeight
    const viewportH = visibleLines * lineHeight
    const viewportT = scrolledLines * lineHeight

    setViewportHeight(viewportH)

    // 如果 minimap 内容高度小于容器高度，不需要滚动
    if (totalMinimapHeight <= containerHeight) {
      setViewportTop(viewportT)
      setScrollTop(0)
    } else {
      // 需要滚动 minimap，使视口指示器保持在可见区域中央
      const maxScroll = totalMinimapHeight - containerHeight
      const editorMaxScroll = editor.getScrollHeight() - layoutInfo.height

      // 按比例计算 minimap 的滚动位置
      const scrollRatio = editorMaxScroll > 0 ? editorScrollTop / editorMaxScroll : 0
      const minimapScroll = scrollRatio * maxScroll

      setScrollTop(minimapScroll)
      setViewportTop(viewportT)
    }
  }, [editor, monaco, lineHeight])

  // 监听编辑器内容变化
  useEffect(() => {
    if (!editor) return

    // 使用 requestAnimationFrame 延迟初始更新，避免 effect 中同步 setState
    const frameId = requestAnimationFrame(() => updateContent())

    // 监听内容变化
    const contentDisposable = editor.onDidChangeModelContent(() => {
      updateContent()
    })

    // 监听 model 切换（tab 切换）
    const modelDisposable = editor.onDidChangeModel(() => {
      // 先清空旧内容，避免闪烁
      setLines([])
      setScrollTop(0)
      setViewportTop(0)
      // 再更新新内容
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
  }, [editor, updateContent, updateViewport])

  // 监听主题变化，重新渲染语法高亮
  useEffect(() => {
    if (editor) {
      const frameId = requestAnimationFrame(() => updateContent())
      return () => cancelAnimationFrame(frameId)
    }
  }, [isDark, editor, updateContent])

  // 监听编辑器滚动
  useEffect(() => {
    if (!editor) return

    // 使用 requestAnimationFrame 延迟初始更新，避免 effect 中同步 setState
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
  }, [editor, updateViewport, isDragging])

  // 监听编辑器布局变化
  useEffect(() => {
    if (!editor) return

    const disposable = editor.onDidLayoutChange(() => {
      updateViewport()
    })

    return () => disposable.dispose()
  }, [editor, updateViewport])

  // 滚轮事件处理（纵向滚动编辑器，横向滚动 minimap）
  useEffect(() => {
    if (!editor || !containerRef.current) return

    const container = containerRef.current
    const handleWheel = (e: WheelEvent) => {
      e.preventDefault()
      e.stopPropagation()

      // Shift + 滚轮 或 横向滚动：横向滚动 minimap
      if (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) {
        const delta = e.shiftKey ? e.deltaY : e.deltaX
        container.scrollLeft += delta
      } else {
        // 纵向滚动：滚动编辑器
        const currentScrollTop = editor.getScrollTop()
        editor.setScrollTop(currentScrollTop + e.deltaY)
      }
    }

    container.addEventListener('wheel', handleWheel, { passive: false })
    return () => container.removeEventListener('wheel', handleWheel)
  }, [editor])

  // 点击跳转
  const handleClick = useCallback((e: React.MouseEvent) => {
    if (!editor || !containerRef.current || isDragging) return

    const rect = containerRef.current.getBoundingClientRect()
    // 点击位置需要加上 minimap 的滚动偏移，然后除以 scale 得到实际位置
    const clickY = (e.clientY - rect.top + scrollTop) / scale
    const targetLine = Math.floor(clickY / fontSize) + 1

    editor.revealLineInCenter(targetLine)
  }, [editor, isDragging, scrollTop, scale, fontSize])

  // 拖拽开始
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (!editor || !viewportRef.current) return

    const rect = viewportRef.current.getBoundingClientRect()
    // 检查是否点击在 viewport 内
    if (e.clientY >= rect.top && e.clientY <= rect.bottom) {
      e.preventDefault()
      setIsDragging(true)
      dragStartY.current = e.clientY
      dragStartScrollTop.current = editor.getScrollTop()
      document.body.style.userSelect = 'none'
      document.body.style.cursor = 'grabbing'
    }
  }, [editor])

  // 拖拽移动
  useEffect(() => {
    if (!isDragging || !editor) return

    const handleMouseMove = (e: MouseEvent) => {
      const deltaY = e.clientY - dragStartY.current
      const editorLineHeight = editor.getOption(monaco?.editor.EditorOption.lineHeight ?? 66)
      // 将 minimap 的移动距离转换为编辑器滚动距离
      const scrollDelta = (deltaY / lineHeight) * editorLineHeight
      const newScrollTop = dragStartScrollTop.current + scrollDelta
      editor.setScrollTop(newScrollTop)
    }

    const handleMouseUp = () => {
      setIsDragging(false)
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [isDragging, editor, monaco, lineHeight])

  // 未准备好时显示占位
  if (!editor || !monaco) {
    return (
      <div
        className="relative bg-slate-50/80 dark:bg-slate-800/80 border-l border-slate-200/60 dark:border-slate-700/60 shrink-0 w-[var(--minimap-width)]"
        style={{ '--minimap-width': `${width}px` } as React.CSSProperties}
      />
    )
  }

  return (
    <div
      ref={containerRef}
      className="relative overflow-y-hidden overflow-x-auto bg-slate-50/80 dark:bg-slate-800/80 border-l border-slate-200/60 dark:border-slate-700/60 shrink-0 scrollbar-hide w-[var(--minimap-width)]"
      style={{ '--minimap-width': `${width}px` } as React.CSSProperties}
      onClick={handleClick}
      onMouseDown={handleMouseDown}
    >
      {/* 代码内容 - 使用 transform 实现滚动 */}
      <div
        ref={codeRef}
        className="relative select-none w-max minimap-code"
        style={{
          '--minimap-font-size': `${fontSize}px`,
          '--minimap-scale': scale,
          '--minimap-scroll': `${-scrollTop}px`,
          transform: `translateY(var(--minimap-scroll)) scale(var(--minimap-scale))`,
          transformOrigin: 'top left',
          fontFamily,
        } as React.CSSProperties}
      >
        {lines.map((line, i) => (
          <div key={i} className="minimap-line">
            {line.tokens.map((token, j) => (
              <span key={j} style={{ color: token.color }}>
                {token.text}
              </span>
            ))}
          </div>
        ))}
      </div>

      {/* 可视区域指示器 - 只有有内容时才显示 */}
      {lines.length > 0 && lines.some(l => l.tokens.some(t => t.text.trim() !== '')) && (
        <div
          ref={viewportRef}
          className="absolute left-0 right-0 bg-slate-400/20 dark:bg-slate-500/30 border-y border-slate-400/30 dark:border-slate-500/40 cursor-grab active:cursor-grabbing top-[var(--viewport-top)] h-[var(--viewport-height)]"
          style={{
            '--viewport-top': `${viewportTop - scrollTop}px`,
            '--viewport-height': `${Math.max(viewportHeight, 20)}px`,
          } as React.CSSProperties}
        />
      )}
    </div>
  )
}
