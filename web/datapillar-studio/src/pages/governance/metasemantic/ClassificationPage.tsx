import { useNavigate } from 'react-router-dom'
import { ClassificationExplorer } from '@/layouts/governance/metasemantic/explorer/ClassificationExplorer'

export function GovernanceClassificationPage() {
  const navigate = useNavigate()

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <ClassificationExplorer onBack={handleBack} />
    </div>
  )
}
