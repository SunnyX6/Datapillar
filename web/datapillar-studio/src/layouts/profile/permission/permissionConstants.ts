export const PERMISSION_LEVELS = ['NONE', 'READ', 'WRITE'] as const

export type PermissionLevel = (typeof PERMISSION_LEVELS)[number]
