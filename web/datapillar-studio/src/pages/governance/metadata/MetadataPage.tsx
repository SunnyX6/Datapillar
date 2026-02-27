import { MetadataView } from '@/features/governance/ui/MetadataView'

export function GovernanceMetadataPage() {
  return (
    <section className="h-full bg-slate-50 dark:bg-[#0f172a] selection:bg-indigo-500/30">
      <div className="h-full overflow-y-auto custom-scrollbar">
        <MetadataView />
      </div>
    </section>
  )
}
