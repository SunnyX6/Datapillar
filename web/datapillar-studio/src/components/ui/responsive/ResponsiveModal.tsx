/**
 * Responsive modal component
 *
 * Full screen display on mobile,Centered display on desktop
 * Automatic processing of mask layers,scroll lock,ESC close
 *
 * Usage:* ```tsx
 * <ResponsiveModal open={isOpen} onClose={() => setIsOpen(false)}>
 * <ResponsiveModal.Header>Title</ResponsiveModal.Header>
 * <ResponsiveModal.Body>content</ResponsiveModal.Body>
 * <ResponsiveModal.Footer>
 * <Button onClick={() => setIsOpen(false)}>close</Button>
 * </ResponsiveModal.Footer>
 * </ResponsiveModal>
 * ```
 */

import { type ReactNode,useEffect } from 'react'
import { cn } from '@/utils'
import { X } from 'lucide-react'
import { Button } from '../Button'
import {
 modalWidthClassMap,modalHeightClassMap,type ModalWidth,type ModalHeight
} from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'

interface ResponsiveModalProps {
 children:ReactNode
 /** Whether to open */
 open:boolean
 /** Close callback */
 onClose:() => void
 /** Modal box size(Default responsive,Automatic adaptation)*/
 size?: ModalWidth
 height?: ModalHeight
 /** Whether to display a close button */
 showClose?: boolean
 /** Whether to click on the mask layer to close it */
 closeOnOverlay?: boolean
 /** Custom class name */
 className?: string
}

export function ResponsiveModal({
 children,open,onClose,size = 'normal',height = 'limited',showClose = true,closeOnOverlay = true,className
}:ResponsiveModalProps) {
 // Lock page scrolling
 useEffect(() => {
 if (open) {
 document.body.style.overflow = 'hidden'
 return () => {
 document.body.style.overflow = ''
 }
 }
 },[open])

 // ESC key off
 useEffect(() => {
 if (!open) return

 const handleEsc = (e:KeyboardEvent) => {
 if (e.key === 'Escape') onClose()
 }

 document.addEventListener('keydown',handleEsc)
 return () => document.removeEventListener('keydown',handleEsc)
 },[open,onClose])

 if (!open) return null

 return (<div
 className="fixed inset-0 z-[9999] flex items-center justify-center p-4"
 onClick={closeOnOverlay?onClose:undefined}
 >
 {/* mask layer */}
 <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />

 {/* Modal box content */}
 <div
 className={cn('relative bg-white dark:bg-[#1e293b] rounded-2xl shadow-2xl','overflow-hidden flex flex-col',modalWidthClassMap[size],modalHeightClassMap[height],className)}
 onClick={(e) => e.stopPropagation()}
 >
 {/* close button */}
 {showClose && (<Button
 type="button"
 onClick={onClose}
 variant="ghost"
 size="icon"
 className="absolute top-4 right-4 z-10 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white transition-colors"
 aria-label="close"
 >
 <X size={20} />
 </Button>)}

 {children}
 </div>
 </div>)
}

/** Modal box header */
ResponsiveModal.Header = function ResponsiveModalHeader({
 children,className
}:{
 children:ReactNode
 className?: string
}) {
 return (<div
 className={cn('px-6 py-5 border-b border-slate-200 dark:border-slate-700',TYPOGRAPHY.subtitle,'font-bold','text-slate-900 dark:text-white',className)}
 >
 {children}
 </div>)
}

/** Modal body */
ResponsiveModal.Body = function ResponsiveModalBody({
 children,className
}:{
 children:ReactNode
 className?: string
}) {
 return (<div
 className={cn('flex-1 px-6 py-5 overflow-y-auto custom-scrollbar',TYPOGRAPHY.body,'text-slate-700 dark:text-slate-300',className)}
 >
 {children}
 </div>)
}

/** Modal box bottom */
ResponsiveModal.Footer = function ResponsiveModalFooter({
 children,className
}:{
 children:ReactNode
 className?: string
}) {
 return (<div
 className={cn('px-6 py-4 border-t border-slate-200 dark:border-slate-700','flex items-center justify-end gap-3',className)}
 >
 {children}
 </div>)
}
