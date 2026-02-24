import { describe, expect, it } from 'vitest'
import { canAccessRouteByMenus, resolveFirstAccessibleRoute } from '@/router/access/routeAccess'
import type { Menu } from '@/types/auth'

describe('routeAccess', () => {
  it('does not allow profile permission route when only profile menu is granted', () => {
    const menus: Menu[] = [
      {
        id: 1,
        name: '个人中心',
        path: '/profile',
        location: 'PROFILE',
        permissionCode: 'READ',
      },
    ]

    expect(canAccessRouteByMenus(menus, '/profile')).toBe(true)
    expect(canAccessRouteByMenus(menus, '/profile/permission')).toBe(false)
  })

  it('supports nested governance metadata routes by metadata menu permission', () => {
    const menus: Menu[] = [
      {
        id: 1,
        name: '数据治理',
        path: '/governance',
        location: 'TOP',
        permissionCode: 'READ',
        children: [
          {
            id: 2,
            name: '元数据',
            path: '/governance/metadata',
            location: 'TOP',
            permissionCode: 'READ',
          },
        ],
      },
    ]

    expect(canAccessRouteByMenus(menus, '/governance/metadata')).toBe(true)
    expect(
      canAccessRouteByMenus(
        menus,
        '/governance/metadata/catalogs/default/schemas/public',
      ),
    ).toBe(true)
  })

  it('selects first accessible route by fixed priority and skips disabled menus', () => {
    const menus: Menu[] = [
      {
        id: 1,
        name: '数据驾驶舱',
        path: '/home',
        location: 'TOP',
        permissionCode: 'DISABLE',
      },
      {
        id: 2,
        name: '项目',
        path: '/projects',
        location: 'TOP',
        permissionCode: 'READ',
      },
      {
        id: 3,
        name: '个人中心',
        path: '/profile',
        location: 'PROFILE',
        permissionCode: 'READ',
      },
    ]

    expect(resolveFirstAccessibleRoute(menus)).toBe('/projects')
  })
})
