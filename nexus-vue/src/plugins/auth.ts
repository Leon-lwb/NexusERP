import useUserStore from '@/store/modules/user'

function authPermission(permission: string): boolean {
  const all_permission = "*:*:*"
  const permissions = useUserStore().permissions
  if (permission && permission.length > 0) {
    return permissions.some((v: string) => {
      return all_permission === v || v === permission
    })
  } else {
    return false
  }
}

function authRole(role: string): boolean {
  const super_admin = "admin"
  const roles = useUserStore().roles
  if (role && role.length > 0) {
    return roles.some((v: string) => {
      return super_admin === v || v === role
    })
  } else {
    return false
  }
}

export default {
  hasPermi(permission: string): boolean {
    return authPermission(permission)
  },
  hasPermiOr(permissions: string[]): boolean {
    return permissions.some((item: string) => {
      return authPermission(item)
    })
  },
  hasPermiAnd(permissions: string[]): boolean {
    return permissions.every((item: string) => {
      return authPermission(item)
    })
  },
  hasRole(role: string): boolean {
    return authRole(role)
  },
  hasRoleOr(roles: string[]): boolean {
    return roles.some((item: string) => {
      return authRole(item)
    })
  },
  hasRoleAnd(roles: string[]): boolean {
    return roles.every((item: string) => {
      return authRole(item)
    })
  }
}
