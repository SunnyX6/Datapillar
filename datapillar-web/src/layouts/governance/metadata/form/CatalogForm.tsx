/**
 * Catalog 表单组件
 */

import { forwardRef, useImperativeHandle, useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { testCatalogConnection } from '@/services/oneMetaService'

type CatalogType = 'RELATIONAL' | 'FILESET' | 'MESSAGING' | 'MODEL' | 'METRIC'

interface ProviderOption {
  value: string
  label: string
}

// Provider 配置
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
  MESSAGING: [{ value: 'kafka', label: 'Apache Kafka' }],
  MODEL: [{ value: 'model', label: 'Model' }],
  METRIC: [{ value: 'metric', label: 'Metric' }]
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

    // 测试连接
    const handleTestConnection = useCallback(async () => {
      if (!formData.name.trim()) {
        toast.error('请先输入 Catalog 名称')
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
        toast.success('连接测试成功')
      } catch (error) {
        const message = error instanceof Error ? error.message : '连接测试失败'
        toast.error(message)
      } finally {
        setIsTesting(false)
      }
    }, [formData, catalogType, provider])

    // 渲染测试连接按钮到 footer
    useEffect(() => {
      if (onFooterLeftRender) {
        onFooterLeftRender(
          <button
            type="button"
            onClick={handleTestConnection}
            disabled={isTesting}
            className="px-3 py-1.5 text-sm font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/30 rounded-md hover:bg-emerald-100 dark:hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {isTesting ? '测试中...' : '测试连接'}
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
          toast.error('请输入 Catalog 名称')
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
      {/* 基础信息 */}
      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Catalog 名称 <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          placeholder="例如: prod_mysql_catalog"
          value={formData.name}
          onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Catalog 类型 <span className="text-red-500">*</span>
        </label>
        <select
          value={catalogType}
          onChange={(e) => handleTypeChange(e.target.value as CatalogType)}
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        >
          <option value="RELATIONAL">数据库</option>
          <option value="FILESET">文件集</option>
          <option value="MESSAGING">消息队列</option>
          <option value="MODEL">模型</option>
          <option value="METRIC">指标</option>
        </select>
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          Provider <span className="text-red-500">*</span>
        </label>
        <select
          value={provider}
          onChange={(e) => handleProviderChange(e.target.value)}
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        >
          {PROVIDER_BY_TYPE[catalogType].map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
      </div>

      {/* 动态配置项 */}
      <div className="space-y-2">
        <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">连接配置</label>
        <div className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-3 space-y-2.5">
          <ProviderConfigFields provider={provider} onChange={handlePropertyChange} values={formData.properties} />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">描述</label>
        <textarea
          placeholder="Catalog 用途说明"
          value={formData.comment}
          onChange={(e) => setFormData((prev) => ({ ...prev, comment: e.target.value }))}
          rows={2}
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none"
        />
      </div>
    </div>
  )
})

CreateCatalogForm.displayName = 'CreateCatalogForm'

// Provider 配置字段组件
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

  // 添加自定义配置项
  const handleAddCustomField = () => {
    const newKey = `custom_${Date.now()}`
    setCustomKeys((prev) => [...prev, newKey])
  }

  // 删除配置项
  const handleDeleteField = (key: string) => {
    setCustomKeys((prev) => prev.filter((k) => k !== key))
    onChange(key, '') // 清空值
  }

  // 修改自定义 key
  const handleKeyChange = (oldKey: string, newKey: string) => {
    const oldValue = values[oldKey] || ''
    setCustomKeys((prev) => prev.map((k) => (k === oldKey ? newKey : k)))
    onChange(oldKey, '') // 清空旧 key
    onChange(newKey, oldValue) // 设置新 key
  }

  // 渲染固定字段
  const renderField = (
    key: string,
    label: string,
    defaultValue: string,
    required = true,
    type: 'text' | 'password' | 'number' | 'select' = 'text',
    options?: { value: string; label: string }[]
  ) => {
    // 首次渲染时写入默认值
    if (values[key] === undefined) {
      onChange(key, defaultValue)
    }

    return (
      <div key={key} className="flex items-center gap-2">
        <div className="w-1/3 flex-shrink-0">
          <input
            type="text"
            value={label}
            disabled
            className="w-full px-3 py-1.5 text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 rounded-md cursor-not-allowed"
          />
        </div>
        <div className="flex-1">
          {type === 'select' && options ? (
            <select
              value={values[key] ?? options[0]?.value ?? ''}
              onChange={(e) => onChange(key, e.target.value)}
              className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
            >
              {options.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          ) : (
            <input
              type={type}
              placeholder={defaultValue}
              value={values[key] ?? ''}
              onChange={(e) => onChange(key, e.target.value)}
              className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
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

  // 渲染自定义字段（可编辑 key 和 value）
  const renderCustomField = (tempKey: string) => {
    const actualKey = Object.keys(values).find((k) => k === tempKey || tempKey.startsWith('custom_')) || tempKey
    const displayKey = tempKey.startsWith('custom_') ? '' : actualKey

    return (
      <div key={tempKey} className="flex items-center gap-2">
        <div className="w-1/3 flex-shrink-0">
          <input
            type="text"
            placeholder="配置项名称"
            value={displayKey}
            onChange={(e) => handleKeyChange(tempKey, e.target.value)}
            className="w-full px-3 py-1.5 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          />
        </div>
        <div className="flex-1">
          <input
            type="text"
            placeholder="配置项值"
            value={values[actualKey] || ''}
            onChange={(e) => onChange(actualKey, e.target.value)}
            className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          />
        </div>
        <button
          type="button"
          onClick={() => handleDeleteField(tempKey)}
          className="w-4 flex-shrink-0 text-red-500 hover:text-red-600 transition-colors"
          aria-label="删除配置项"
        >
          <svg className="size-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    )
  }

  // 获取预定义字段
  const getFields = () => {
    // Hive 配置
    if (provider === 'hive') {
      return (
        <>
          {renderField('metastore.uris', 'Metastore URI', 'thrift://127.0.0.1:9083')}
          {renderField('client.pool-size', '连接池大小', '1', false, 'number')}
        </>
      )
    }

    // JDBC 类配置
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
          {renderField('jdbc-user', '用户名', 'root')}
          {renderField('jdbc-password', '密码', '', true, 'password')}
          {renderField('jdbc.pool.min-size', '最小连接数', '2', false, 'number')}
          {renderField('jdbc.pool.max-size', '最大连接数', '10', false, 'number')}
        </>
      )
    }

    // Lakehouse 配置
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
          {renderField('warehouse', 'Warehouse 路径', 'hdfs://namespace/path 或 s3://bucket/path')}
        </>
      )
    }

    // Kafka 配置
    if (provider === 'kafka') {
      return <>{renderField('bootstrap.servers', 'Bootstrap Servers', 'localhost:9092')}</>
    }

    // Fileset 配置
    if (provider === 'fileset') {
      return (
        <>
          {renderField('location', '存储路径', 'hdfs://path 或 s3://bucket/path', false)}
          {renderField('filesystem-providers', 'Filesystem Providers', 'builtin-hdfs,s3', false)}
        </>
      )
    }

    // Model 配置
    if (provider === 'model') {
      return <div className="text-xs text-slate-500 dark:text-slate-400 italic">Model catalog 暂无额外配置项</div>
    }

    // Metric 配置
    if (provider === 'metric') {
      return <div className="text-xs text-slate-500 dark:text-slate-400 italic">Metric catalog 暂无额外配置项</div>
    }

    return null
  }

  return (
    <>
      {/* 预定义字段 */}
      {getFields()}

      {/* 自定义字段 */}
      {customKeys.map((key) => renderCustomField(key))}

      {/* 添加配置按钮 */}
      <button
        type="button"
        onClick={handleAddCustomField}
        className="w-full mt-2 px-3 py-1.5 text-xs font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/30 rounded-md hover:bg-indigo-100 dark:hover:bg-indigo-500/20 transition-colors"
      >
        + 添加自定义配置
      </button>
    </>
  )
}
