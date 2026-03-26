import config from '@/config'

const baseUrl = config.baseUrl

/**
 * 获取完整的头像URL
 * @param {string} avatarPath - 头像路径（可能是相对路径或完整URL）
 * @returns {string} 完整的头像URL
 */
export function getAvatarUrl(avatarPath) {
  if (!avatarPath) {
    return '/static/images/profile.jpg'
  }

  // 如果已经是完整URL，直接返回
  if (avatarPath.startsWith('http://') || avatarPath.startsWith('https://')) {
    return avatarPath
  }

  // 如果是相对路径，拼接 baseUrl
  if (avatarPath.startsWith('/')) {
    return baseUrl + avatarPath
  }

  // 其他情况，添加 / 后拼接
  return baseUrl + '/' + avatarPath
}

/**
 * 默认头像
 */
export const defaultAvatar = '/static/images/profile.jpg'
