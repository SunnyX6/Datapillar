import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'

export function NotFoundPage() {
  const { t } = useTranslation('home')

  return (
    <div className="min-h-dvh w-full bg-slate-950 text-white flex items-center justify-center px-6">
      <div className="max-w-xl text-center">
        <p className={cn(TYPOGRAPHY.caption, 'uppercase tracking-[0.35em] text-white/50')}>{t('notFound.label')}</p>
        <h1 className={cn(TYPOGRAPHY.display, 'mt-4 font-black tracking-tight')}>{t('notFound.title')}</h1>
        <p className={cn(TYPOGRAPHY.bodySm, 'mt-4 text-white/70')}>{t('notFound.description')}</p>
        <div className="mt-8 flex justify-center">
          <Link to="/">
            <Button size="normal" className="bg-white text-slate-900 hover:bg-slate-100">
              <ArrowLeft size={14} />
              {t('notFound.back')}
            </Button>
          </Link>
        </div>
      </div>
    </div>
  )
}
