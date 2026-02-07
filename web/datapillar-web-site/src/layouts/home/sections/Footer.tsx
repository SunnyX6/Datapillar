import { Twitter, Github, Linkedin } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import brandLogo from '@/assets/icons/logo.png'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'

export function Footer() {
  const { t } = useTranslation('home')

  return (
    <footer className="bg-slate-950 border-t border-slate-800 pt-12 pb-6">
      <div className={`${contentMaxWidthClassMap.ultraWide} mx-auto px-8`}>
        <div className="grid grid-cols-4 gap-12 mb-12">
          <div className="col-span-1">
            <div className="flex items-center space-x-2 mb-4">
              <div className="w-8 h-8 flex items-center justify-center">
                <img src={brandLogo} alt="Datapillar" className="h-8 w-8 object-contain" />
              </div>
              <span className="text-xl font-bold text-white tracking-tight">Datapillar</span>
            </div>
            <p className="text-slate-400 text-sm leading-relaxed">
              {t('footer.tagline')}
            </p>
          </div>

          <div>
            <h4 className="text-white font-semibold mb-4">{t('footer.sections.product')}</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.core')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.integration')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.enterprise')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.changelog')}
                </a>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-white font-semibold mb-4">{t('footer.sections.company')}</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.about')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.careers')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.blog')}
                </a>
              </li>
              <li>
                <a href="#" className="hover:text-violet-400 transition-colors">
                  {t('footer.links.legal')}
                </a>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-white font-semibold mb-4">{t('footer.sections.follow')}</h4>
            <div className="flex space-x-4">
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Twitter className="w-5 h-5" />
              </a>
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Github className="w-5 h-5" />
              </a>
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Linkedin className="w-5 h-5" />
              </a>
            </div>
          </div>
        </div>

      </div>
    </footer>
  )
}
