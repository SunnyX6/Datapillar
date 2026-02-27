import { Terminal } from 'lucide-react'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { OneIdeLeftColumn } from '@/features/ide/ui/OneIdeLeftColumn'
import { OneIdeRightColumn } from '@/features/ide/ui/OneIdeRightColumn'

export function OneIdeView() {
  return (
    <section className="h-full bg-white dark:bg-slate-900 flex flex-col overflow-hidden relative @container">
      <div className={`flex-1 overflow-y-auto p-4 @md:p-6 @xl:p-8 custom-scrollbar ${contentMaxWidthClassMap.full} mx-auto w-full`}>
        <div className="flex flex-col gap-6 @md:gap-8">
          <div className="flex flex-col gap-4 @md:flex-row @md:items-end @md:justify-between">
            <div>
              <div className="flex items-center gap-2 text-legal font-semibold uppercase tracking-widest text-slate-400 dark:text-slate-500">
                <span className="inline-flex items-center px-2 py-0.5 rounded bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 text-nano font-black tracking-widest uppercase">
                  <Terminal size={10} className="mr-1.5" />
                  v2.4
                </span>
                <span className="h-1 w-1 rounded-full bg-slate-300 dark:bg-slate-600" />
                <span className="inline-flex items-center gap-1.5 text-legal font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-100 dark:border-emerald-500/20">
                  <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse" />
                  System Operational
                </span>
              </div>
              <h1 className="text-heading @md:text-title @xl:text-display font-black tracking-tight text-slate-900 dark:text-slate-100 mt-2">
                One Ide Studio
              </h1>
              <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body max-w-xl">
                Integrated environment for batch, streaming, and ML workloads. <br />
                Seamlessly switch between SQL, Python, and Java contexts.
              </p>
            </div>

            <div className="flex items-center gap-3 @md:gap-4">{/* Actions */}</div>
          </div>

          <div className="grid grid-cols-12 gap-4 @md:gap-6 items-start">
            <OneIdeLeftColumn />
            <OneIdeRightColumn />
          </div>
        </div>
      </div>
    </section>
  )
}
