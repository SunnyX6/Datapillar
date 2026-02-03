import { cn } from '@/lib/utils'

interface PermissionAvatarProps {
  name: string
  src?: string
  size?: 'sm' | 'lg'
  className?: string
}

export function PermissionAvatar({ name, src, size = 'sm', className = '' }: PermissionAvatarProps) {
  const sizeClass = size === 'lg' ? 'w-12 h-12 text-sm' : 'w-8 h-8 text-xs'
  const initials = name.slice(0, 2)

  return (
    <div
      className={cn(
        'rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-200 flex items-center justify-center font-semibold overflow-hidden',
        sizeClass,
        className
      )}
    >
      {src ? <img src={src} alt={name} className="w-full h-full rounded-full object-cover" /> : initials}
    </div>
  )
}
