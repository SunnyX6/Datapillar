import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2, CheckCircle } from 'lucide-react'
import { Modal, Select, type SelectOption } from '@/components/ui'

const ApplicationStatus = {
  IDLE: 'idle',
  SUBMITTING: 'submitting',
  SUCCESS: 'success'
} as const

type ApplicationStatus = (typeof ApplicationStatus)[keyof typeof ApplicationStatus]

interface RequestAccessModalProps {
  isOpen: boolean
  onClose: () => void
}

const DEFAULT_FORM = {
  name: '',
  email: '',
  company: '',
  role: 'CTO'
}
export function RequestAccessModal({ isOpen, onClose }: RequestAccessModalProps) {
  const { t, i18n } = useTranslation('home')
  const [status, setStatus] = useState<ApplicationStatus>(ApplicationStatus.IDLE)
  const [formData, setFormData] = useState(DEFAULT_FORM)
  const submitTimerRef = useRef<number | null>(null)
  const roleOptions = useMemo<SelectOption[]>(
    () => [
      { value: 'CTO', label: t('requestAccess.form.role.options.CTO') },
      { value: 'DataEngineer', label: t('requestAccess.form.role.options.DataEngineer') },
      { value: 'Product', label: t('requestAccess.form.role.options.Product') },
      { value: 'Other', label: t('requestAccess.form.role.options.Other') }
    ],
    [t, i18n.language]
  )

  useEffect(() => {
    if (!isOpen) return
    setStatus(ApplicationStatus.IDLE)
    setFormData(DEFAULT_FORM)
  }, [isOpen])

  useEffect(() => {
    return () => {
      if (submitTimerRef.current) {
        window.clearTimeout(submitTimerRef.current)
      }
    }
  }, [])

  if (!isOpen) return null

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setStatus(ApplicationStatus.SUBMITTING)

    if (submitTimerRef.current) {
      window.clearTimeout(submitTimerRef.current)
    }

    submitTimerRef.current = window.setTimeout(() => {
      setStatus(ApplicationStatus.SUCCESS)
    }, 1500)
  }

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }
  const handleRoleChange = (value: string) => {
    setFormData((prev) => ({ ...prev, role: value }))
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="mini"
      title={status === ApplicationStatus.SUCCESS ? undefined : t('requestAccess.title')}
      subtitle={
        status === ApplicationStatus.SUCCESS ? undefined : (
          <p className="text-slate-500 dark:text-slate-400 text-sm">{t('requestAccess.subtitle')}</p>
        )
      }
    >
      {status === ApplicationStatus.SUCCESS ? (
        <div className="text-center py-8">
          <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-5">
            <CheckCircle className="w-8 h-8 text-green-500" />
          </div>
          <h3 className="text-xl font-bold text-white mb-2">{t('requestAccess.successTitle')}</h3>
          <p className="text-slate-400 mb-6 text-sm">
            {t('requestAccess.successBody')}
            <strong className="ml-1">{formData.email}</strong>
            {t('requestAccess.successSuffix')}
          </p>
          <button onClick={onClose} className="px-5 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-lg transition-colors">
            {t('requestAccess.close')}
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-300 mb-1">
              {t('requestAccess.form.name.label')}
            </label>
            <input
              type="text"
              id="name"
              name="name"
              required
              value={formData.name}
              onChange={handleChange}
              className="w-full bg-slate-900/80 border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder:text-xs placeholder:text-slate-500/80 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
              placeholder={t('requestAccess.form.name.placeholder')}
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-300 mb-1">
              {t('requestAccess.form.email.label')}
            </label>
            <input
              type="email"
              id="email"
              name="email"
              required
              value={formData.email}
              onChange={handleChange}
              className="w-full bg-slate-900/80 border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder:text-xs placeholder:text-slate-500/80 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
              placeholder={t('requestAccess.form.email.placeholder')}
            />
          </div>

          <div className="grid grid-cols-1 gap-4">
            <div>
              <label htmlFor="company" className="block text-sm font-medium text-slate-300 mb-1">
                {t('requestAccess.form.company.label')}
              </label>
              <input
                type="text"
                id="company"
                name="company"
                required
                value={formData.company}
                onChange={handleChange}
                className="w-full bg-slate-900/80 border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder:text-xs placeholder:text-slate-500/80 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
                placeholder={t('requestAccess.form.company.placeholder')}
              />
            </div>
            <div>
              <label htmlFor="role" className="block text-sm font-medium text-slate-300 mb-1">
                {t('requestAccess.form.role.label')}
              </label>
              <Select
                value={formData.role}
                options={roleOptions}
                onChange={handleRoleChange}
                placeholder={t('requestAccess.form.role.placeholder')}
                size="sm"
                className="bg-slate-900/80 border-slate-700 text-white"
              />
            </div>
          </div>

          <div className="pt-4">
            <button
              type="submit"
              disabled={status === ApplicationStatus.SUBMITTING}
              className="w-full py-3 bg-[#5558ff] hover:bg-[#4548e6] text-white rounded-lg font-bold shadow-lg shadow-violet-500/20 transition-all flex items-center justify-center gap-2"
            >
              {status === ApplicationStatus.SUBMITTING ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  {t('requestAccess.submitting')}
                </>
              ) : (
                t('requestAccess.submit')
              )}
            </button>
            <p className="text-center text-xs text-slate-500 mt-4">{t('requestAccess.agreement')}</p>
          </div>
        </form>
      )}
    </Modal>
  )
}
