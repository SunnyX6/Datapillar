import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Check, CheckCircle2, Eye } from 'lucide-react'
import { toast } from 'sonner'
import { AppLayout, SplitGrid, useLayout } from '@/layouts/responsive'
import { BrandLogo, Button, ThemeToggle, LanguageToggle } from '@/components'
import { DemoCanvas } from '@/pages/login/DemoCanvas'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { cn } from '@/lib/utils'
import { getSetupStatus, initializeSetup, type SetupStatusResponse, type SetupStep as SetupStatusStep, type SetupStepStatus } from '@/lib/api/setup'

type SetupUiStep = 1 | 2 | 3 | 4

type SetupLogType = 'info' | 'success' | 'warn'

interface SetupLog {
  text: string
  type: SetupLogType
}

interface SetupFormData {
  organizationName: string
  username: string
  email: string
  password: string
}

interface InputGroupProps {
  label: string
  type?: 'text' | 'email' | 'password'
  value: string
  placeholder: string
  required?: boolean
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
}

function toSetupLogType(status: SetupStepStatus): SetupLogType {
  if (status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'IN_PROGRESS') {
    return 'info'
  }
  return 'warn'
}

function buildLogsFromSteps(steps?: SetupStatusStep[]): SetupLog[] {
  if (!steps || steps.length === 0) {
    return [{ text: '等待初始化步骤返回...', type: 'info' }]
  }
  return steps.map((step) => ({
    text: `${step.name}${step.description ? ` - ${step.description}` : ''}`,
    type: toSetupLogType(step.status)
  }))
}

function calcProgress(steps?: SetupStatusStep[]): number {
  if (!steps || steps.length === 0) {
    return 0
  }
  const completedCount = steps.filter((step) => step.status === 'COMPLETED').length
  const inProgressCount = steps.filter((step) => step.status === 'IN_PROGRESS').length
  return Math.min(100, ((completedCount + inProgressCount * 0.5) / steps.length) * 100)
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }
  return '初始化失败，请重试'
}

const SETUP_FORM_BASE_WIDTH = 400
const SETUP_FORM_BASE_HEIGHT = 700
const SETUP_FORM_WIDTH_CLASS = 'w-[400px] max-w-[400px]'

const SETUP_LABEL_CLASS = 'text-xs font-semibold tracking-[0.05em] text-slate-500 dark:text-slate-400'
const SETUP_INPUT_CLASS =
  'select-text w-full rounded-xl border border-slate-200/60 dark:border-slate-800/60 bg-white dark:bg-[#0B1120] px-4 py-2.5 text-sm text-slate-900 dark:text-white placeholder:text-[11px] placeholder:text-slate-400 dark:placeholder:text-slate-600 selection:bg-indigo-500/20 selection:text-slate-900 dark:selection:bg-indigo-400/35 dark:selection:text-white transition-all focus:border-indigo-400/70 focus:ring-[0.5px] focus:ring-indigo-400/60 focus:outline-none'
const SETUP_PRIMARY_BUTTON_CLASS = cn(
  'w-full rounded-lg text-sm font-semibold text-white shadow-md shadow-indigo-500/20',
  'flex items-center justify-center gap-2',
  'bg-indigo-500 hover:bg-indigo-600 active:bg-indigo-700',
  'transition-colors duration-150',
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-400',
  'disabled:cursor-not-allowed disabled:bg-indigo-400'
)

function InputGroup({ label, type = 'text', value, onChange, placeholder, required }: InputGroupProps) {
  return (
    <div className="group">
      <label className={`mb-2 block transition-colors group-focus-within:text-indigo-500 ${SETUP_LABEL_CLASS}`}>
        {label}
      </label>
      <div className="relative">
        <input
          type={type}
          className={SETUP_INPUT_CLASS}
          placeholder={placeholder}
          value={value}
          onChange={onChange}
          required={required}
        />
        {type === 'password' && (
          <span className="absolute top-1/2 right-3 -translate-y-1/2 text-slate-400">
            <Eye className="h-5 w-5" />
          </span>
        )}
      </div>
    </div>
  )
}

export function SetupPage() {
  const { t } = useTranslation('login')
  const navigate = useNavigate()
  const [step, setStep] = useState<SetupUiStep>(1)
  const [formData, setFormData] = useState<SetupFormData>({
    organizationName: '',
    username: '',
    email: '',
    password: ''
  })
  const [installProgress, setInstallProgress] = useState(0)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [logs, setLogs] = useState<SetupLog[]>([])
  const [setupStatus, setSetupStatus] = useState<SetupStatusResponse | null>(null)
  const [schemaWaiting, setSchemaWaiting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const logsEndRef = useRef<HTMLDivElement | null>(null)
  const stepRef = useRef<SetupUiStep>(1)
  const submittingRef = useRef(false)

  useEffect(() => {
    stepRef.current = step
  }, [step])

  useEffect(() => {
    submittingRef.current = submitting
  }, [submitting])

  const { ref: rightPaneRef, scale: formScale, ready: formReady } = useLayout<HTMLDivElement>({
    baseWidth: SETUP_FORM_BASE_WIDTH,
    baseHeight: SETUP_FORM_BASE_HEIGHT,
    scaleFactor: 1.0,
    minScale: 0.5,
    maxScale: 1.5
  })

  const panelStyle = useMemo(() => ({
    transform: `scale(${formScale})`,
    transformOrigin: 'center center',
    opacity: formReady ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [formScale, formReady])

  const headerBrandStyle = useMemo(() => ({
    transform: `scale(${formScale})`,
    transformOrigin: 'left top',
    opacity: formReady ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [formScale, formReady])

  useEffect(() => {
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = originalOverflow
    }
  }, [])

  const syncSetupStatus = useCallback(async () => {
    try {
      const status = await getSetupStatus()
      setSetupStatus(status)
      const currentStep = stepRef.current

      if (status.initialized && currentStep !== 4) {
        navigate('/login', { replace: true })
        return
      }

      const waitingSchema = !status.schemaReady
      setSchemaWaiting(waitingSchema)

      if (waitingSchema) {
        setStep(3)
        setLogs(buildLogsFromSteps(status.steps))
        setInstallProgress(calcProgress(status.steps))
        return
      }

      if (!submittingRef.current && currentStep === 3) {
        setStep(1)
        setLogs([])
        setInstallProgress(0)
      }
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }, [navigate])

  useEffect(() => {
    void syncSetupStatus()
  }, [syncSetupStatus])

  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  const updateForm = (field: keyof SetupFormData, value: string) => {
    setFormData((previous) => ({
      ...previous,
      [field]: value
    }))
  }

  const isStep1Valid = formData.organizationName.trim().length > 0
  const isStep2Valid =
    formData.username.trim().length > 0 &&
    formData.email.trim().length > 0 &&
    formData.password.length > 0 &&
    termsAccepted

  return (
    <AppLayout
      surface="dark"
      padding="none"
      align="stretch"
      maxWidthClassName="max-w-none"
      scrollBehavior="hidden"
      className="relative font-sans selection:bg-indigo-500/30 selection:text-indigo-200"
      contentClassName="flex w-full flex-1 overflow-hidden"
    >
      <SplitGrid
        columns={[0.65, 0.35]}
        stackAt="never"
        gapX="none"
        gapY="lg"
        className="flex-1 w-full"
        leftClassName="overflow-hidden bg-[#02040a]"
        rightClassName="overflow-hidden bg-white dark:bg-[#020617]"
        left={
          <div className="flex h-full w-full items-center justify-center">
            <DemoCanvas />
          </div>
        }
        right={
          <div ref={rightPaneRef} className="@container relative flex h-dvh max-h-dvh w-full flex-col overflow-hidden bg-white dark:bg-[#020617]">
            <div className="sticky top-0 z-20 flex items-center bg-white/80 dark:bg-[#020617]/80 px-6 py-4 backdrop-blur-md">
              <div className="absolute top-4 right-4 z-30 flex items-center gap-3">
                <ThemeToggle />
                <LanguageToggle />
              </div>
              <div style={headerBrandStyle}>
                <BrandLogo
                  size={44}
                  showText
                  brandName={t('brand.name')}
                  brandTagline={t('setup.brandTagline')}
                  nameClassName="text-lg font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200"
                />
              </div>
            </div>

            <div className="flex flex-1 items-center justify-center overflow-hidden">
              <div
                className={cn(
                  'relative flex w-full flex-col gap-3 md:gap-4 lg:gap-5 rounded-none',
                  paddingClassMap.md,
                  SETUP_FORM_WIDTH_CLASS
                )}
                style={panelStyle}
              >
                {step === 1 && (
                  <div className="animate-fade-in-up">
                    <div className="mb-7 text-center">
                      <h2 className="mb-2 text-lg font-semibold text-slate-900 dark:text-slate-100">{t('setup.step1.title')}</h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{t('setup.step1.subtitle')}</p>
                    </div>

                    <form
                      className="space-y-6"
                      noValidate
                      onSubmit={(event) => {
                        event.preventDefault()
                        if (!isStep1Valid) {
                          return
                        }
                        setStep(2)
                      }}
                    >
                      <InputGroup
                        label={t('setup.form.orgName.label')}
                        placeholder={t('setup.form.orgName.placeholder')}
                        value={formData.organizationName}
                        onChange={(event) => updateForm('organizationName', event.target.value)}
                        required
                      />

                      <Button
                        type="submit"
                        size="normal"
                        disabled={!isStep1Valid}
                        className={SETUP_PRIMARY_BUTTON_CLASS}
                      >
                        {t('setup.actions.continue')} <ArrowRight size={18} />
                      </Button>
                    </form>
                  </div>
                )}

                {step === 2 && (
                  <div className="animate-fade-in-up">
                    <div className="mb-6">
                      <button
                        type="button"
                        onClick={() => setStep(1)}
                        className="mb-6 flex items-center gap-1 text-xs font-semibold tracking-[0.05em] text-slate-400 dark:text-slate-500 transition-colors hover:text-slate-600 dark:hover:text-slate-300"
                      >
                        ← {t('setup.step2.back')}
                      </button>
                      <h2 className="mb-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{t('setup.step2.title')}</h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{t('setup.step2.subtitle')}</p>
                    </div>

                    <form
                      className="space-y-4"
                      noValidate
                      onSubmit={(event) => {
                        event.preventDefault()
                        if (!isStep2Valid || submitting) {
                          return
                        }

                        setSubmitting(true)
                        setStep(3)

                        const runningLogs = buildLogsFromSteps(setupStatus?.steps)
                        setLogs(runningLogs)
                        setInstallProgress(calcProgress(setupStatus?.steps))

                        void (async () => {
                          try {
                            await initializeSetup({
                              organizationName: formData.organizationName,
                              adminName: formData.username,
                              username: formData.username,
                              email: formData.email.trim(),
                              password: formData.password
                            })

                            const latestStatus = await getSetupStatus()
                            setSetupStatus(latestStatus)
                            setSchemaWaiting(!latestStatus.schemaReady)
                            setLogs(buildLogsFromSteps(latestStatus.steps))
                            setInstallProgress(100)
                            setStep(4)
                          } catch (error) {
                            toast.error(getErrorMessage(error))
                            setStep(2)
                          } finally {
                            setSubmitting(false)
                          }
                        })()
                      }}
                    >
                      <InputGroup
                        label={t('setup.form.username.label')}
                        placeholder={t('setup.form.username.placeholder')}
                        value={formData.username}
                        onChange={(event) => updateForm('username', event.target.value)}
                        required
                      />
                      <InputGroup
                        label={t('setup.form.email.label')}
                        type="email"
                        placeholder={t('setup.form.email.placeholder')}
                        value={formData.email}
                        onChange={(event) => updateForm('email', event.target.value)}
                        required
                      />
                      <InputGroup
                        label={t('setup.form.password.label')}
                        type="password"
                        placeholder={t('setup.form.password.placeholder')}
                        value={formData.password}
                        onChange={(event) => updateForm('password', event.target.value)}
                        required
                      />

                      <div className="flex items-center gap-2.5 py-2 select-none">
                        <label htmlFor="terms" className="flex items-center gap-2.5 group cursor-pointer">
                          <input
                            id="terms"
                            type="checkbox"
                            checked={termsAccepted}
                            onChange={(event) => setTermsAccepted(event.target.checked)}
                            required
                            className="sr-only"
                          />
                          <span
                            className={`flex h-3.5 w-3.5 items-center justify-center rounded-[5px] border transition-all duration-200 ${
                              termsAccepted
                                ? 'border-transparent bg-gradient-to-br from-indigo-500 to-purple-500 text-white shadow-[0_8px_14px_rgba(79,70,229,0.35)]'
                                : 'border-slate-400/70 dark:border-slate-600 bg-transparent text-transparent group-hover:border-indigo-400/70 dark:group-hover:border-indigo-500/60'
                            }`}
                          >
                            {termsAccepted ? <Check size={10} strokeWidth={2} /> : null}
                          </span>
                          <span className="text-xs font-semibold tracking-[0.05em] text-slate-500 dark:text-slate-400 transition-colors group-hover:text-slate-900 dark:group-hover:text-slate-300">
                            {t('setup.form.terms.prefix')}{' '}
                            <a href="#" className="text-indigo-600 dark:text-indigo-300 hover:underline">
                              {t('setup.form.terms.link')}
                            </a>
                          </span>
                        </label>
                      </div>

                      <Button
                        type="submit"
                        size="normal"
                        disabled={!isStep2Valid || submitting || schemaWaiting}
                        className={SETUP_PRIMARY_BUTTON_CLASS}
                      >
                        {t('setup.actions.install')}
                      </Button>
                    </form>
                  </div>
                )}

                {step === 3 && (
                  <div className="w-full animate-fade-in-up">
                    <div className="mb-6 text-center">
                      <div className="relative mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-indigo-50">
                        <svg className="h-8 w-8 animate-spin text-indigo-600" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                          <path
                            className="opacity-75"
                            fill="currentColor"
                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                          />
                        </svg>
                      </div>
                      <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{t('setup.step3.title')}</h2>
                      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{schemaWaiting ? t('setup.step3.waitingMigration') : t('setup.step3.subtitle')}</p>
                    </div>

                    {setupStatus?.steps?.length ? (
                      <div className="mb-4 rounded-xl border border-slate-200/70 bg-slate-50 px-3 py-2 text-xs dark:border-slate-700 dark:bg-slate-900/70">
                        {setupStatus.steps.map((stepItem) => (
                          <div key={stepItem.code} className="flex items-center justify-between py-1">
                            <span className="text-slate-600 dark:text-slate-300">{stepItem.name}</span>
                            <span className={cn(
                              'rounded px-2 py-0.5 text-xs font-semibold',
                              stepItem.status === 'COMPLETED'
                                ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
                                : stepItem.status === 'IN_PROGRESS'
                                  ? 'bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300'
                                  : 'bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
                            )}
                            >
                              {stepItem.status}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : null}

                    <div className="overflow-hidden rounded-xl border border-slate-800 bg-[#1e1e2e] shadow-2xl">
                      <div className="flex items-center border-b border-slate-700 bg-[#282838] px-4 py-2">
                        <div className="flex gap-1.5">
                          <div className="h-2.5 w-2.5 rounded-full bg-red-500" />
                          <div className="h-2.5 w-2.5 rounded-full bg-yellow-500" />
                          <div className="h-2.5 w-2.5 rounded-full bg-green-500" />
                        </div>
                        <div className="ml-4 text-xs font-mono text-slate-400">{t('setup.step3.logFile')}</div>
                      </div>
                      <div className="scrollbar-hide h-64 overflow-y-auto p-4 font-mono text-xs">
                        {logs.map((log, index) => (
                          <div key={`${log.text}-${index}`} className="animate-fade-in mb-2 flex gap-3">
                            <span className="shrink-0 text-slate-500">{(index + 1).toString().padStart(2, '0')}</span>
                            <span
                              className={
                                log.type === 'success'
                                  ? 'text-green-400'
                                  : log.type === 'warn'
                                    ? 'text-yellow-400'
                                    : 'text-blue-300'
                              }
                            >
                              {log.type === 'success' && '✓ '}
                              {log.type === 'warn' && '⚠ '}
                              {log.text}
                            </span>
                          </div>
                        ))}
                        <div ref={logsEndRef} />
                        {installProgress < 100 && <div className="mt-1 ml-6 h-4 w-2 animate-pulse bg-slate-400" />}
                      </div>
                      <div className="h-1 w-full bg-[#282838]">
                        <div className="flex h-full gap-px">
                          {Array.from({ length: Math.max(setupStatus?.steps?.length ?? logs.length, 1) }).map((_, index) => {
                            const total = Math.max(setupStatus?.steps?.length ?? logs.length, 1)
                            const completed = Math.round((installProgress / 100) * total)
                            return (
                              <div
                                key={`progress-${index}`}
                                className={index < completed ? 'flex-1 bg-green-500' : 'flex-1 bg-transparent'}
                              />
                            )
                          })}
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {step === 4 && (
                  <div className="animate-fade-in-up text-center">
                    <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-green-100 text-green-600 shadow-inner">
                      <CheckCircle2 className="h-10 w-10" />
                    </div>
                    <h2 className="mb-2 text-base font-semibold text-slate-900 dark:text-slate-100">{t('setup.step4.title')}</h2>
                    <Button
                      type="button"
                      onClick={() => navigate('/login')}
                      size="normal"
                      className={SETUP_PRIMARY_BUTTON_CLASS}
                    >
                      {t('setup.actions.toLogin')}
                    </Button>
                  </div>
                )}
              </div>
            </div>

            <div className="px-6 pb-2">
              <div className="mx-auto flex w-fit items-center gap-2">
                {[1, 2, 3].map((index) => (
                  <div
                    key={`setup-progress-bottom-${index}`}
                    className={`h-1 rounded-full transition-all duration-300 ${step >= index ? 'w-8 bg-indigo-600' : 'w-2 bg-slate-200 dark:bg-slate-700'}`}
                  />
                ))}
              </div>
            </div>

            <div className="p-6 text-center text-xs text-slate-400 dark:text-slate-500">
              &copy; 2026 Datapillar Inc. <span className="mx-2">&bull;</span> {t('setup.footer.privacy')} <span className="mx-2">&bull;</span> {t('setup.footer.terms')}
            </div>
          </div>
        }
      />
    </AppLayout>
  )
}
