/**
 * Catalog form component
 */

import { forwardRef, useImperativeHandle, useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { testCatalogConnection } from '@/services/oneMetaService'
import { Select } from '@/components/ui'

type CatalogType = 'RELATIONAL' | 'FILESET' | 'MESSAGING'

interface ProviderOption {
  value: string
  label: string
}

// Provider Configuration
const PROVIDER_BY_TYPE: Record<CatalogType, ProviderOption[]> = {
  RELATIONAL: [
    { value: 'hive', label: 'Apache Hive' },
    { value: 'jdbc-mysql', label: 'MySQL' },
    { value: 'jdbc-postgresql', label: 'PostgreSQL' },
    { value: 'jdbc-doris', label: 'Apache Doris' },
    { value: 'jdbc-oceanbase', label: 'OceanBase' },
    { value: 'jdbc-starrocks', label: 'StarRocks' },
    { value: 'lakehouse-iceberg', label: 'Apache Iceberg' },
    { value: 'lakehouse-hudi', label: 'Apache Hudi' },
    { value: 'lakehouse-paimon', label: 'Apache Paimon' }
  ],
  FILESET: [{ value: 'fileset', label: 'Fileset' }],
  MESSAGING: [{ value: 'kafka', label: 'Apache Kafka' }]
}

interface CreateCatalogFormProps {
  parentName: string
  onFooterLeftRender?: (node: React.ReactNode) => void
}

export interface CatalogFormData {
  name: string
  type: CatalogType
  provider: string
  comment: string
  properties: Record<string, string>
}

export interface CatalogFormHandle {
  getData: () => CatalogFormData
  validate: () => boolean
}

export const CreateCatalogForm = forwardRef<CatalogFormHandle, CreateCatalogFormProps>(
  ({ parentName: _parentName, onFooterLeftRender }, ref) => {
    const [catalogType, setCatalogType] = useState<CatalogType>('RELATIONAL')
    const [provider, setProvider] = useState<string>('hive')
    const [formData, setFormData] = useState({
      name: '',
      comment: '',
      properties: {} as Record<string, string>
    })
    const [isTesting, setIsTesting] = useState(false)

    // test connection
    const handleTestConnection = useCallback(async () => {
      if (!formData.name.trim()) {
        toast.error('Please enter first Catalog Name')
        return
      }
      setIsTesting(true)
      try {
        await testCatalogConnection({
          name: formData.name,
          type: catalogType,
          provider,
          comment: formData.comment,
          properties: formData.properties
        })
        toast.success('Connection test successful')
      } catch {
        // The error was passed by the unity client toast show
      } finally {
        setIsTesting(false)
      }
    }, [formData, catalogType, provider])

    // Render test connection button to footer
    useEffect(() => {
      if (onFooterLeftRender) {
        onFooterLeftRender(
          <button
            type="button"
            onClick={handleTestConnection}
            disabled={isTesting}
            className="px-5 py-2 text-sm font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/30 rounded-xl hover:bg-emerald-100 dark:hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-2"
          >
            {isTesting && (
              <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            )}
            {isTesting ? 'Under test...' : 'test connection'}
          </button>
        )
      }
    }, [onFooterLeftRender, handleTestConnection, isTesting])

    useImperativeHandle(ref, () => ({
      getData: () => ({
        name: formData.name,
        type: catalogType,
        provider,
        comment: formData.comment,
        properties: formData.properties
      }),
      validate: () => {
        if (!formData.name.trim()) {
          toast.error('Please enter Catalog Name')
          return false
        }
        return true
      }
    }))

  const handleTypeChange = (newType: CatalogType) => {
    setCatalogType(newType)
    const firstProvider = PROVIDER_BY_TYPE[newType][0]?.value || ''
    setProvider(firstProvider)
    setFormData((prev) => ({ ...prev, properties: {} }))
  }

  const handleProviderChange = (newProvider: string) => {
    setProvider(newProvider)
    setFormData((prev) => ({ ...prev, properties: {} }))
  }

  const handlePropertyChange = (key: string, value: string) => {
    setFormData((prev) => ({
      ...prev,
      properties: { ...prev.properties, [key]: value }
    }))
  }

  return (
    <div className="space-y-3">
      {/* Basic information */}
      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Catalog Name <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          placeholder="For example: prod_mysql_catalog"
          value={formData.name}
          onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
          className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Catalog Type <span className="text-red-500">*</span>
        </label>
        <Select
          value={catalogType}
          onChange={(value) => handleTypeChange(value as CatalogType)}
          options={[
            { value: 'RELATIONAL', label: 'database' },
            { value: 'FILESET', label: 'File set' },
            { value: 'MESSAGING', label: 'message queue' }
          ]}
          dropdownHeader="Choose Catalog Type"
          className="dark:bg-slate-900 dark:border-slate-700"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Provider <span className="text-red-500">*</span>
        </label>
        <Select
          value={provider}
          onChange={handleProviderChange}
          options={PROVIDER_BY_TYPE[catalogType]}
          dropdownHeader="Select data source type"
          className="dark:bg-slate-900 dark:border-slate-700"
        />
      </div>

      {/* Dynamic configuration items */}
      <div className="space-y-2">
        <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">Connection configuration</label>
        <div className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-3 space-y-2.5">
          <ProviderConfigFields provider={provider} onChange={handlePropertyChange} values={formData.properties} />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">Description</label>
        <textarea
          placeholder="Catalog Instructions for use"
          value={formData.comment}
          onChange={(e) => setFormData((prev) => ({ ...prev, comment: e.target.value }))}
          rows={2}
          className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
        />
      </div>
    </div>
  )
})

CreateCatalogForm.displayName = 'CreateCatalogForm'

// Provider Configure field components
function ProviderConfigFields({
  provider,
  onChange,
  values
}: {
  provider: string
  onChange: (key: string, value: string) => void
  values: Record<string, string>
}) {
  const [customKeys, setCustomKeys] = useState<string[]>([])

  // Add custom configuration items
  const handleAddCustomField = () => {
    const newKey = `custom_${Date.now()}`
    setCustomKeys((prev) => [...prev, newKey])
  }

  // Delete configuration item
  const handleDeleteField = (key: string) => {
    setCustomKeys((prev) => prev.filter((k) => k !== key))
    onChange(key, '') // Clear value
  }

  // Modify custom key
  const handleKeyChange = (oldKey: string, newKey: string) => {
    const oldValue = values[oldKey] || ''
    setCustomKeys((prev) => prev.map((k) => (k === oldKey ? newKey : k)))
    onChange(oldKey, '') // clear old key
    onChange(newKey, oldValue) // set new key
  }

  // Render fixed fields
  const renderField = (
    key: string,
    label: string,
    defaultValue: string,
    required = true,
    type: 'text' | 'password' | 'number' | 'select' = 'text',
    options?: { value: string; label: string }[]
  ) => {
    return (
      <div key={key} className="flex items-center gap-2">
        <div className="w-1/3 flex-shrink-0">
          <input
            type="text"
            value={label}
            disabled
            className="w-full px-3 py-1.5 text-xs font-medium bg-slate-100 dark:bg-slate-900 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 rounded-md cursor-not-allowed"
          />
        </div>
        <div className="flex-1">
          {type === 'select' && options ? (
            <Select
              value={values[key] ?? options[0]?.value ?? ''}
              onChange={(value) => onChange(key, value)}
              options={options}
              dropdownHeader={`Choose${label}`}
            />
          ) : (
            <input
              type={type}
              placeholder={defaultValue}
              value={values[key] ?? ''}
              onChange={(e) => onChange(key, e.target.value)}
              className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          )}
        </div>
        {required && (
          <div className="w-4 flex-shrink-0 text-center">
            <span className="text-red-500 text-sm">*</span>
          </div>
        )}
        {!required && <div className="w-4 flex-shrink-0" />}
      </div>
    )
  }

  // Render custom fields（Editable key and value）
  const renderCustomField = (tempKey: string) => {
    const actualKey = Object.keys(values).find((k) => k === tempKey || tempKey.startsWith('custom_')) || tempKey
    const displayKey = tempKey.startsWith('custom_') ? '' : actualKey

    return (
      <div key={tempKey} className="flex items-center gap-2">
        <div className="w-1/3 flex-shrink-0">
          <input
            type="text"
            placeholder="Configuration item name"
            value={displayKey}
            onChange={(e) => handleKeyChange(tempKey, e.target.value)}
            className="w-full px-3 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div className="flex-1">
          <input
            type="text"
            placeholder="Configuration item value"
            value={values[actualKey] || ''}
            onChange={(e) => onChange(actualKey, e.target.value)}
            className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <button
          type="button"
          onClick={() => handleDeleteField(tempKey)}
          className="w-4 flex-shrink-0 text-red-500 hover:text-red-600 transition-colors"
          aria-label="Delete configuration item"
        >
          <svg className="size-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    )
  }

  // Get predefined fields
  const getFields = () => {
    // Hive Configuration
    if (provider === 'hive') {
      return (
        <>
          {renderField('metastore.uris', 'Metastore URI', 'thrift://127.0.0.1:9083')}
          {renderField('client.pool-size', 'Connection pool size', '1', false, 'number')}
        </>
      )
    }

    // JDBC class configuration
    if (provider.startsWith('jdbc-')) {
      const dbType = provider.replace('jdbc-', '')
      const driverMap: Record<string, string> = {
        mysql: 'com.mysql.cj.jdbc.Driver',
        postgresql: 'org.postgresql.Driver',
        doris: 'com.mysql.cj.jdbc.Driver',
        oceanbase: 'com.oceanbase.jdbc.Driver',
        starrocks: 'com.mysql.cj.jdbc.Driver'
      }
      const urlMap: Record<string, string> = {
        mysql: 'jdbc:mysql://localhost:3306/database',
        postgresql: 'jdbc:postgresql://localhost:5432/database',
        doris: 'jdbc:mysql://localhost:9030',
        oceanbase: 'jdbc:oceanbase://localhost:2881/database',
        starrocks: 'jdbc:mysql://localhost:9030'
      }

      return (
        <>
          {renderField('jdbc-url', 'JDBC URL', urlMap[dbType] || 'jdbc://localhost:3306/db')}
          {renderField('jdbc-driver', 'JDBC Driver', driverMap[dbType] || 'com.mysql.cj.jdbc.Driver')}
          {renderField('jdbc-user', 'Username', 'root')}
          {renderField('jdbc-password', 'Password', '', true, 'password')}
          {renderField('jdbc.pool.min-size', 'Minimum number of connections', '2', false, 'number')}
          {renderField('jdbc.pool.max-size', 'Maximum number of connections', '10', false, 'number')}
        </>
      )
    }

    // Lakehouse Configuration
    if (provider.startsWith('lakehouse-')) {
      const backendOptions = [
        { value: 'hive', label: 'Hive' },
        { value: 'jdbc', label: 'JDBC' },
        { value: 'rest', label: 'REST' }
      ]
      if (provider === 'lakehouse-paimon') {
        backendOptions.push({ value: 'filesystem', label: 'Filesystem' })
      }

      return (
        <>
          {renderField('catalog-backend', 'Catalog Backend', '', true, 'select', backendOptions)}
          {renderField('uri', 'Backend URI', 'thrift://127.0.0.1:9083')}
          {renderField('warehouse', 'Warehouse path', 'hdfs://namespace/path or s3://bucket/path')}
        </>
      )
    }

    // Kafka Configuration
    if (provider === 'kafka') {
      return <>{renderField('bootstrap.servers', 'Bootstrap Servers', 'localhost:9092')}</>
    }

    // Fileset Configuration
    if (provider === 'fileset') {
      return (
        <>
          {renderField('location', 'storage path', 'hdfs://path or s3://bucket/path', false)}
          {renderField('filesystem-providers', 'Filesystem Providers', 'builtin-hdfs,s3', false)}
        </>
      )
    }

    return null
  }

  return (
    <>
      {/* Predefined fields */}
      {getFields()}

      {/* Custom fields */}
      {customKeys.map((key) => renderCustomField(key))}

      {/* Add configuration button */}
      <button
        type="button"
        onClick={handleAddCustomField}
        className="w-full mt-2 px-3 py-1.5 text-xs font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/30 rounded-md hover:bg-indigo-100 dark:hover:bg-indigo-500/20 transition-colors"
      >
        + Add custom configuration
      </button>
    </>
  )
}
