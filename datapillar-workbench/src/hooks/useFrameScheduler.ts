import { useCallback, useEffect, useRef } from 'react'

interface FrameTask {
  id: number
  callback: () => void
  delay: number
  repeat: boolean
  elapsed: number
}

export interface FrameScheduler {
  setFrameTimeout: (callback: () => void, delay: number) => number
  setFrameInterval: (callback: () => void, interval: number) => number
  clearFrameTask: (taskId: number) => void
  clearAllTasks: () => void
}

/**
 * 使用单个 requestAnimationFrame 驱动的调度器
 * - pause = false 时自动暂停计时，resume 后继续
 */
export function useFrameScheduler(active: boolean): FrameScheduler {
  const tasksRef = useRef<Map<number, FrameTask>>(new Map())
  const rafRef = useRef<number | null>(null)
  const lastTimeRef = useRef<number | null>(null)
  const taskIdRef = useRef(0)
  const runFrameRef = useRef<(timestamp: number) => void>(() => {})

  const stopLoop = useCallback(() => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }
    lastTimeRef.current = null
  }, [])

  const runFrame = useCallback((timestamp: number) => {
    if (!active) {
      stopLoop()
      return
    }

    if (lastTimeRef.current === null) {
      lastTimeRef.current = timestamp
    }

    const delta = timestamp - lastTimeRef.current
    lastTimeRef.current = timestamp

    tasksRef.current.forEach((task, id) => {
      task.elapsed += delta
      if (task.elapsed >= task.delay) {
        task.callback()
        if (task.repeat) {
          task.elapsed = task.elapsed % task.delay
        } else {
          tasksRef.current.delete(id)
        }
      }
    })

    if (tasksRef.current.size > 0) {
      rafRef.current = requestAnimationFrame((nextTimestamp) => runFrameRef.current(nextTimestamp))
    } else {
      stopLoop()
    }
  }, [active, stopLoop])

  const ensureLoop = useCallback(() => {
    if (!active || tasksRef.current.size === 0) {
      return
    }
    if (rafRef.current === null) {
      lastTimeRef.current = null
      rafRef.current = requestAnimationFrame((timestamp) => runFrameRef.current(timestamp))
    }
  }, [active])

  const scheduleTask = useCallback((callback: () => void, delay: number, repeat: boolean) => {
    const id = ++taskIdRef.current
    tasksRef.current.set(id, {
      id,
      callback,
      delay,
      repeat,
      elapsed: 0
    })
    ensureLoop()
    return id
  }, [ensureLoop])

  const clearFrameTask = useCallback((taskId: number) => {
    tasksRef.current.delete(taskId)
    if (tasksRef.current.size === 0) {
      stopLoop()
    }
  }, [stopLoop])

  const clearAllTasks = useCallback(() => {
    tasksRef.current.clear()
    stopLoop()
  }, [stopLoop])

  const setFrameTimeout = useCallback((callback: () => void, delay: number) => {
    return scheduleTask(callback, delay, false)
  }, [scheduleTask])

  const setFrameInterval = useCallback((callback: () => void, interval: number) => {
    return scheduleTask(callback, interval, true)
  }, [scheduleTask])

  useEffect(() => {
    runFrameRef.current = runFrame
  }, [runFrame])

  useEffect(() => {
    if (!active) {
      stopLoop()
    } else if (tasksRef.current.size > 0) {
      ensureLoop()
    }

    return () => {
      clearAllTasks()
    }
  }, [active, ensureLoop, clearAllTasks, stopLoop])

  return {
    setFrameTimeout,
    setFrameInterval,
    clearFrameTask,
    clearAllTasks
  }
}
