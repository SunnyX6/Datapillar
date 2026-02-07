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

  const handleMetricSelect = (metric: Metric) => {
    setSelectedMetric((prev) => (prev?.code === metric.code ? null : metric))
  }

  const handleVersionSwitch = (metric: Metric) => {
    setUpdatedMetric(metric)
    setSelectedMetric(metric)
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <MetricExplorer onBack={handleBack} onOpenDrawer={handleMetricSelect} updatedMetric={updatedMetric} />
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
