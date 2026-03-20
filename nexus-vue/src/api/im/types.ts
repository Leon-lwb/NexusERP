export interface ImMessage {
  messageId: string
  fromUserId: number
  toUserId?: number
  groupId?: string
  content: string
  messageType: string
  status: string
  createTime: string
  isRead: number
  extra?: string
  source: string
  isRevoked: number
  revokeTime?: string
  senderName?: string
  senderAvatar?: string
}

export interface ImConversation {
  id: string
  name: string
  avatar: string
  lastMessage: string
  lastTime: string
  unreadCount: number
  type: 'private' | 'group'
  userId?: number
  groupId?: string
  memberCount?: number
  status?: string
}

export interface ImGroup {
  groupId: string
  groupName: string
  avatar: string
  description: string
  ownerId: number
  groupType: string
  maxMembers: number
  memberCount: number
  status: string
  allowInvite: number
  allowMemberModify: number
  allowMemberInvite: number
  createTime: string
  updateTime: string
}

export interface ImGroupMember {
  userId: number
  userName: string
  nickName: string
  avatar: string
  role: 'owner' | 'admin' | 'member'
  groupNickname: string
  joinTime: string
  isMuted: number
  enabled: number
}

export interface ImUserInfo {
  userId: number
  username: string
  nickname: string
  avatar: string
  email: string
  phone: string
  imStatus: string
  imLastOnlineTime: string
  imSignature: string
  imGender: string
  imRegion: string
}

export interface SendMessageParams {
  toUserId?: number
  groupId?: string
  content: string
  messageType?: string
}

export interface CreateGroupParams {
  groupName: string
  description?: string
  avatar?: string
  memberIds?: number[]
}
