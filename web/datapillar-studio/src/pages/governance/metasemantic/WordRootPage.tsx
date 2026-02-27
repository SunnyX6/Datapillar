import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { WordRootExplorer, WordRootOverview } from '@/features/governance/ui/metasemantic'
import type { WordRoot } from '@/features/governance/ui/metasemantic'

export function GovernanceWordRootPage() {
  const navigate = useNavigate()
  const [selectedWordRoot, setSelectedWordRoot] = useState<WordRoot | null>(null)

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  const handleWordRootSelect = (root: WordRoot) => {
    setSelectedWordRoot((prev) => (prev?.code === root.code ? null : root))
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <WordRootExplorer onBack={handleBack} onOpenDrawer={handleWordRootSelect} />
      {selectedWordRoot && <WordRootOverview wordRoot={selectedWordRoot} onClose={() => setSelectedWordRoot(null)} />}
    </div>
  )
}
