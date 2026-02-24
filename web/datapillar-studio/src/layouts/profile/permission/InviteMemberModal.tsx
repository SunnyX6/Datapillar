import { useEffect, useMemo, useState } from 'react'
import { Copy, Shield } from 'lucide-react'
import { toast } from 'sonner'
import { Button, Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { createTenantInvitation, type CreateTenantInvitationResponse } from '@/services/studioTenantAdminService'
import { useAuthStore } from '@/stores/authStore'
import { cn } from '@/lib/utils'
import type { RoleDefinition } from './Permission'

interface InviteMemberModalProps {
  isOpen: boolean
  onClose: () => void
  role: RoleDefinition
  roleId?: string
}

function parseRoleId(rawRoleId: string): number | null {
  const normalized = rawRoleId.trim().replace(/^role_/i, '')
  const parsed = Number.parseInt(normalized, 10)
  return Number.isFinite(parsed) ? parsed : null
}

function buildDefaultExpiresAt(): string {
  const sevenDaysLater = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  return sevenDaysLater.toISOString()
}

const inflightInvitationRequest = new Map<string, Promise<CreateTenantInvitationResponse>>()

function buildInviteRequestKey(tenantId: number, roleId: number): string {
  return `${tenantId}:${roleId}`
}

function getOrCreateInvitationRequest(tenantId: number, roleId: number): Promise<CreateTenantInvitationResponse> {
  const key = buildInviteRequestKey(tenantId, roleId)
  const existingRequest = inflightInvitationRequest.get(key)
  if (existingRequest) {
    return existingRequest
  }

  const request = createTenantInvitation(tenantId, {
    roleId,
    expiresAt: buildDefaultExpiresAt()
  }).finally(() => {
    inflightInvitationRequest.delete(key)
  })

  inflightInvitationRequest.set(key, request)
  return request
}

function buildAbsoluteInviteLink(inviteUri: string): string {
  const normalized = inviteUri.trim()
  if (!normalized) {
    return ''
  }
  if (/^https?:\/\//i.test(normalized)) {
    return normalized
  }
  try {
    return new URL(normalized, window.location.origin).toString()
  } catch {
    return normalized
  }
}

export function InviteMemberModal({ isOpen, onClose, role, roleId }: InviteMemberModalProps) {
  const tenantId = useAuthStore((state) => state.user?.tenantId)
  const [copied, setCopied] = useState(false)
  const [inviteLink, setInviteLink] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)

  const resolvedRoleId = roleId?.trim() || role.id
  const resolvedRoleNumericId = useMemo(() => parseRoleId(resolvedRoleId), [resolvedRoleId])

  useEffect(() => {
    if (!isOpen) {
      return
    }

    if (!tenantId || !resolvedRoleNumericId) {
      setInviteLink('')
      return
    }

    let cancelled = false
    setIsGenerating(true)
    setCopied(false)

    const generateInviteLink = async () => {
      try {
        const response = await getOrCreateInvitationRequest(tenantId, resolvedRoleNumericId)
        if (cancelled) {
          return
        }
        setInviteLink(buildAbsoluteInviteLink(response.inviteUri))
      } catch (error) {
        if (cancelled) {
          return
        }
        setInviteLink('')
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`生成邀请链接失败：${message}`)
      } finally {
        if (!cancelled) {
          setIsGenerating(false)
        }
      }
    }

    void generateInviteLink()

    return () => {
      cancelled = true
    }
  }, [isOpen, resolvedRoleNumericId, tenantId])

  const handleCopy = async () => {
    if (!inviteLink) {
      return
    }
    try {
      await navigator.clipboard.writeText(inviteLink)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  const inviteText = isGenerating
    ? '正在生成邀请链接...'
    : inviteLink || '邀请链接生成失败，请关闭后重试'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="sm"
      title="邀请成员加入"
    >
      <div className="space-y-5">
        <div className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/70 p-5">
          <div className={cn(TYPOGRAPHY.bodySm, 'font-semibold text-slate-500 dark:text-slate-400 mb-4')}>专属邀请链接</div>
          <div className="flex items-center gap-3 rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-4 py-4">
            <div className={cn(TYPOGRAPHY.body, 'flex-1 min-w-0 truncate font-mono text-slate-800 dark:text-slate-200')}>
              {inviteText}
            </div>
            <button
              type="button"
              onClick={handleCopy}
              disabled={!inviteLink || isGenerating}
              className="inline-flex size-11 items-center justify-center rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-300 hover:text-slate-800 dark:hover:text-white transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="复制邀请链接"
            >
              <Copy size={18} />
            </button>
          </div>
          <p className={cn(TYPOGRAPHY.caption, 'mt-2 text-slate-400 dark:text-slate-500')}>
            {copied ? '邀请链接已复制' : '复制后发送给待加入成员'}
          </p>
        </div>

        <div className="rounded-2xl border border-amber-200/80 dark:border-amber-500/30 bg-amber-50/60 dark:bg-amber-500/10 px-5 py-4">
          <div className="flex items-start gap-3">
            <Shield size={18} className="mt-0.5 text-amber-600 dark:text-amber-300 shrink-0" />
            <p className={cn(TYPOGRAPHY.bodySm, 'font-semibold text-amber-700 dark:text-amber-200 leading-relaxed')}>
              持有此链接的成员在登录后将自动获得 <span className="font-black">{role.name}</span> 角色的所有权限。请妥善保管。
            </p>
          </div>
        </div>

        <div className="pt-1">
          <Button
            size="normal"
            onClick={onClose}
            className="w-full bg-slate-950 hover:bg-slate-800 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-100 text-white py-3.5"
          >
            确认并关闭
          </Button>
        </div>
      </div>
    </Modal>
  )
}
