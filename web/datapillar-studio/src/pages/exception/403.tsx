import { motion } from 'framer-motion'
import { ArrowLeft, Home, Lock, ShieldAlert } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { resolveForbiddenQuery } from '@/utils/exceptionNavigation'

export function ForbiddenPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const routeQuery = resolveForbiddenQuery(location.search)
  const description = '抱歉，你没有权限访问当前页面，请联系管理员分配菜单权限。'

  const handleBack = () => {
    navigate(routeQuery.from, { replace: true })
  }

  const handleBackHome = () => {
    navigate('/', { replace: true })
  }

  return (
    <div className="min-h-dvh bg-slate-50 flex flex-col items-center justify-center px-6 py-10 dark:bg-[#020617]">
      <motion.div
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45 }}
        className="w-full max-w-md text-center"
      >
        <div className="relative mb-8 flex justify-center">
          <div className="absolute inset-0 rounded-full bg-rose-500/10 blur-3xl scale-150 dark:bg-rose-500/20" />
          <div className="relative h-24 w-24 rounded-3xl border border-rose-100 bg-white shadow-xl shadow-rose-500/10 flex items-center justify-center dark:border-rose-900/50 dark:bg-slate-900">
            <ShieldAlert className="h-11 w-11 text-rose-500 dark:text-rose-300" />
            <motion.div
              initial={{ scale: 0.2, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: 0.2, type: 'spring', stiffness: 380, damping: 20 }}
              className="absolute -top-2 -right-2 h-8 w-8 rounded-full border-4 border-white bg-rose-500 flex items-center justify-center shadow-lg dark:border-slate-900"
            >
              <Lock className="h-3.5 w-3.5 text-white" />
            </motion.div>
          </div>
        </div>

        <h1 className="mb-4 text-6xl font-black tracking-tighter text-slate-900 dark:text-slate-100">403</h1>
        <h2 className="mb-3 text-2xl font-bold tracking-tight text-slate-800 dark:text-slate-100">访问受限</h2>
        <p className="mb-8 text-body-sm leading-relaxed text-slate-500 dark:text-slate-400">
          {description}
        </p>

        {routeQuery.deniedPath ? (
          <p className="mb-8 text-caption text-slate-400 dark:text-slate-500">
            受限路径：{routeQuery.deniedPath}
          </p>
        ) : null}

        <div className="flex flex-wrap justify-center gap-3">
          <button
            type="button"
            data-testid="forbidden-back-button"
            onClick={handleBack}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-6 py-3 text-body-sm font-semibold text-slate-700 transition-all hover:bg-slate-50 hover:border-slate-300 active:scale-95 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            <ArrowLeft size={16} />
            返回上一页
          </button>

          <button
            type="button"
            data-testid="forbidden-home-button"
            onClick={handleBackHome}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3 text-body-sm font-semibold text-white transition-all hover:bg-slate-800 active:scale-95 dark:bg-indigo-500 dark:hover:bg-indigo-600"
          >
            <Home size={16} />
            回到首页
          </button>
        </div>

        <div className="mt-12 border-t border-slate-200/60 pt-8 dark:border-slate-700">
          <p className="text-micro font-medium uppercase tracking-widest text-slate-400 dark:text-slate-500">
            <span className="mr-2 inline-block h-1.5 w-1.5 rounded-full bg-rose-500 align-middle animate-pulse" />
            Security Protocol Active
          </p>
          <p className="mt-2 text-micro text-slate-400 dark:text-slate-500">
            如果你认为这是一个错误，请联系系统管理员申请相关权限。
          </p>
        </div>
      </motion.div>
    </div>
  )
}
