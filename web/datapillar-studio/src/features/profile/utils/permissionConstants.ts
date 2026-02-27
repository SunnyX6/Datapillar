export const PERMISSION_LEVELS = ['DISABLE', 'READ', 'ADMIN'] as const

export type PermissionLevel = (typeof PERMISSION_LEVELS)[number]
