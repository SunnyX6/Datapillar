import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DataTypeExplorer } from '@/layouts/governance/metasemantic/explorer/DataTypeExplorer'

/** 数据类型定义 - 基于 Gravitino Types.java */
export interface DataTypeItem {
  id: string
  name: string
  label: string
  category: 'INTEGRAL' | 'FRACTION' | 'STRING' | 'DATETIME' | 'COMPLEX'
  icon: string
  description: string
  badge?: string
  hasPrecision?: boolean
  hasScale?: boolean
  maxPrecision?: number
  maxScale?: number
  hasLength?: boolean
  maxLength?: number
}

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
