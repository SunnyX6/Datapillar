export type Column = {
  name: string
  dataType: string
  comment?: string
  isPrimaryKey?: boolean
  piiTag?: string
}

export type TableAsset = {
  id: string
  name: string
  description: string
  certification?: 'GOLD' | 'SILVER' | 'BRONZE'
  qualityScore: number
  rowCount: number
  size: string
  owner: string
  updatedAt: string
  domains: string[]
  columns: Column[]
  properties?: Record<string, string>
}

export type SchemaAsset = {
  id: string
  name: string
  catalogId: string
  tables: TableAsset[]
}

export type CatalogAsset = {
  id: string
  name: string
  icon?: string
  provider?: string
  schemas: SchemaAsset[]
}

export type NodeType = 'ROOT' | 'CATALOG' | 'SCHEMA' | 'TABLE'
