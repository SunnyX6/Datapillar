import { useEffect,useMemo,useState } from 'react'
import { Shield,User } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Modal,ModalCancelButton,ModalPrimaryButton } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { RoleType } from '../../utils/permissionTypes'

interface CreateRoleModalProps {
 isOpen:boolean
 onClose:() => void
 existingNames:string[]
 initialValues?: {
 name:string
 description?: string
 type:RoleType
 }
 title?: string
 submitLabel?: string
 submittingLabel?: string
 onCreate:(payload:{
 name:string
 description?: string
 type:RoleType
 }) => Promise<boolean> | boolean
}

const ROLE_TYPE_OPTIONS:Array<{
 type:RoleType
 icon:typeof Shield
}> = [{
 type:'ADMIN',icon:Shield,},{
 type:'USER',icon:User,},]

const ROLE_NAME_MAX_LENGTH = 64
const ROLE_DESC_MAX_LENGTH = 255

export function CreateRoleModal({
 isOpen,onClose,existingNames,initialValues,title,submitLabel,submittingLabel,onCreate,}:CreateRoleModalProps) {
 const { t } = useTranslation('permission')
 const [name,setName] = useState(initialValues?.name?? '')
 const [description,setDescription] = useState(initialValues?.description?? '')
 const [roleType,setRoleType] = useState<RoleType>(initialValues?.type?? 'USER')
 const [error,setError] = useState<string | null>(null)
 const [submitting,setSubmitting] = useState(false)
 const resolvedTitle = title ?? t('createRole.title')
 const resolvedSubmitLabel = submitLabel ?? t('createRole.submit')
 const resolvedSubmittingLabel = submittingLabel ?? t('createRole.submitting')

 useEffect(() => {
 if (!isOpen) {
 return
 }
 setName(initialValues?.name?? '')
 setDescription(initialValues?.description?? '')
 setRoleType(initialValues?.type?? 'USER')
 setError(null)
 setSubmitting(false)
 },[initialValues?.description,initialValues?.name,initialValues?.type,isOpen])

 const resetForm = () => {
 setName('')
 setDescription('')
 setRoleType('USER')
 setError(null)
 setSubmitting(false)
 }

 const handleClose = () => {
 resetForm()
 onClose()
 }

 const normalizedNames = useMemo(() => {
 return new Set(existingNames.map((item) => item.trim().toLowerCase()))
 },[existingNames])

 const handleSubmit = async () => {
 if (submitting) {
 return
 }

 const normalizedName = name.trim()
 const normalizedDesc = description.trim()

 if (!normalizedName) {
 setError(t('createRole.errors.nameRequired'))
 return
 }

 if (normalizedName.length > ROLE_NAME_MAX_LENGTH) {
 setError(t('createRole.errors.nameTooLong', { max: ROLE_NAME_MAX_LENGTH }))
 return
 }

 if (normalizedDesc.length > ROLE_DESC_MAX_LENGTH) {
 setError(t('createRole.errors.descriptionTooLong', { max: ROLE_DESC_MAX_LENGTH }))
 return
 }

 if (normalizedNames.has(normalizedName.toLowerCase())) {
 setError(t('createRole.errors.nameDuplicate'))
 return
 }

 setSubmitting(true)
 try {
 const created = await onCreate({
 name:normalizedName,description:normalizedDesc || undefined,type:roleType,})
 if (created === false) {
 return
 }
 handleClose()
 } finally {
 setSubmitting(false)
 }
 }

 return (<Modal
 isOpen={isOpen}
 onClose={handleClose}
 size="sm"
 title={resolvedTitle}
 subtitle={
 <p
 className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400',)}
 >
 {t('createRole.roleTypeHintPrefix')} <span className="font-semibold">ADMIN</span> {t('createRole.roleTypeHintAnd')}{' '}
 <span className="font-semibold">USER</span>
 </p>
 }
 footerLeft={
 <ModalCancelButton onClick={handleClose}>{t('common.button.cancel', { ns: 'common', defaultValue: 'Cancel' })}</ModalCancelButton>
 }
 footerRight={
 <ModalPrimaryButton
 disabled={submitting}
 onClick={() => void handleSubmit()}
 >
 {submitting?resolvedSubmittingLabel:resolvedSubmitLabel}
 </ModalPrimaryButton>
 }
 >
 <div className="space-y-5">
 <div className="space-y-2">
 <label
 className={cn(TYPOGRAPHY.caption,'font-semibold text-slate-700 dark:text-slate-300',)}
 >
 {t('createRole.nameLabel')}
 </label>
 <input
 type="text"
 value={name}
 maxLength={ROLE_NAME_MAX_LENGTH}
 onChange={(event) => {
 setName(event.target.value)
 if (error) {
 setError(null)
 }
 }}
 placeholder={t('createRole.namePlaceholder')}
 className={cn(TYPOGRAPHY.bodySm,'w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all',)}
 />
 <div
 className={cn(TYPOGRAPHY.micro,'text-slate-400 dark:text-slate-500',)}
 >
 {name.trim().length}/{ROLE_NAME_MAX_LENGTH}
 </div>
 </div>

 <div className="space-y-2">
 <label
 className={cn(TYPOGRAPHY.caption,'font-semibold text-slate-700 dark:text-slate-300',)}
 >
 {t('createRole.typeLabel')}
 </label>
 <div className="grid grid-cols-1 gap-2">
 {ROLE_TYPE_OPTIONS.map((item) => {
 const Icon = item.icon
 const active = roleType === item.type
 return (<button
 key={item.type}
 type="button"
 onClick={() => setRoleType(item.type)}
 className={cn('w-full text-left rounded-xl border px-4 py-3 transition-colors',active?'border-brand-500 bg-brand-50 dark:bg-brand-500/10 dark:border-brand-400':'border-slate-200 bg-white dark:bg-slate-900 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-500',)}
 >
 <div className="flex items-start gap-3">
 <Icon
 size={16}
 className={cn('mt-0.5',active?'text-brand-600 dark:text-brand-300':'text-slate-500 dark:text-slate-400',)}
 />
 <div>
 <p
 className={cn(TYPOGRAPHY.bodySm,'font-semibold',active?'text-brand-700 dark:text-brand-200':'text-slate-800 dark:text-slate-200',)}
 >
 {item.type}
 </p>
 <p
 className={cn(TYPOGRAPHY.caption,active?'text-brand-600/80 dark:text-brand-200/80':'text-slate-500 dark:text-slate-400',)}
 >
 {item.type === 'ADMIN' ? t('createRole.typeAdminDescription') : t('createRole.typeUserDescription')}
 </p>
 </div>
 </div>
 </button>)
 })}
 </div>
 </div>

 <div className="space-y-2">
 <label
 className={cn(TYPOGRAPHY.caption,'font-semibold text-slate-700 dark:text-slate-300',)}
 >
 {t('createRole.descriptionLabel')}
 </label>
 <textarea
 value={description}
 maxLength={ROLE_DESC_MAX_LENGTH}
 onChange={(event) => {
 setDescription(event.target.value)
 if (error) {
 setError(null)
 }
 }}
 placeholder={t('createRole.descriptionPlaceholder')}
 rows={3}
 className={cn(TYPOGRAPHY.bodySm,'w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all resize-none',)}
 />
 <div
 className={cn(TYPOGRAPHY.micro,'text-slate-400 dark:text-slate-500',)}
 >
 {description.trim().length}/{ROLE_DESC_MAX_LENGTH}
 </div>
 </div>

 {error && (<div
 className={cn(TYPOGRAPHY.caption,'rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-200',)}
 >
 {error}
 </div>)}
 </div>
 </Modal>)
}
