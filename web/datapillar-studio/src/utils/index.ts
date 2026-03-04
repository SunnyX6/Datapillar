import { clsx,type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'
import { TYPOGRAPHY } from '@/design-tokens/typography'

// design system fonts token(Such as `text-body-sm`)with Tailwind of `text-{color}` All with `text-` Beginning.// tailwind-merge When not configured,unknown `text-*` as color class,leading to `text-slate-500` font when merging token swallow,// The final performance is"You obviously changed the font,But the page looks unchanged/Not effective".//
// prudent approach:put `TYPOGRAPHY` All in `text-*` token Register as Tailwind v4 of font-size(theme.text,Correspond `--text-*`),// This will be added later typography token Just write in `TYPOGRAPHY`,wont be beaten again tailwind-merge Contamination by mistake.
const TYPOGRAPHY_TEXT_THEME_VALUES = Array.from(
 new Set(
 Object.values(TYPOGRAPHY)
 .filter((className) => className.startsWith('text-'))
 .map((className) => className.slice('text-'.length))
 )
)

const twMerge = extendTailwindMerge({
 extend:{
 theme:{
 text:TYPOGRAPHY_TEXT_THEME_VALUES
 }
 }
})

export function cn(...inputs:ClassValue[]) {
 return twMerge(clsx(inputs))
}

/**
 * Format time string(Accurate to the second)
 * automatically UTC Convert time to local time zone
 */
export function formatTime(time?: string):string {
 if (!time) return '-'
 try {
 const date = new Date(time)
 const y = date.getFullYear()
 const m = String(date.getMonth() + 1).padStart(2,'0')
 const d = String(date.getDate()).padStart(2,'0')
 const h = String(date.getHours()).padStart(2,'0')
 const min = String(date.getMinutes()).padStart(2,'0')
 const s = String(date.getSeconds()).padStart(2,'0')
 return `${y}-${m}-${d} ${h}:${min}:${s}`
 } catch {
 return '-'
 }
}
