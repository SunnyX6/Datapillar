import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react'
import { Globe, Key, Loader2, Server, Shield, UploadCloud } from 'lucide-react'
import { toast } from 'sonner'
import { Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { getDocument, uploadDocument } from '@/services/knowledgeWikiService'
import type { Document } from './types'
import { mapDocumentToUi } from './utils'

type UploadMode = 'local' | 'url'

type UploadDocumentModalProps = {
  isOpen: boolean
  onClose: () => void
  spaceId: string
  spaceName: string
  onUploaded: (doc: Document) => void
}

type AuthType = 'Bearer' | 'Header' | 'Basic'

export default function UploadDocumentModal({
  isOpen,
  onClose,
  spaceId,
  spaceName,
  onUploaded
}: UploadDocumentModalProps) {
  const [uploadMode, setUploadMode] = useState<UploadMode>('local')
  const [dragActive, setDragActive] = useState(false)
  const [importUrl, setImportUrl] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [urlAuthEnabled, setUrlAuthEnabled] = useState(false)
  const [urlAuthType, setUrlAuthType] = useState<AuthType>('Bearer')
  const [urlAuthValue, setUrlAuthValue] = useState('')
  const [urlAuthKey, setUrlAuthKey] = useState('Authorization')
  const [useProxy, setUseProxy] = useState(false)
  const [proxyAgent, setProxyAgent] = useState('默认代理')
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const isUrlDisabled = true

  const resetState = () => {
    setUploadMode('local')
    setDragActive(false)
    setImportUrl('')
    setIsUploading(false)
    setUrlAuthEnabled(false)
    setUrlAuthType('Bearer')
    setUrlAuthValue('')
    setUrlAuthKey('Authorization')
    setUseProxy(false)
    setProxyAgent('默认代理')
  }

  useEffect(() => {
    if (!isOpen) resetState()
  }, [isOpen])

  const handleClose = () => {
    if (isUploading) return
    resetState()
    onClose()
  }

  const startLocalUpload = async (file: File) => {
    if (!spaceId) {
      toast.error('请先选择知识空间')
      return
    }
    const namespaceId = Number(spaceId)
    if (!Number.isFinite(namespaceId)) {
      toast.error('知识空间 ID 无效')
      return
    }
    try {
      setIsUploading(true)
      const uploadResult = await uploadDocument(namespaceId, file, file.name)
      const document = await getDocument(uploadResult.document_id)
      onUploaded(mapDocumentToUi(document))
      toast.success('文档上传成功')
      resetState()
      onClose()
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`上传失败：${message}`)
    } finally {
      setIsUploading(false)
    }
  }

  const handleDrag = (event: DragEvent) => {
    event.preventDefault()
    event.stopPropagation()
    if (event.type === 'dragenter' || event.type === 'dragover') {
      setDragActive(true)
    } else if (event.type === 'dragleave') {
      setDragActive(false)
    }
  }

  const handleDrop = (event: DragEvent) => {
    event.preventDefault()
    event.stopPropagation()
    setDragActive(false)
    const file = event.dataTransfer.files?.[0]
    if (file) {
      startLocalUpload(file)
    }
  }

  const handleFileSelect = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    startLocalUpload(file)
    event.target.value = ''
  }

  const handleUrlImport = () => {
    if (!importUrl.trim()) return
    toast.warning('URL 导入暂未开放')
  }

  const isUrlValid = importUrl.trim().length > 0

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="上传文档"
      subtitle={(
        <span className="text-xs text-slate-400">
          上传至: <span className="font-semibold text-indigo-500">{spaceName}</span>
        </span>
      )}
      size="sm"
    >
      <div className="-mx-8 -mt-4">
        {!isUploading && (
          <div className="flex border-b border-slate-200 dark:border-slate-800 px-8">
            <button
              type="button"
              onClick={() => setUploadMode('local')}
              className={`pb-3 pt-4 text-xs font-semibold uppercase tracking-wider border-b-2 mr-6 transition-colors ${
                uploadMode === 'local'
                  ? 'border-indigo-500 text-indigo-500'
                  : 'border-transparent text-slate-400 hover:text-slate-600'
              }`}
            >
              本地文件
            </button>
            <button
              type="button"
              onClick={() => {
                if (!isUrlDisabled) setUploadMode('url')
              }}
              disabled={isUrlDisabled}
              className={`pb-3 pt-4 text-xs font-semibold uppercase tracking-wider border-b-2 transition-colors ${
                uploadMode === 'url'
                  ? 'border-indigo-500 text-indigo-500'
                  : 'border-transparent text-slate-400 hover:text-slate-600'
              } ${isUrlDisabled ? 'cursor-not-allowed opacity-50' : ''}`}
            >
              URL 链接
            </button>
          </div>
        )}
      </div>

      <div className="pt-6">
        {isUploading ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <Loader2 size={42} className="text-indigo-500 animate-spin mb-4" />
            <p className={`${TYPOGRAPHY.bodySm} font-semibold text-slate-800`}>正在上传文档...</p>
            <p className="text-xs text-slate-400 mt-1">上传完成后可在「切分管理」中配置切分策略</p>
          </div>
        ) : uploadMode === 'local' ? (
          <div
            className={`border-2 border-dashed rounded-2xl p-10 flex flex-col items-center justify-center transition-all ${
              dragActive
                ? 'border-indigo-500 bg-indigo-50/60'
                : 'border-slate-200 bg-slate-50/70 hover:bg-slate-100/70 hover:border-slate-300'
            }`}
            onDragEnter={handleDrag}
            onDragLeave={handleDrag}
            onDragOver={handleDrag}
            onDrop={handleDrop}
          >
            <div className="w-16 h-16 bg-white rounded-full shadow-sm flex items-center justify-center mb-4 text-indigo-500">
              <UploadCloud size={30} />
            </div>
            <p className="text-sm font-semibold text-slate-700 mb-1">点击或拖拽文件到此处</p>
            <p className="text-xs text-slate-400">支持 PDF, DOCX, MD, TXT (Max 20MB)</p>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="mt-6 px-6 py-2 bg-white border border-slate-200 text-slate-600 text-xs font-semibold rounded-xl shadow-sm hover:border-indigo-300 hover:text-indigo-600 transition-all"
            >
              选择文件
            </button>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept=".pdf,.docx,.md,.txt"
              onChange={handleFileSelect}
            />
          </div>
        ) : (
          <div className="space-y-6">
            <div>
              <label className="block text-body-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">文档地址 (URL)</label>
              <div className="relative">
                <Globe size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={importUrl}
                  onChange={(event) => setImportUrl(event.target.value)}
                  placeholder="https://wiki.corp.internal/doc/..."
                  className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  autoFocus
                />
              </div>
              <p className={`${TYPOGRAPHY.legal} text-slate-400 mt-2`}>支持 PDF, Notion 页面, 在线文档或普通网页。</p>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <label className="flex items-center space-x-2 text-xs font-semibold text-slate-600 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={urlAuthEnabled}
                    onChange={(event) => setUrlAuthEnabled(event.target.checked)}
                    className="w-3.5 h-3.5 rounded border-slate-300 text-indigo-500 focus:ring-indigo-500"
                  />
                  <span className="flex items-center">
                    <Key size={14} className="mr-1.5 text-slate-400" />
                    鉴权配置 (Authentication)
                  </span>
                </label>

                <label className="flex items-center space-x-2 text-xs font-semibold text-slate-600 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={useProxy}
                    onChange={(event) => setUseProxy(event.target.checked)}
                    className="w-3.5 h-3.5 rounded border-slate-300 text-indigo-500 focus:ring-indigo-500"
                  />
                  <span className="flex items-center">
                    <Server size={14} className="mr-1.5 text-slate-400" />
                    网络代理 (Network)
                  </span>
                </label>
              </div>

              {urlAuthEnabled && (
                <div className="p-4 bg-amber-50/50 rounded-xl border border-amber-100 space-y-4 animate-in fade-in slide-in-from-top-2">
                  <div className="flex space-x-2 p-1 bg-white rounded-lg border border-amber-100 w-fit">
                    {(['Bearer', 'Header', 'Basic'] as AuthType[]).map((type) => (
                      <button
                        key={type}
                        type="button"
                        onClick={() => setUrlAuthType(type)}
                        className={`px-3 py-1.5 ${TYPOGRAPHY.legal} font-semibold rounded-md transition-all ${
                          urlAuthType === type
                            ? 'bg-amber-100 text-amber-700'
                            : 'text-slate-500 hover:text-slate-700'
                        }`}
                      >
                        {type}
                      </button>
                    ))}
                  </div>

                  <div className="space-y-3">
                    {urlAuthType === 'Header' && (
                      <div>
                        <label className={`block ${TYPOGRAPHY.legal} font-semibold text-slate-500 uppercase mb-1`}>Header Key</label>
                        <input
                          type="text"
                          value={urlAuthKey}
                          onChange={(event) => setUrlAuthKey(event.target.value)}
                          placeholder="e.g. Cookie, X-API-Key"
                          className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs"
                        />
                      </div>
                    )}
                    <div>
                      <label className={`block ${TYPOGRAPHY.legal} font-semibold text-slate-500 uppercase mb-1`}>
                        {urlAuthType === 'Basic' ? 'Credentials (user:pass)' : 'Token / Value'}
                      </label>
                      <input
                        type={urlAuthType === 'Basic' ? 'password' : 'text'}
                        value={urlAuthValue}
                        onChange={(event) => setUrlAuthValue(event.target.value)}
                        placeholder={urlAuthType === 'Basic' ? 'username:password' : 'eyJh...'}
                        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs font-mono focus:border-amber-300 outline-none"
                      />
                    </div>
                  </div>
                </div>
              )}

              {useProxy && (
                <div className="p-4 bg-blue-50/50 rounded-xl border border-blue-100 space-y-3 animate-in fade-in slide-in-from-top-2">
                  <div>
                    <label className={`block ${TYPOGRAPHY.legal} font-semibold text-slate-500 uppercase mb-1`}>Tunnel Agent</label>
                    <select
                      value={proxyAgent}
                      onChange={(event) => setProxyAgent(event.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs outline-none focus:border-blue-300"
                    >
                      <option value="默认代理">默认代理（共享）</option>
                      <option value="内网代理">内网代理</option>
                      <option value="VPN 通道">VPN 通道</option>
                    </select>
                  </div>
                  <div className={`flex items-center ${TYPOGRAPHY.legal} text-blue-600 bg-white px-2 py-1.5 rounded border border-blue-100`}>
                    <span className="mr-1.5">提示</span>
                    流量将通过所选 Agent 进行安全转发，无需公网访问权限。
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-start text-xs text-slate-400">
              <Shield size={12} className="mt-0.5 mr-1.5 flex-shrink-0" />
              <p>系统将自动访问并解析内容。对于内网 Wiki (如 Confluence)，请启用鉴权配置。</p>
            </div>

            <button
              type="button"
              onClick={handleUrlImport}
              disabled={!isUrlValid}
              className="w-full py-2.5 bg-indigo-500 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-200/50 hover:bg-indigo-600 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {urlAuthEnabled ? '带凭证导入 (Import with Auth)' : '开始导入'}
            </button>
          </div>
        )}
      </div>
    </Modal>
  )
}
