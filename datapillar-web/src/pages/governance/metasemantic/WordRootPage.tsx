import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { WordRootExplorer, WordRootOverview } from '@/layouts/governance/metasemantic'
import type { WordRoot } from '@/layouts/governance/metasemantic'

export function GovernanceWordRootPage() {
  const navigate = useNavigate()
  const [selectedWordRoot, setSelectedWordRoot] = useState<WordRoot | null>(null)

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <WordRootExplorer onBack={handleBack} onOpenDrawer={setSelectedWordRoot} />
      {selectedWordRoot && <WordRootOverview wordRoot={selectedWordRoot} onClose={() => setSelectedWordRoot(null)} />}
    </div>
  )
}
