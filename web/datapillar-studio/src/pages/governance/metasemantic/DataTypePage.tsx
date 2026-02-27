import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DataTypeExplorer } from '@/features/governance/ui/metasemantic/explorer/DataTypeExplorer'
import type { DataTypeItem } from '@/features/governance/ui/metasemantic/types'

export function GovernanceDataTypePage() {
  const navigate = useNavigate()
  const [selectedType, setSelectedType] = useState<DataTypeItem | null>(null)

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <DataTypeExplorer onBack={handleBack} selectedType={selectedType} onSelectType={setSelectedType} />
    </div>
  )
}
