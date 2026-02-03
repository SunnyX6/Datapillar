import type { Page } from '@playwright/test'

export const MOCK_CATALOG_NAME = 'demo_catalog'
export const MOCK_SCHEMA_NAME = 'analytics'
export const MOCK_TABLE_NAME = 'fact_orders'

const mockCatalogs = [
  {
    name: MOCK_CATALOG_NAME,
    type: 'hive',
    provider: 'hive',
    comment: '用于前端性能自测的 Catalog'
  }
]

const mockSchemas: Record<string, string[]> = {
  [MOCK_CATALOG_NAME]: [MOCK_SCHEMA_NAME]
}

const mockTables: Record<string, string[]> = {
  [`${MOCK_CATALOG_NAME}.${MOCK_SCHEMA_NAME}`]: [MOCK_TABLE_NAME, 'dim_users']
}

const buildResponse = (payload: Record<string, unknown>) => ({
  code: 0,
  ...payload
})

const buildIdentifiers = (names: string[]) => buildResponse({
  identifiers: names.map((name) => ({ name }))
})

const buildTableDetail = (tableName: string) =>
  buildResponse({
    table: {
      name: tableName,
      comment: '订单事实表（UI 自测）',
      columns: [
        { name: 'order_id', type: 'BIGINT', comment: '订单 ID' },
        { name: 'user_id', type: 'BIGINT', comment: '用户 ID' },
        { name: 'amount', type: 'DECIMAL(10,2)', comment: '订单金额' },
        { name: 'created_at', type: 'TIMESTAMP', comment: '创建时间' }
      ],
      properties: {
        'table-type': 'MANAGED',
        'input-format': 'parquet',
        numRows: '12000',
        engine: 'InnoDB'
      },
      audit: {
        creator: 'data-admin',
        lastModifiedTime: new Date().toISOString()
      }
    }
  })

const buildValueDomains = () =>
  buildResponse({
    valueDomains: [
      {
        domainCode: 'ORDER_STATUS',
        domainName: '订单状态',
        domainType: 'ENUM',
        domainLevel: 'L1',
        dataType: 'STRING',
        comment: '订单生命周期状态',
        items: [
          { code: 'PAID', name: '已支付', order: 1 },
          { code: 'CANCELLED', name: '已取消', order: 2 }
        ]
      }
    ],
    total: 1,
    offset: 0,
    limit: 100
  })

const mockMetrics = [
  {
    name: '订单数',
    code: 'order_count',
    type: 'ATOMIC',
    dataType: 'BIGINT',
    comment: '订单数量指标',
    currentVersion: 1,
    lastVersion: 1,
    audit: {
      creator: 'data-admin',
      createTime: '2024-01-01T00:00:00Z'
    }
  },
  {
    name: '成交金额',
    code: 'gmv',
    type: 'DERIVED',
    dataType: 'DECIMAL(10,2)',
    comment: '成交金额指标',
    currentVersion: 1,
    lastVersion: 1,
    audit: {
      creator: 'data-admin',
      createTime: '2024-01-01T00:00:00Z'
    }
  }
]

const mockWordRoots = [
  {
    code: 'ORDER',
    name: '订单',
    dataType: 'STRING',
    comment: '订单相关语义词根',
    audit: {
      creator: 'data-admin',
      createTime: '2024-01-01T00:00:00Z'
    }
  },
  {
    code: 'AMOUNT',
    name: '金额',
    dataType: 'DECIMAL',
    comment: '金额相关语义词根',
    audit: {
      creator: 'data-admin',
      createTime: '2024-01-01T00:00:00Z'
    }
  }
]

const getListParams = (url: URL) => {
  const offset = Number(url.searchParams.get('offset') ?? '0')
  const limit = Number(url.searchParams.get('limit') ?? '20')
  return {
    offset: Number.isNaN(offset) ? 0 : offset,
    limit: Number.isNaN(limit) ? 20 : limit
  }
}

const buildMetricList = (offset: number, limit: number) =>
  buildResponse({
    metrics: mockMetrics.slice(offset, offset + limit),
    total: mockMetrics.length,
    offset,
    limit
  })

const buildWordRootList = (offset: number, limit: number) =>
  buildResponse({
    roots: mockWordRoots.slice(offset, offset + limit),
    total: mockWordRoots.length,
    offset,
    limit
  })

export const mockOneMetaRoutes = async (page: Page) => {
  await page.route('**/api/onemeta/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/onemeta', '')

    if (request.method() !== 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildResponse({}))
      })
      return
    }

    if (path === '/metalakes/OneMeta/catalogs') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildResponse({ catalogs: mockCatalogs }))
      })
      return
    }

    if (path === '/metalakes/OneMeta/catalogs/OneDS/schemas/OneDS/metrics') {
      const { offset, limit } = getListParams(url)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildMetricList(offset, limit))
      })
      return
    }

    if (path === '/metalakes/OneMeta/catalogs/OneDS/schemas/OneDS/wordroots') {
      const { offset, limit } = getListParams(url)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildWordRootList(offset, limit))
      })
      return
    }

    const schemasMatch = path.match(/^\/metalakes\/OneMeta\/catalogs\/([^/]+)\/schemas$/)
    if (schemasMatch) {
      const catalogName = decodeURIComponent(schemasMatch[1])
      const schemas = mockSchemas[catalogName] ?? []
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildIdentifiers(schemas))
      })
      return
    }

    const tablesMatch = path.match(/^\/metalakes\/OneMeta\/catalogs\/([^/]+)\/schemas\/([^/]+)\/tables$/)
    if (tablesMatch) {
      const catalogName = decodeURIComponent(tablesMatch[1])
      const schemaName = decodeURIComponent(tablesMatch[2])
      const tables = mockTables[`${catalogName}.${schemaName}`] ?? []
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildIdentifiers(tables))
      })
      return
    }

    const tableDetailMatch = path.match(/^\/metalakes\/OneMeta\/catalogs\/([^/]+)\/schemas\/([^/]+)\/tables\/([^/]+)$/)
    if (tableDetailMatch) {
      const tableName = decodeURIComponent(tableDetailMatch[3])
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildTableDetail(tableName))
      })
      return
    }

    const objectTagsMatch = path.match(/^\/metalakes\/OneMeta\/objects\/(CATALOG|SCHEMA|TABLE|COLUMN)\/.+\/tags$/)
    if (objectTagsMatch) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildResponse({ names: [] }))
      })
      return
    }

    if (path === '/metalakes/OneMeta/tags') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildResponse({ tags: [] }))
      })
      return
    }

    if (path === '/metalakes/OneMeta/catalogs/OneDS/schemas/OneDS/valuedomains') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildValueDomains())
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildResponse({}))
    })
  })
}
