import type { PermissionLevel } from './permissionConstants'
import type {
  PermissionResource,
  RoleDefinition,
  RoleItem,
  UserItem,
} from './permissionTypes'

export function flattenPermissionResources(
  resources: PermissionResource[],
): PermissionResource[] {
  return resources.flatMap((resource) => [
    resource,
    ...flattenPermissionResources(resource.children),
  ])
}

export function sortPermissionResources(
  resources: PermissionResource[],
): PermissionResource[] {
  return [...resources]
    .sort((left, right) => {
      if (left.sort !== right.sort) {
        return left.sort - right.sort
      }
      return left.objectId - right.objectId
    })
    .map((resource) => ({
      ...resource,
      children: sortPermissionResources(resource.children),
    }))
}

export function toRolePermissionAssignments(
  resources: PermissionResource[],
): Array<{ objectId: number; permissionCode: PermissionLevel }> {
  return flattenPermissionResources(resources).map((resource) => ({
    objectId: resource.objectId,
    permissionCode: resource.level,
  }))
}

export function updatePermissionTree(
  resources: PermissionResource[],
  objectId: number,
  level: PermissionLevel,
): { resources: PermissionResource[]; updated: boolean } {
  let updated = false

  const nextResources = resources.map((resource) => {
    const childUpdate = updatePermissionTree(resource.children, objectId, level)

    if (resource.objectId === objectId) {
      updated = true
      return {
        ...resource,
        level,
        children: childUpdate.resources,
      }
    }

    if (childUpdate.updated) {
      updated = true
      return {
        ...resource,
        children: childUpdate.resources,
      }
    }

    return resource
  })

  return {
    resources: updated ? nextResources : resources,
    updated,
  }
}

export function buildRoleItems(
  roles: RoleDefinition[],
  membersByRoleId: Record<string, UserItem[] | undefined>,
): RoleItem[] {
  return roles.map((role) => ({
    ...role,
    userCount: membersByRoleId[role.id]?.length ?? role.memberCount ?? 0,
  }))
}
