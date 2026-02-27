import { Outlet } from 'react-router-dom'

/**
 * 启动守卫：统一守卫链入口，承载后续守卫编排。
 */
export function BootstrapGate() {
  return <Outlet />
}
