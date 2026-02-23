import { useMemo, useState } from 'react'
import { Copy, Shield } from 'lucide-react'
import { Button, Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { useAuthStore } from '@/stores/authStore'
import { cn } from '@/lib/utils'
import type { RoleDefinition } from './Permission'

interface InviteMemberModalProps {
  isOpen: boolean
  onClose: () => void
  role: RoleDefinition
}

function buildInviteCode(roleId: string) {
  const roleSegment = roleId.replace('role_', '').slice(0, 6).toUpperCase()
  const randomSegment =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().replace(/-/g, '').slice(0, 10).toUpperCase()
      : Math.random().toString(36).slice(2, 12).toUpperCase()
  return `${roleSegment}${randomSegment}`
}

export function InviteMemberModal({ isOpen, onClose, role }: InviteMemberModalProps) {
  const tenantCode = useAuthStore((state) => state.user?.tenantCode?.trim() ?? '')
  const [copied, setCopied] = useState(false)

  const inviteCode = useMemo(() => buildInviteCode(role.id), [role.id])

  const inviteLink = useMemo(() => {
    const params = new URLSearchParams()
    if (tenantCode) {
      params.set('tenantCode', tenantCode)
    }
    params.set('inviteCode', inviteCode)
    params.set('roleId', role.id)
    const origin = typeof window !== 'undefined' ? window.location.origin : ''
    const path = `/invite?${params.toString()}`
    return origin ? `${origin}${path}` : path
  }, [inviteCode, role.id, tenantCode])

  const handleCopy = async () => {
    if (!inviteLink) return
    try {
      await navigator.clipboard.writeText(inviteLink)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

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
              {inviteLink}
            </div>
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex size-11 items-center justify-center rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-300 hover:text-slate-800 dark:hover:text-white transition-colors"
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
