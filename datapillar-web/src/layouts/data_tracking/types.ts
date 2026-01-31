export type TrackingTab = 'PLAN' | 'LIBRARY'

export type DrawerMode = 'SCHEMA' | 'TRACKING'

export type TrackingStatus = 'planned' | 'implemented' | 'tested'

export type TrackingPlatform = 'Web' | 'App' | 'Server'

export type SchemaKind = 'ATOMIC' | 'COMPOSITE'

export interface SchemaProperty {
  id: string
  name: string
}

export interface EventSchema {
  id: string
  key: string
  name: string
  kind: SchemaKind
  description: string
  domain: string
  usageCount: number
  standardProperties: SchemaProperty[]
}

export interface TrackingPoint {
  id: string
  schemaId: string
  schemaName: string
  viewPath: string
  platform: TrackingPlatform
  triggerDescription: string
  status: TrackingStatus
  contextProperties: SchemaProperty[]
}
