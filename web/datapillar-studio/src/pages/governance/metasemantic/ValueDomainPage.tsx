import { useNavigate } from 'react-router-dom'
import { ValueDomainExplorer } from '@/features/governance/ui/metasemantic/explorer/ValueDomainExplorer'

export function GovernanceValueDomainPage() {
  const navigate = useNavigate()

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <ValueDomainExplorer onBack={handleBack} />
    </div>
  )
}
