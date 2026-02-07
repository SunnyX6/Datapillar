import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Plus,
  FileText,
  Database,
  Zap,
  MoreHorizontal,
  FolderOpen,
  Settings,
  TrendingUp,
  ChevronLeft,
  ChevronRight,
  type LucideIcon
} from 'lucide-react'
import { Button, Card, Modal, ModalCancelButton, ModalPrimaryButton } from '@/components/ui'
import { contentMaxWidthClassMap, iconSizeToken, paddingClassMap, panelWidthClassMap } from '@/design-tokens/dimensions'
import { RESPONSIVE_TYPOGRAPHY, TYPOGRAPHY } from '@/design-tokens/typography'
import { toast } from 'sonner'
import { createNamespace, listDocuments, listNamespaces } from '@/services/knowledgeWikiService'
import type { Document, KnowledgeSpace, WikiTab } from './types'
import DocList from './DocList'
import ChunkManager from './ChunkManager'
import RetrievalPlayground from './RetrievalPlayground'
import { SPACE_COLOR_PALETTE, getNamespaceFormStatus, mapDocumentToUi, mapNamespaceToSpace } from './utils'
import UploadDocumentModal from './UploadDocumentModal'
import { WikiHero } from './WikiHero'
import { NamespaceCreateForm, type NamespaceCreateFormValue } from './NamespaceCreateForm'

type StatConfig = {
  label: string
  value: string | number
  unit: string
  icon: LucideIcon
  change: string
  trend: 'up' | 'down'
  color: string
  bg: string
}

export function WikiView() {
  const [activeTab, setActiveTab] = useState<WikiTab>('DOCUMENTS')
  const [spaceList, setSpaceList] = useState<KnowledgeSpace[]>([])
  const [currentSpace, setCurrentSpace] = useState<KnowledgeSpace | null>(null)
  const [documents, setDocuments] = useState<Document[]>([])
  const [isNamespaceCollapsed, setIsNamespaceCollapsed] = useState(false)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false)
  const [newSpaceForm, setNewSpaceForm] = useState<NamespaceCreateFormValue>({ name: '', description: '' })
  const spaceListRef = useRef<KnowledgeSpace[]>([])
  const currentSpaceRef = useRef<KnowledgeSpace | null>(null)

  const { trimmedName: trimmedSpaceName, showNameError, canCreateSpace } = getNamespaceFormStatus(
    spaceList,
    newSpaceForm.name
  )
  const hasSpaces = spaceList.length > 0

  useEffect(() => {
    spaceListRef.current = spaceList
  }, [spaceList])

  useEffect(() => {
    currentSpaceRef.current = currentSpace
  }, [currentSpace])

  const syncDocuments = useCallback(async (spaceId?: string) => {
    if (!spaceId) {
      setDocuments([])
      return
    }
    const namespaceId = Number(spaceId)
    if (!Number.isFinite(namespaceId)) {
      setDocuments([])
      return
    }
    try {
      const { items } = await listDocuments(namespaceId)
      setDocuments(items.map(mapDocumentToUi))
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`加载文档失败：${message}`)
      setDocuments([])
    }
  }, [])

  const syncSpaces = useCallback(async (preferredId?: string) => {
    try {
      const { items } = await listNamespaces()
      const colorMap = new Map(spaceListRef.current.map((space) => [space.id, space.color]))
      const nextSpaces = items.map((item, index) =>
        mapNamespaceToSpace(
          item,
          colorMap.get(String(item.namespace_id)) ?? SPACE_COLOR_PALETTE[index % SPACE_COLOR_PALETTE.length]
        )
      )
      setSpaceList(nextSpaces)
      const target = preferredId ? nextSpaces.find((space) => space.id === preferredId) : undefined
      const fallback = currentSpaceRef.current ? nextSpaces.find((space) => space.id === currentSpaceRef.current?.id) : undefined
      const nextActiveSpace = target ?? fallback ?? nextSpaces[0] ?? null
      setCurrentSpace(nextActiveSpace)
      void syncDocuments(nextActiveSpace?.id)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`加载知识空间失败：${message}`)
    }
  }, [syncDocuments])

  useEffect(() => {
    void syncSpaces()
  }, [syncSpaces])

  const getStats = (space: KnowledgeSpace, index: number): StatConfig[] => {
    const multiplier = index <= 0 ? 1 : index === 1 ? 0.6 : 0.3

    return [
      {
        label: '空间文档总数',
        value: space.docCount,
        unit: '个',
        icon: FileText,
        change: index <= 0 ? '+12' : '+3',
        trend: 'up',
        color: 'text-indigo-600',
        bg: 'bg-indigo-50 dark:bg-indigo-500/10'
      },
      {
        label: '活跃切片 (Chunks)',
        value: (space.docCount * 145 * multiplier).toFixed(0),
        unit: '个',
        icon: Database,
        change: `+${(40 * multiplier).toFixed(0)}`,
        trend: 'up',
        color: 'text-blue-600',
        bg: 'bg-blue-50 dark:bg-blue-500/10'
      },
      {
        label: '平均召回准确率',
        value: index <= 0 ? '94.2' : index === 1 ? '89.5' : '91.0',
        unit: '%',
        icon: Zap,
        change: index <= 0 ? '+2.1%' : '-0.4%',
        trend: index <= 0 ? 'up' : 'down',
        color: 'text-emerald-600',
        bg: 'bg-emerald-50 dark:bg-emerald-500/10'
      }
    ]
  }

  const handleCloseCreateModal = () => {
    setIsCreateModalOpen(false)
    setNewSpaceForm({ name: '', description: '' })
  }

  const handleCreateSpace = async () => {
    if (!canCreateSpace) return
    try {
      const namespaceId = await createNamespace({
        namespace: trimmedSpaceName,
        description: newSpaceForm.description.trim() || null
      })
      await syncSpaces(String(namespaceId))
      setNewSpaceForm({ name: '', description: '' })
      setIsCreateModalOpen(false)
      toast.success('知识空间创建成功')
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`创建失败：${message}`)
    }
  }

  const handleDocumentUploaded = (doc: Document) => {
    setDocuments((prev) => [doc, ...prev])
    setSpaceList((prev) =>
      prev.map((space) => (space.id === doc.spaceId ? { ...space, docCount: space.docCount + 1 } : space))
    )
    if (currentSpace?.id === doc.spaceId) {
      setCurrentSpace((prev) => (prev ? { ...prev, docCount: prev.docCount + 1 } : prev))
    }
  }

  const handleSelectSpace = useCallback(
    (space: KnowledgeSpace) => {
      setCurrentSpace(space)
      void syncDocuments(space.id)
    },
    [syncDocuments]
  )

  if (!hasSpaces) {
    return (
      <section className="h-full bg-slate-50 dark:bg-[#0f172a] selection:bg-indigo-500/30">
        <WikiHero onCreate={() => setIsCreateModalOpen(true)} />

        <Modal
          isOpen={isCreateModalOpen}
          onClose={handleCloseCreateModal}
          title="创建知识空间"
          subtitle={<span className="text-xs text-slate-400 dark:text-slate-500">Namespace 用于隔离不同业务域的知识文档。</span>}
          size="sm"
          footerRight={
            <>
              <ModalCancelButton onClick={handleCloseCreateModal}>取消</ModalCancelButton>
              <ModalPrimaryButton onClick={handleCreateSpace} disabled={!canCreateSpace}>
                创建空间
              </ModalPrimaryButton>
            </>
          }
        >
          <NamespaceCreateForm
            value={newSpaceForm}
            onChange={setNewSpaceForm}
            showNameError={showNameError}
          />
        </Modal>
      </section>
    )
  }

  const fallbackSpace: KnowledgeSpace = {
    id: '',
    name: '未选择知识空间',
    description: '请先创建知识空间以开始上传文档',
    docCount: 0,
    color: 'bg-slate-400'
  }
  const activeSpace = currentSpace ?? fallbackSpace
  const currentIndex = Math.max(
    0,
    spaceList.findIndex((space) => space.id === activeSpace.id)
  )
  const currentStats = getStats(activeSpace, currentIndex)
  return (
    <section className="h-full bg-slate-50 dark:bg-[#0f172a] selection:bg-indigo-500/30">
      <div className="relative flex h-full">
        <div
          className={`relative flex-shrink-0 flex flex-col min-h-0 bg-transparent transition-[width,margin] duration-300 ${
            isNamespaceCollapsed ? 'w-0 mr-0 border-transparent overflow-hidden' : `${panelWidthClassMap.mediumResponsive} mr-4 border-r border-slate-200 dark:border-slate-800`
          }`}
        >
          {!isNamespaceCollapsed && (
            <>
              <div className={`${paddingClassMap.sm} flex flex-col min-h-0 h-full`}>
                <div className="flex items-center justify-between mb-3">
                  <h3 className={`${TYPOGRAPHY.caption} font-semibold text-slate-500 uppercase tracking-wider`}>知识空间 (Namespaces)</h3>
                  <button
                    type="button"
                    onClick={() => setIsCreateModalOpen(true)}
                    className="text-slate-400 hover:text-indigo-600"
                    aria-label="创建知识空间"
                  >
                    <Plus size={14} />
                  </button>
                </div>

                <div className="space-y-2 flex-1 min-h-0 overflow-y-auto custom-scrollbar pb-8">
                  {spaceList.map((space) => (
                    <div
                      key={space.id}
                      onClick={() => handleSelectSpace(space)}
                      className={`group flex items-start p-3 rounded-lg cursor-pointer transition-all border ${
                        currentSpace?.id === space.id
                          ? 'bg-white dark:bg-slate-900 border-indigo-200 dark:border-indigo-500/30 shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-500/20'
                          : 'border-transparent hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      <div className={`w-2 h-2 mt-1.5 rounded-full mr-3 ${space.color}`} />
                      <div>
                        <div className={`${TYPOGRAPHY.bodySm} font-medium ${currentSpace?.id === space.id ? 'text-slate-900 dark:text-slate-100' : 'text-slate-600 dark:text-slate-300'}`}>
                          {space.name}
                        </div>
                        <div className={`${TYPOGRAPHY.micro} text-slate-400 mt-0.5 line-clamp-1`}>{space.description}</div>
                        <div className={`flex items-center mt-2 ${TYPOGRAPHY.micro} text-slate-400 font-mono`}>
                          <Database size={10} className="mr-1" /> {space.docCount} docs
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsNamespaceCollapsed((prev) => !prev)}
                className="absolute bottom-3 right-3 z-10 size-7 rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:text-indigo-600 dark:border-slate-700 dark:bg-slate-900"
                aria-label="收起知识空间"
                title="收起知识空间"
              >
                <ChevronLeft size={iconSizeToken.small} className="mx-auto" />
              </button>
            </>
          )}
        </div>

        <div
          className={`relative flex-1 min-w-0 min-h-0 ${
            isNamespaceCollapsed ? 'border-l border-slate-200 dark:border-slate-800' : ''
          }`}
        >
          {isNamespaceCollapsed && (
            <button
              type="button"
              onClick={() => setIsNamespaceCollapsed((prev) => !prev)}
              className="absolute bottom-4 left-2 z-10 size-7 rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:text-indigo-600 dark:border-slate-700 dark:bg-slate-900"
              aria-label="展开知识空间"
              title="展开知识空间"
            >
              <ChevronRight size={iconSizeToken.small} className="mx-auto" />
            </button>
          )}

          <div className="h-full overflow-y-auto custom-scrollbar">
            <div
              className={`${contentMaxWidthClassMap.full} ${paddingClassMap.sm} py-4 lg:py-6 w-full mx-auto ${
                isNamespaceCollapsed ? 'pl-10' : ''
              }`}
            >
              <div className="flex flex-col pr-2 pb-6">
                <div className="flex justify-between items-end mb-6 flex-shrink-0">
                  <div>
                    <div className={`flex items-center space-x-2 ${TYPOGRAPHY.legal} text-slate-400 uppercase tracking-widest mb-1`}>
                      <span>Knowledge Wiki</span>
                      <span>/</span>
                      <span className="text-slate-600 dark:text-slate-300">{activeSpace.name}</span>
                    </div>
                    <h2 className={`${TYPOGRAPHY.subtitle} font-bold text-slate-900 dark:text-slate-100 tracking-tight flex items-center`}>
                      {activeSpace.name}
                      <button className="ml-3 text-slate-300 hover:text-slate-500"><Settings size={16} /></button>
                    </h2>
                    <p className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400 mt-1`}>{activeSpace.description}</p>
                  </div>
                  <div className="flex space-x-3">
                    <Button
                      variant="primary"
                      size="small"
                      className="shadow-sm hover:shadow-lg"
                      disabled={!currentSpace?.id}
                      onClick={() => setIsUploadModalOpen(true)}
                    >
                      <Plus size={14} />
                      上传文档至空间
                    </Button>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6 flex-shrink-0">
                  {currentStats.map((stat) => (
                    <Card
                      key={stat.label}
                      padding="sm"
                      className="shadow-sm hover:shadow-md group"
                    >
                      <div className="flex justify-between items-start mb-4">
                        <div className={`p-2 rounded-lg ${stat.bg} ${stat.color} group-hover:scale-110 transition-transform`}>
                          <stat.icon size={18} />
                        </div>
                        <span className={`flex items-center ${RESPONSIVE_TYPOGRAPHY.badge} font-medium px-2 py-1 rounded-full ${stat.trend === 'up' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-200' : 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-200'}`}>
                          {stat.trend === 'up' ? <TrendingUp size={12} className="mr-1" /> : <TrendingUp size={12} className="mr-1 transform rotate-180" />}
                          {stat.change}
                        </span>
                      </div>
                      <div className="flex items-baseline space-x-1">
                        <div className={`${TYPOGRAPHY.subtitle} font-bold text-slate-900 dark:text-slate-100 tracking-tight`}>{stat.value}</div>
                        <div className={`${TYPOGRAPHY.caption} text-slate-400 font-medium`}>{stat.unit}</div>
                      </div>
                      <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400 mt-1 uppercase tracking-wide font-medium opacity-80`}>{stat.label}</div>
                    </Card>
                  ))}
                </div>

                <Card padding="none" className="shadow-sm flex flex-col min-h-[500px] overflow-hidden">
                  <div className="border-b border-slate-200 dark:border-slate-800 px-5 flex items-center justify-between flex-shrink-0">
                    <div className="flex space-x-6">
                      <TabButton
                        active={activeTab === 'DOCUMENTS'}
                        onClick={() => setActiveTab('DOCUMENTS')}
                        label="文档列表"
                        icon={FolderOpen}
                      />
                      <TabButton
                        active={activeTab === 'CHUNKS'}
                        onClick={() => setActiveTab('CHUNKS')}
                        label="切片编辑器"
                        icon={Database}
                      />
                      <TabButton
                        active={activeTab === 'RETRIEVAL_TEST'}
                        onClick={() => setActiveTab('RETRIEVAL_TEST')}
                        label="召回测试 (Playground)"
                        icon={Zap}
                      />
                    </div>
                    <button className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-md text-slate-400">
                      <MoreHorizontal size={18} />
                    </button>
                  </div>

                  <div className={`p-0 bg-slate-50/50 dark:bg-slate-950/40 flex flex-col relative ${activeTab === 'DOCUMENTS' ? 'overflow-hidden' : 'overflow-visible'}`}>
                    {activeTab === 'DOCUMENTS' && (
                      <div className="p-4">
                        <DocList spaceId={activeSpace.id} documents={documents} />
                      </div>
                    )}
                    {activeTab === 'CHUNKS' && (
                      <ChunkManager spaceId={activeSpace.id} spaceName={activeSpace.name} documents={documents} />
                    )}
                    {activeTab === 'RETRIEVAL_TEST' && (
                      <RetrievalPlayground
                        spaceId={activeSpace.id}
                        spaceName={activeSpace.name}
                        documents={documents}
                        isNamespaceCollapsed={isNamespaceCollapsed}
                      />
                    )}
                  </div>
                </Card>
              </div>
            </div>
          </div>
        </div>

        <Modal
          isOpen={isCreateModalOpen}
          onClose={handleCloseCreateModal}
          title="创建知识空间"
          subtitle={<span className="text-xs text-slate-400 dark:text-slate-500">Namespace 用于隔离不同业务域的知识文档。</span>}
          size="sm"
          footerRight={
            <>
              <ModalCancelButton onClick={handleCloseCreateModal}>取消</ModalCancelButton>
              <ModalPrimaryButton onClick={handleCreateSpace} disabled={!canCreateSpace}>
                创建空间
              </ModalPrimaryButton>
            </>
          }
        >
          <NamespaceCreateForm
            value={newSpaceForm}
            onChange={setNewSpaceForm}
            showNameError={showNameError}
          />
        </Modal>

        <UploadDocumentModal
          isOpen={isUploadModalOpen}
          onClose={() => setIsUploadModalOpen(false)}
          spaceId={activeSpace.id}
          spaceName={activeSpace.name}
          onUploaded={handleDocumentUploaded}
        />
      </div>
    </section>
  )
}

type TabButtonProps = {
  active: boolean
  onClick: () => void
  label: string
  icon: LucideIcon
}

function TabButton({ active, onClick, label, icon: Icon }: TabButtonProps) {
  return (
    <button
      onClick={onClick}
      className={`py-3 ${TYPOGRAPHY.caption} font-medium border-b-2 transition-colors duration-200 flex items-center ${
        active
          ? 'border-indigo-600 text-indigo-600'
          : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
      }`}
    >
      <Icon size={12} className={`mr-2 ${active ? 'text-indigo-500' : 'text-slate-400'}`} />
      {label}
    </button>
  )
}
