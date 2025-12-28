import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MetricExplorer, MetricOverview } from '@/layouts/governance/metasemantic'
import type { Metric } from '@/layouts/governance/metasemantic'

export function GovernanceMetricPage() {
  const navigate = useNavigate()
  const [selectedMetric, setSelectedMetric] = useState<Metric | null>(null)
  const [updatedMetric, setUpdatedMetric] = useState<Metric | null>(null)

  const handleBack = () => {
    navigate('/governance/semantic')
  }

  const handleVersionSwitch = (metric: Metric) => {
    setUpdatedMetric(metric)
    setSelectedMetric(metric)
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <MetricExplorer onBack={handleBack} onOpenDrawer={setSelectedMetric} updatedMetric={updatedMetric} />
      {selectedMetric && (
        <MetricOverview
          metric={selectedMetric}
          onClose={() => setSelectedMetric(null)}
          onVersionSwitch={handleVersionSwitch}
        />
      )}
    </div>
  )
}
