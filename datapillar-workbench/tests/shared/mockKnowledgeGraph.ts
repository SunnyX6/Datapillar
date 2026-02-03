import type { Page } from '@playwright/test'

type Neo4jNode = {
  id: number
  type: string
  level: number
  properties: {
    name: string
    displayName?: string
    description?: string
    owner?: string
    tags?: string[]
    updatedAt?: string
  }
}

type Neo4jRelationship = {
  id: number
  start: number
  end: number
  type: string
  properties: Record<string, unknown>
}

const baseNodes: Neo4jNode[] = [
  {
    id: 1,
    type: 'Domain',
    level: 0,
    properties: {
      name: 'Commerce',
      displayName: 'Business Domain',
      description: 'Core commerce domain',
      owner: 'data-owner',
      tags: ['core', 'commerce'],
      updatedAt: '2024-01-01T00:00:00Z'
    }
  },
  {
    id: 2,
    type: 'Catalog',
    level: 1,
    properties: {
      name: 'Sales_Catalog',
      displayName: 'Sales Catalog',
      description: 'Sales data catalog',
      owner: 'data-owner',
      tags: ['sales'],
      updatedAt: '2024-01-01T00:00:00Z'
    }
  },
  {
    id: 3,
    type: 'Schema',
    level: 2,
    properties: {
      name: 'sales',
      displayName: 'Sales Domain',
      description: 'Sales subject data',
      owner: 'data-owner',
      updatedAt: '2024-01-01T00:00:00Z'
    }
  },
  {
    id: 4,
    type: 'Table',
    level: 3,
    properties: {
      name: 'fact_orders',
      displayName: 'Order Fact Table',
      description: 'Orders and payments fact table',
      owner: 'data-owner',
      updatedAt: '2024-01-01T00:00:00Z'
    }
  },
  {
    id: 5,
    type: 'Column',
    level: 4,
    properties: {
      name: 'order_id',
      displayName: 'Order ID',
      description: 'Order primary key',
      updatedAt: '2024-01-01T00:00:00Z'
    }
  },
  {
    id: 6,
    type: 'Column',
    level: 4,
    properties: {
      name: 'amount',
      displayName: 'Order Amount',
      description: 'Order amount field',
      updatedAt: '2024-01-01T00:00:00Z'
    }
  }
]

const baseRelationships: Neo4jRelationship[] = [
  { id: 101, start: 1, end: 2, type: 'CONTAINS', properties: {} },
  { id: 102, start: 2, end: 3, type: 'CONTAINS', properties: {} },
  { id: 103, start: 3, end: 4, type: 'CONTAINS', properties: {} },
  { id: 104, start: 4, end: 5, type: 'HAS_COLUMN', properties: {} },
  { id: 105, start: 4, end: 6, type: 'HAS_COLUMN', properties: {} }
]

const baseGraph = {
  nodes: baseNodes,
  relationships: baseRelationships
}

const searchGraph = {
  nodes: [baseNodes[3], baseNodes[4]],
  relationships: [{ id: 201, start: 4, end: 5, type: 'HAS_COLUMN', properties: {} }]
}

export const mockKnowledgeGraphRoutes = async (page: Page) => {
  await page.route('**/api/ai/knowledge/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/initial')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(baseGraph)
      })
      return
    }

    if (pathname.endsWith('/search')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(searchGraph)
      })
      return
    }

    if (request.method() === 'GET' || request.method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ nodes: [], relationships: [] })
      })
      return
    }

    await route.fulfill({ status: 204, body: '' })
  })
}
