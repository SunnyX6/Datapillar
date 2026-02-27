import { KnowledgeGraphView } from '@/features/governance/ui/KnowledgeGraphView'

export function GovernanceKnowledgePage() {
  return (
    <section className="h-full bg-slate-50 dark:bg-[#0f172a] selection:bg-indigo-500/30">
      <div className="h-full overflow-hidden relative">
        <KnowledgeGraphView />
      </div>
    </section>
  )
}
