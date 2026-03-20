<template>
  <el-dialog
    v-model="visible"
    :width="dialogWidth"
    :height="dialogHeight"
    :top="dialogTop"
    :modal="false"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    :show-close="false"
    :draggable="true"
    class="im-chat-dialog"
    @close="handleClose"
  >
    <!-- 自定义标题栏 -->
    <template #header>
      <div class="chat-header">
        <div class="header-title">即时通讯</div>
        <div class="header-actions">
          <el-button type="text" @click="toggleMinimize" class="action-btn" v-if="!isMinimized">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button type="text" @click="handleClose" class="action-btn">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </template>

    <div class="im-container" :class="{ 'minimized': isMinimized }">
      <!-- 左侧导航栏 -->
      <div class="nav-sidebar" v-if="!isMinimized">
        <!-- 用户信息 -->
        <div class="user-section" @click="showUserProfile">
          <el-avatar :src="currentUser?.avatar" :size="40" class="user-avatar">
            {{ currentUser?.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="user-info">
            <div class="username">{{ currentUser?.nickname || '用户' }}</div>
            <div class="user-status">在线</div>
          </div>
        </div>


        <!-- 导航菜单 -->
        <div class="nav-menu">
          <div class="nav-item" :class="{ active: currentNav === 'chat' }" @click="switchNav('chat')">
            <el-icon><ChatDotRound /></el-icon>
            <span>聊天</span>
          </div>
          <div class="nav-item" :class="{ active: currentNav === 'contacts' }" @click="switchNav('contacts')">
            <el-icon><User /></el-icon>
            <span>联系人</span>
          </div>
          <div class="nav-item" :class="{ active: currentNav === 'groups' }" @click="switchNav('groups')">
            <el-icon><UserFilled /></el-icon>
            <span>群聊</span>
          </div>
          <div class="nav-item" :class="{ active: currentNav === 'settings' }" @click="switchNav('settings')">
            <el-icon><Setting /></el-icon>
            <span>设置</span>
          </div>
        </div>
      </div>

      <!-- 中间内容区域 -->
      <div class="middle-content" v-if="!isMinimized">
        <!-- 搜索框 -->
        <div class="search-section">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索"
            clearable
            class="search-input"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 聊天会话列表 -->
        <div v-if="currentNav === 'chat'" class="conversation-list">
          <div
            v-for="conversation in filteredConversations"
            :key="conversation.id"
            class="conversation-item"
            :class="{ 'active': conversation.id === currentConversationId }"
            @click="selectConversation(conversation)"
          >
            <div class="conversation-avatar-wrapper">
              <el-avatar :src="conversation.avatar" :size="40" class="conversation-avatar">
                {{ conversation.name?.charAt(0) }}
              </el-avatar>
              <!-- 未读消息数量提示 -->
              <div v-if="conversation.unreadCount > 0" class="unread-badge">
                {{ conversation.unreadCount > 99 ? '99+' : conversation.unreadCount }}
              </div>
            </div>
            <div class="conversation-info">
              <div class="conversation-name">{{ conversation.name }}</div>
              <div class="conversation-preview">{{ conversation.lastMessage }}</div>
            </div>
            <div class="conversation-meta">
              <div class="conversation-time">{{ formatTime(conversation.lastTime) }}</div>
            </div>
          </div>
        </div>

         <!-- 联系人列表 -->
         <div v-if="currentNav === 'contacts'" class="contact-list">
           <div class="contact-section">
             <div class="section-title">联系人</div>
            <div
              v-for="contact in filteredContacts"
              :key="contact.userId"
              class="contact-item"
              @click="startChatWithContact(contact)"
            >
              <el-avatar :src="contact.avatar" :size="40" class="contact-avatar">
                {{ contact.nickname?.charAt(0) }}
              </el-avatar>
              <div class="contact-info">
                <div class="contact-name">{{ contact.nickname }}</div>
                <div class="contact-status" :class="contact.status === 'online' ? 'online' : 'offline'">
                  {{ contact.status === 'online' ? '在线' : '离线' }}
                </div>
              </div>
              <div class="contact-actions">
                <el-button type="text" @click.stop="startChatWithContact(contact)">
                  <el-icon><ChatDotRound /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 群聊列表 -->
        <div v-if="currentNav === 'groups'" class="group-list">
          <div class="group-section">
            <div class="section-title">
              我的群聊
              <div class="group-actions">
                <el-button type="primary" size="small" @click="showCreateGroupDialog = true">
                  <el-icon><Plus /></el-icon>
                  创建群聊
                </el-button>
                <el-button type="success" size="small" @click="openAddMembersDialog" v-if="currentConversation?.type === 'group'">
                  <el-icon><UserFilled /></el-icon>
                  添加人员
                </el-button>
              </div>
            </div>
            <div
              v-for="group in filteredGroups"
              :key="group.groupId"
              class="group-item"
              @click="selectGroup(group)"
            >
              <el-avatar :src="group.avatar" :size="40" class="group-avatar">
                {{ group.groupName?.charAt(0) }}
              </el-avatar>
              <div class="group-info">
                <div class="group-name">{{ group.groupName }}</div>
                <div class="group-members">{{ group.memberCount }}人</div>
              </div>
              <div class="group-actions">
                <el-button type="text" @click.stop="openGroupSettings(group)">
                  <el-icon><Setting /></el-icon>
                </el-button>
                <el-button type="text" @click.stop="selectGroup(group)">
                  <el-icon><ChatDotRound /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 设置页面 -->
        <div v-if="currentNav === 'settings'" class="settings-list">
          <div class="settings-section">
            <div class="section-title">设置</div>
            <div class="setting-item">
              <el-icon><Bell /></el-icon>
              <span>消息通知</span>
              <el-switch v-model="settings.notifications" />
            </div>
            <div class="setting-item">
              <el-icon><Lock /></el-icon>
              <span>隐私设置</span>
              <el-switch v-model="settings.privacy" />
            </div>
            <div class="setting-item">
              <el-icon><Moon /></el-icon>
              <span>深色模式</span>
              <el-switch v-model="settings.darkMode" />
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-area" v-if="!isMinimized">
        <!-- 聊天头部 -->
        <div class="chat-header-area" v-if="currentConversation">
          <div class="chat-user-info">
            <el-avatar :src="currentConversation.avatar" :size="40">
              {{ currentConversation.name?.charAt(0) }}
            </el-avatar>
            <div class="chat-user-details">
              <div class="chat-username">
                {{ currentConversation.name }}
                <span v-if="currentConversation?.type === 'group'" class="member-count">
                  ({{ currentConversation.members?.length || currentConversation.memberCount || 0 }}人)
                </span>
              </div>
              <div v-if="currentConversation?.type === 'private'" class="chat-status" :class="currentConversation.status === 'online' ? 'online' : 'offline'">
                {{ currentConversation.status === 'online' ? '在线' : '离线' }}
              </div>
            </div>
          </div>
          
          <div class="chat-actions">
            <el-button type="text" @click="startVoiceCall">
              <el-icon><Phone /></el-icon>
            </el-button>
            <el-button type="text" @click="startVideoCall">
              <el-icon><VideoCamera /></el-icon>
            </el-button>
            <el-button type="text" @click="handleMenuClick">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="message-area" ref="messageAreaRef">
          <div
            v-for="message in currentMessages"
            :key="message.id"
            class="message-item"
            :class="{ 'own': message.fromUserId === currentUserId }"
          >
            <div class="message-avatar" v-if="message.fromUserId !== currentUserId">
              <el-avatar :src="message.avatar" :size="36">
                {{ message.nickname?.charAt(0) }}
              </el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble" :class="{ 'own': message.fromUserId === currentUserId }">
                <div v-if="message.messageType === 'text'" class="text-message">
                  {{ message.content }}
                </div>
                <div v-else-if="message.messageType === 'image'" class="image-message">
                  <el-image
                    :src="message.content"
                    :preview-src-list="[message.content]"
                    fit="cover"
                    class="message-image"
                  />
                </div>
                <div v-else-if="message.messageType === 'file'" class="file-message">
                  <div class="file-icon">
                    <el-icon><Document /></el-icon>
                  </div>
                  <div class="file-info">
                    <div class="file-name">{{ message.fileName || '文件' }}</div>
                    <div class="file-size">{{ message.fileSize || '' }}</div>
                  </div>
                </div>
                <div v-else class="other-message">
                  {{ message.content }}
                </div>
              </div>
              <div class="message-time">{{ formatTime(message.createTime) }}</div>
            </div>
            <div class="message-avatar" v-if="message.fromUserId === currentUserId">
              <el-avatar :src="currentUser?.avatar" :size="36">
                {{ currentUser?.nickname?.charAt(0) }}
              </el-avatar>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area" v-if="currentConversation">
          <!-- 工具栏 -->
          <div class="input-toolbar">
            <el-button type="text" @click="showEmojiPicker = !showEmojiPicker" class="tool-btn">
              <el-icon><ChatDotRound /></el-icon>
            </el-button>
            <el-button type="text" @click="selectImage" class="tool-btn">
              <el-icon><Picture /></el-icon>
            </el-button>
            <el-button type="text" @click="selectFile" class="tool-btn">
              <el-icon><Paperclip /></el-icon>
            </el-button>
            <el-button type="text" @click="startVoiceRecord" class="tool-btn">
              <el-icon><Microphone /></el-icon>
            </el-button>
          </div>
          
          <!-- 输入框 -->
          <div class="input-box">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息..."
              @keydown.enter.prevent="sendMessage"
              class="message-input"
              resize="none"
            />
            <el-button
              type="primary"
              @click="sendMessage"
              :disabled="!inputMessage.trim()"
              class="send-btn"
            >
              发送
            </el-button>
          </div>
        </div>

        <!-- 空状态 -->
        <div class="empty-state" v-if="!currentConversation">
          <div class="empty-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="empty-text">选择一个会话开始聊天</div>
        </div>
      </div>

      <!-- 最小化状态 -->
      <div class="minimized-content" v-if="isMinimized" @click="restoreFromMinimize">
        <div class="minimized-info">
          <el-avatar :src="currentConversation?.avatar" :size="32">
            {{ currentConversation?.name?.charAt(0) }}
          </el-avatar>
          <span class="minimized-name">{{ currentConversation?.name || '即时通讯' }}</span>
          <el-badge :value="totalUnreadCount" v-if="totalUnreadCount > 0" class="unread-badge" />
        </div>
      </div>
    </div>
  </el-dialog>

  <!-- 表情选择器 -->
  <el-dialog
    v-model="showEmojiPicker"
    title="选择表情"
    width="300px"
    :modal="false"
    class="emoji-dialog"
  >
    <div class="emoji-picker">
      <div class="emoji-grid">
        <span
          v-for="emoji in emojiList"
          :key="emoji"
          class="emoji-item"
          @click="insertEmoji(emoji)"
        >
          {{ emoji }}
        </span>
      </div>
    </div>
  </el-dialog>

  <!-- 创建群聊弹窗 -->
  <CreateGroupDialog
    v-model:visible="showCreateGroupDialog"
    ref="createGroupDialogRef"
    @success="handleGroupCreated"
    @close="showCreateGroupDialog = false"
  />

  <!-- 群聊设置弹窗 -->
  <GroupSettingsDialog
    ref="groupSettingsDialogRef"
    @success="handleGroupSettingsSuccess"
    @close="showGroupSettingsDialog = false"
  />

  <!-- 添加群成员对话框 -->
  <AddGroupMembersDialog
    v-model:visible="showAddMembersDialog"
    :group-info="currentConversation"
    :current-user-id="currentUser?.userId"
    @success="handleAddMembersSuccess"
    @close="handleAddMembersClose"
  />

  <!-- 群聊设置对话框 -->
  <el-dialog
    v-model="showGroupSettingsDialog"
    title="群聊设置"
    width="400px"
    :close-on-click-modal="false"
    class="group-settings-dialog"
    :show-close="true"
    :close-on-press-escape="true"
  >
    <div class="group-settings-content">
      <!-- 群成员 -->
      <div class="settings-section">
        <div class="section-header">
          <span class="section-title">群成员</span>
          <span class="member-count">查看全部{{ currentConversation?.members?.length || currentConversation?.memberCount || 0 }}名成员</span>
        </div>
        <div class="member-avatars">
          <div 
            v-for="member in (currentConversation?.members || [])" 
            :key="member.userId"
            class="member-avatar-item"
            :class="{ 
              'selected': isMemberSelected(member.userId),
              'current-user': member.userId == currentUser?.userId,
              'removable': member.userId != currentUser?.userId && member.role !== 'owner'
            }"
            @click="toggleMemberSelection(member)"
          >
            <el-avatar :src="member.avatar" :size="40">
              {{ member.nickName?.charAt(0) || member.userName?.charAt(0) }}
            </el-avatar>
            <div class="member-name">{{ member.nickName || member.userName }}</div>
            <div v-if="member.role === 'owner'" class="member-role">群主</div>
            <div v-if="isMemberSelected(member.userId)" class="member-selected">✓</div>
          </div>
          <!-- 如果没有成员数据，显示占位符 -->
          <div v-if="!currentConversation?.members || currentConversation.members.length === 0" class="no-members">
            <div class="no-members-text">暂无成员数据</div>
          </div>
        </div>
        
        <!-- 移除成员操作区域 -->
        <div v-if="selectedMembers.length > 0" class="remove-members-actions">
          <div class="selected-count">已选择 {{ selectedMembers.length }} 名成员</div>
          <el-button type="danger" size="small" @click="confirmRemoveMembers">
            移除选中成员
          </el-button>
          <el-button size="small" @click="clearMemberSelection">
            取消选择
          </el-button>
        </div>
        <div class="member-actions">
          <div class="action-item" @click="inviteMembers">
            <div class="action-icon invite">+</div>
            <span>邀请</span>
          </div>
          <div class="action-item" :class="{ 'active': isRemovingMembers }" @click="removeMembers">
            <div class="action-icon remove">-</div>
            <span>{{ isRemovingMembers ? '取消移除' : '移除' }}</span>
          </div>
          <div class="action-item" @click="muteMember">
            <div class="action-icon mute">🚫</div>
            <span>禁言</span>
          </div>
          <div class="action-item" @click="unmuteMember">
            <div class="action-icon unmute">⊖</div>
            <span>解除禁言</span>
          </div>
        </div>
      </div>

      <!-- 聊天设置 -->
      <div class="settings-section">
        <div class="setting-item">
          <span>消息免打扰</span>
          <el-switch v-model="groupSettings.doNotDisturb" />
        </div>
        <div class="setting-item">
          <span>置顶聊天</span>
          <el-switch v-model="groupSettings.pinToTop" />
        </div>
        <div class="setting-item">
          <span>全员禁言</span>
          <el-switch v-model="groupSettings.muteAll" />
        </div>
        <div class="setting-item">
          <span>允许普通成员邀请好友</span>
          <el-switch v-model="groupSettings.allowInvite" />
        </div>
        <div class="setting-item">
          <span>允许普通成员分享名片</span>
          <el-switch v-model="groupSettings.allowShareCard" />
        </div>
      </div>

      <!-- 群信息 -->
      <div class="settings-section">
        <div class="setting-item">
          <span>群聊名称</span>
          <span class="setting-value">{{ currentConversation?.name || '未设置' }}</span>
        </div>
        <div class="setting-item">
          <span>备注名</span>
          <span class="setting-value placeholder">点击设置备注名</span>
        </div>
        <div class="setting-item">
          <span>我在本群的昵称</span>
          <span class="setting-value placeholder">点击设置昵称</span>
        </div>
      </div>

      <!-- 群公告 -->
      <div class="settings-section">
        <div class="section-title">群公告</div>
        <el-input
          v-model="groupSettings.announcement"
          type="textarea"
          placeholder="点击设置群公告"
          :rows="3"
          class="announcement-input"
        />
      </div>

      <!-- 底部操作 -->
      <div class="settings-actions">
        <el-button type="danger" @click="clearChatHistory" class="clear-button">
          清空聊天记录
        </el-button>
        <el-button type="text" @click="reportGroup" class="report-button">
          被骚扰了?举报该群
        </el-button>
        <el-button @click="showGroupSettingsDialog = false" class="close-button">
          关闭
        </el-button>
      </div>
    </div>
  </el-dialog>

  <!-- 个人信息弹窗 -->
  <el-dialog
    v-model="showUserProfileDialog"
    title="个人信息"
    width="500px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
  >
    <div class="user-profile-content">
      <div class="profile-header">
        <el-avatar :size="80" :src="currentUser?.avatar" class="profile-avatar">
          {{ currentUser?.nickname?.charAt(0) || 'U' }}
        </el-avatar>
        <div class="profile-info">
          <h3 class="profile-name">{{ currentUser?.nickname || '用户' }}</h3>
          <p class="profile-username">@{{ currentUser?.username || 'username' }}</p>
          <div class="profile-status">
            <el-tag type="success" size="small">在线</el-tag>
          </div>
        </div>
      </div>
      
      <div class="profile-details">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户ID">
            {{ currentUser?.userId || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="昵称">
            {{ currentUser?.nickname || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户名">
            {{ currentUser?.username || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ currentUser?.email || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">
            {{ currentUser?.phone || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ currentUser?.createTime || 'N/A' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="showUserProfileDialog = false">关闭</el-button>
        <el-button type="primary" @click="editProfile">编辑资料</el-button>
      </div>
    </template>
  </el-dialog>

</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { 
  Minus, Close, ChatDotRound, Picture, Paperclip, Microphone, 
  Search, Document, User, UserFilled, Setting, Phone, VideoCamera, MoreFilled,
  Bell, Lock, ArrowRight, Plus
} from '@element-plus/icons-vue'
import { useImStore } from '@/store/modules/im'
import useUserStore from '@/store/modules/user'
import { getUserListForIm, getImUserList, getGroups } from '@/api/im'
import CreateGroupDialog from '@/components/CreateGroupDialog/index.vue'
import GroupSettingsDialog from '@/components/GroupSettingsDialog/index.vue'
import AddGroupMembersDialog from '@/components/AddGroupMembersDialog/index.vue'

const imStore = useImStore()
const userStore = useUserStore()

// 类型定义
interface Conversation {
  id: string | number
  name: string
  avatar?: string
  lastMessage?: string
  lastTime?: Date
  unreadCount?: number
  status?: string
  type: 'private' | 'group'
  userId?: string | number
  groupId?: string
  members?: Array<{
    userId: string | number
    username: string
    nickname?: string
    avatar?: string
  }>
}

interface Message {
  id: string | number
  fromUserId: string | number
  toUserId?: string | number
  content: string
  messageType: string
  createTime: Date
  nickname?: string
  avatar?: string
  status?: string
  fileName?: string
  fileSize?: string
}

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'close', 'minimize'])

// 弹窗状态
const visible = computed({
  get: () => props.visible || props.modelValue,
  set: (val) => {
    if (props.visible !== undefined) {
      // 如果外部传入了 visible prop，则通过事件通知父组件
      emit('update:modelValue', val)
    } else {
      emit('update:modelValue', val)
    }
  }
})

// 弹窗尺寸和位置
const dialogWidth = ref('800px')
const dialogHeight = ref('600px')
const dialogTop = ref('5vh')
const isMinimized = ref(false)

// 聊天相关
const currentUserId = computed(() => {
  return userStore.id
})
const currentUser = computed(() => {
  return {
    userId: userStore.id,
    nickname: userStore.nickName || '用户',
    avatar: userStore.avatar || '',
    username: userStore.name || '',
    email: '', // 用户存储中没有邮箱信息
    phone: '', // 用户存储中没有手机号信息
    createTime: '' // 用户存储中没有创建时间信息
  }
})
const currentConversationId = ref<string | number | null>(null)
const currentConversation = ref<Conversation | null>(null)
const currentMessages = ref<Message[]>([])
const inputMessage = ref('')
const messageAreaRef = ref(null)
const totalUnreadCount = ref(0)
const receivedMessageKeys = new Set()

// 导航状态
const currentNav = ref('chat')

// 群聊设置相关
const groupSettings = ref({
  doNotDisturb: false,
  pinToTop: false,
  muteAll: false,
  allowInvite: true,
  allowShareCard: true,
  announcement: ''
})

// 移除成员相关
const selectedMembers = ref([])
const isRemovingMembers = ref(false)

// 会话列表
const conversations = ref<Conversation[]>([])
const searchKeyword = ref('')

// 联系人列表
const contacts = ref([])
const filteredContacts = computed(() => {
  if (!searchKeyword.value) return contacts.value
  return contacts.value.filter(contact => 
    contact.nickname?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 群聊列表
const groups = ref([])
const filteredGroups = computed(() => {
  if (!searchKeyword.value) return groups.value
  return groups.value.filter(group => 
    group.groupName?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 群聊相关状态
const showCreateGroupDialog = ref(false)
const showGroupSettingsDialog = ref(false)
const groupSettingsDialogRef = ref()

// 添加人员相关状态
const showAddMembersDialog = ref(false)

// 个人信息相关状态
const showUserProfileDialog = ref(false)


// 设置
const settings = ref({
  notifications: true
})

// 其他状态
const showEmojiPicker = ref(false)

// 表情列表
const emojiList = ref([
  '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣',
  '😊', '😇', '🙂', '🙃', '😉', '😌', '😍', '🥰',
  '😘', '😗', '😙', '😚', '😋', '😛', '😝', '😜',
  '🤪', '🤨', '🧐', '🤓', '😎', '🤩', '🥳', '😏',
  '😒', '😞', '😔', '😟', '😕', '🙁', '☹️', '😣',
  '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠',
  '😡', '🤬', '🤯', '😳', '🥵', '🥶', '😱', '😨',
  '😰', '😥', '😓', '🤗', '🤔', '🤭', '🤫', '🤥',
  '😶', '😐', '😑', '😬', '🙄', '😯', '😦', '😧',
  '😮', '😲', '🥱', '😴', '🤤', '😪', '😵', '🤐',
  '🥴', '🤢', '🤮', '🤧', '😷', '🤒', '🤕', '🤑'
])

// 计算属性
const filteredConversations = computed(() => {
  if (!searchKeyword.value) return conversations.value
  return conversations.value.filter(conv => 
    conv.name?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 方法
const handleClose = () => {
  visible.value = false
  isMinimized.value = false
  // 触发关闭事件
  emit('close')
}

const toggleMinimize = () => {
  isMinimized.value = !isMinimized.value
  if (isMinimized.value) {
    dialogWidth.value = '300px'
    dialogHeight.value = '60px'
    // 触发最小化事件
    emit('minimize')
  } else {
    dialogWidth.value = '800px'
    dialogHeight.value = '600px'
  }
}

// 从最小化状态恢复
const restoreFromMinimize = () => {
  isMinimized.value = false
  dialogWidth.value = '800px'
  dialogHeight.value = '600px'
}

const switchNav = (nav) => {
  currentNav.value = nav
  // 切换到聊天时，如果有选中的会话，保持选中状态
  if (nav === 'chat' && currentConversation.value) {
    // 保持当前会话选中
  } else if (nav !== 'chat') {
    // 切换到其他导航时，清空当前会话和消息
    currentConversationId.value = null
    currentConversation.value = null
    currentMessages.value = []
  }
}

const selectConversation = async (conversation: Conversation) => {
  console.log('切换会话:', conversation)
  currentConversationId.value = conversation.id
  currentConversation.value = conversation
  
  // 为会话添加成员信息（仅群聊需要）
  if (conversation.type === 'group') {
    // 群聊：从群聊信息中获取成员列表
    currentConversation.value.members = conversation.members || []
  } else {
    // 私聊：不设置members，避免显示成员列表
    currentConversation.value.members = []
  }
  
  // 标记消息为已读
  await markConversationAsRead(conversation)
  
  // 立即清空消息列表，避免显示上一个会话的消息
  currentMessages.value = []
  loadMessages(conversation.id)
}

const markConversationAsRead = async (conversation: Conversation) => {
  try {
    const { markMessagesAsRead } = await import('@/api/im')
    
    if (conversation.type === 'private') {
      await markMessagesAsRead(conversation.userId, undefined)
    } else if (conversation.type === 'group') {
      await markMessagesAsRead(undefined, conversation.groupId)
    }
    
    // 更新会话列表中的未读数量
    conversation.unreadCount = 0
    
    console.log('消息已标记为已读:', conversation.name)
  } catch (error) {
    console.error('标记消息已读失败:', error)
  }
}

const startChatWithContact = (contact) => {
  // 创建或找到与联系人的会话
  let conversation = conversations.value.find(conv => 
    conv.type === 'private' && conv.userId === contact.userId
  )
  
  if (!conversation) {
    // 创建新会话
    conversation = {
      id: contact.userId, // 使用用户ID作为会话ID，这样后端查询时能正确匹配
      name: contact.nickname,
      avatar: contact.avatar,
      lastMessage: '',
      lastTime: new Date(),
      unreadCount: 0,
      status: contact.status,
      type: 'private',
      userId: contact.userId
    }
    conversations.value.unshift(conversation)
  }
  
  // 选择会话并切换到聊天
  selectConversation(conversation)
  currentNav.value = 'chat'
}

const selectGroup = (group) => {
  // 创建或找到群聊会话
  let conversation = conversations.value.find(conv => 
    conv.type === 'group' && conv.groupId === group.groupId
  )
  
  if (!conversation) {
    // 创建新会话
    conversation = {
      id: group.groupId, // 使用群ID作为会话ID
      name: group.groupName,
      avatar: group.avatar,
      lastMessage: '',
      lastTime: new Date(),
      unreadCount: 0,
      status: 'online',
      type: 'group',
      groupId: group.groupId,
      memberCount: group.memberCount || 0,
      members: group.members || [] // 添加成员数组
    }
    conversations.value.unshift(conversation)
  } else {
    // 更新现有会话的成员信息
    conversation.memberCount = group.memberCount || conversation.memberCount || 0
    conversation.members = group.members || conversation.members || []
  }
  
  // 选择会话并切换到聊天
  selectConversation(conversation)
  currentNav.value = 'chat'
}

// 打开群聊设置
const openGroupSettings = (group) => {
  if (groupSettingsDialogRef.value) {
    groupSettingsDialogRef.value.open(group.groupId)
  }
}

// 处理群聊创建成功
const handleGroupCreated = (groupData) => {
  console.log('群聊创建成功:', groupData)
  // 重新加载群聊列表
  loadGroupList()
  // 自动选择新创建的群聊
  selectGroup(groupData)
}

// 处理群聊设置成功
const handleGroupSettingsSuccess = () => {
  console.log('群聊设置更新成功')
  // 重新加载群聊列表
  loadGroupList()
}


const loadMessages = async (conversationId) => {
  try {
    console.log('开始加载消息历史，会话ID:', conversationId)
    
    // 清空当前消息列表，避免不同用户的消息混在一起
    currentMessages.value = []
    
    // 调用后端API获取消息历史
    const { getMessageHistory } = await import('@/api/im')
    const response = await getMessageHistory({
      targetId: conversationId,
      pageNum: 1,
      pageSize: 50
    })
    
    if (response.code === 200 && response.data) {
      const messages = response.data.records || response.data.list || []
      console.log('加载到消息历史:', messages.length, '条')
      
      // 转换消息格式
      const dbMessages = messages.map(msg => ({
        id: msg.messageId || msg.id,
        fromUserId: msg.fromUserId,
        toUserId: msg.toUserId,
        content: msg.content,
        messageType: msg.messageType || 'text',
        createTime: new Date(msg.createTime),
        nickname: msg.senderName || msg.nickname || '未知用户',
        avatar: msg.senderAvatar || msg.avatar || '',
        status: 'received'
      }))
      
      // 直接设置消息列表，不合并
      currentMessages.value = dbMessages
      
      // 按时间排序
      currentMessages.value.sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
      
      console.log('加载后的消息列表:', currentMessages.value.length, '条')
      
      // 滚动到底部
      await nextTick()
      scrollToBottom()
    } else {
      console.log('没有消息历史或加载失败:', response.msg)
      // 保持空列表
      currentMessages.value = []
    }
  } catch (error) {
    console.error('加载消息历史失败:', error)
    currentMessages.value = []
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || !currentConversation.value) return

  const messageContent = inputMessage.value.trim()
  inputMessage.value = ''

  console.log('ChatDialog发送消息 - 当前会话:', currentConversation.value)
  console.log('ChatDialog发送消息 - 会话类型:', currentConversation.value.type)
  console.log('ChatDialog发送消息 - 消息内容:', messageContent)

  // 创建消息对象
  const message = {
    id: Date.now(),
    fromUserId: currentUserId.value,
    toUserId: currentConversation.value.type === 'private' ? currentConversation.value.userId : undefined,
    groupId: currentConversation.value.type === 'group' ? currentConversation.value.id : undefined,
    content: messageContent,
    messageType: 'text',
    createTime: new Date(),
    nickname: currentUser.value.nickname,
    avatar: currentUser.value.avatar,
    status: 'sending' // 先显示为发送中
  }

  // 立即添加到消息列表（乐观更新）
  currentMessages.value.push(message)
  
  // 滚动到底部
  await nextTick()
  scrollToBottom()

  try {
    // 根据会话类型调用不同的API
    const { sendMessage: sendMessageApi, sendGroupMessage: sendGroupMessageApi } = await import('@/api/im')
    let response
    
    if (currentConversation.value.type === 'private') {
      console.log('ChatDialog发送私聊消息 - 参数:', {
        toUserId: currentConversation.value.userId,
        content: messageContent,
        messageType: 'text'
      })
      response = await sendMessageApi({
        toUserId: currentConversation.value.userId,
        content: messageContent,
        messageType: 'text'
      })
    } else if (currentConversation.value.type === 'group') {
      console.log('ChatDialog发送群聊消息 - 参数:', {
        groupId: currentConversation.value.id,
        content: messageContent,
        messageType: 'text'
      })
      response = await sendGroupMessageApi({
        groupId: currentConversation.value.id,
        content: messageContent,
        messageType: 'text'
      })
    } else {
      throw new Error('未知的会话类型: ' + currentConversation.value.type)
    }

    console.log('ChatDialog发送消息 - API响应:', response)

    if (response.code === 200) {
      // 发送成功，更新消息状态和ID
      message.status = 'sent'
      // 如果后端返回了messageId，更新前端消息的ID，避免重复
      if (response.data && response.data.messageId) {
        message.id = response.data.messageId
      }
      ElMessage.success('消息发送成功')
    } else {
      // 发送失败，更新消息状态
      message.status = 'failed'
      ElMessage.error('消息发送失败: ' + response.msg)
    }
  } catch (error) {
    // 发送失败，更新消息状态
    message.status = 'failed'
    console.error('发送消息失败:', error)
    ElMessage.error('消息发送失败: ' + error.message)
  }
}

const scrollToBottom = () => {
  if (messageAreaRef.value) {
    messageAreaRef.value.scrollTop = messageAreaRef.value.scrollHeight
  }
}

const formatTime = (time) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN')
}

const selectImage = () => {
  ElMessage.info('图片选择功能待实现')
}

const selectFile = () => {
  ElMessage.info('文件选择功能待实现')
}

const startVoiceRecord = () => {
  ElMessage.info('语音录制功能待实现')
}

const startVoiceCall = () => {
  ElMessage.info('语音通话功能待实现')
}

const startVideoCall = () => {
  ElMessage.info('视频通话功能待实现')
}

// 这个函数现在不需要了，因为使用下拉菜单

// 处理菜单点击
const handleMenuClick = async () => {
  if (currentConversation.value?.type === 'group') {
    // 群聊：直接打开群聊设置
    await openGroupSettingsDialog()
  } else if (currentConversation.value?.type === 'private') {
    // 私聊：显示用户信息
    showUserInfoMenu()
  }
}

// 打开群聊设置对话框
const openGroupSettingsDialog = async () => {
  if (currentConversation.value?.type === 'group') {
    console.log('打开群聊设置，当前会话:', currentConversation.value)
    // 加载群成员信息
    const members = await loadGroupMembers(currentConversation.value.groupId)
    console.log('加载到的群成员:', members)
    
    if (currentConversation.value) {
      // 更新当前会话的成员信息
      currentConversation.value.members = members
      currentConversation.value.memberCount = members.length
      console.log('更新后的会话成员信息:', currentConversation.value.members)
      console.log('更新后的成员数量:', currentConversation.value.memberCount)
      
      // 同时更新conversations数组中的对应会话
      const conversationIndex = conversations.value.findIndex(conv => 
        conv.type === 'group' && conv.groupId === currentConversation.value.groupId
      )
      if (conversationIndex !== -1) {
        conversations.value[conversationIndex].members = members
        conversations.value[conversationIndex].memberCount = members.length
        console.log('已更新conversations数组中的成员信息')
      }
    }
  }
  showGroupSettingsDialog.value = true
}

// 显示添加成员对话框
const showAddMemberDialog = () => {
  if (currentConversation.value?.type === 'group') {
    // 群聊：打开添加成员对话框
    if (groupSettingsDialogRef.value) {
      groupSettingsDialogRef.value.showAddMemberDialog()
    }
  } else {
    // 私聊：打开创建群聊对话框
    showCreateGroupDialog.value = true
  }
}

// 显示添加人员对话框（从菜单触发）
const openAddMembersDialog = () => {
  showAddMembersDialog.value = true
}

// 显示个人信息对话框
const showUserProfile = () => {
  console.log('当前用户信息:', currentUser.value)
  console.log('用户存储信息:', {
    userId: userStore.userId,
    name: userStore.name,
    nickname: userStore.nickname,
    avatar: userStore.avatar
  })
  showUserProfileDialog.value = true
}

// 编辑资料
const editProfile = () => {
  ElMessage.info('编辑资料功能待开发')
  // TODO: 实现编辑资料功能
}

// 处理添加群成员成功
const handleAddMembersSuccess = async () => {
  console.log('添加群成员成功')
  // 重新加载群成员列表并更新群聊信息
  if (currentConversation.value?.type === 'group') {
    try {
      const members = await loadGroupMembers(currentConversation.value.groupId)
      console.log('重新加载的群成员:', members)
      
      if (currentConversation.value) {
        // 更新当前会话的成员信息
        currentConversation.value.members = members
        currentConversation.value.memberCount = members.length
        console.log('更新后的会话成员信息:', currentConversation.value.members)
        console.log('更新后的成员数量:', currentConversation.value.memberCount)
        
        // 同时更新conversations数组中的对应会话
        const conversationIndex = conversations.value.findIndex(conv => 
          conv.id === currentConversation.value.id
        )
        if (conversationIndex !== -1) {
          conversations.value[conversationIndex].members = members
          conversations.value[conversationIndex].memberCount = members.length
          console.log('更新conversations数组中的群聊信息')
        }
      }
    } catch (error) {
      console.error('重新加载群成员失败:', error)
    }
  }
  showAddMembersDialog.value = false
}

// 处理添加人员对话框关闭
const handleAddMembersClose = () => {
  showAddMembersDialog.value = false
}

// 显示用户信息菜单
const showUserInfoMenu = () => {
  ElMessageBox.alert(
    `用户：${currentConversation.value.name}\n状态：${currentConversation.value.status === 'online' ? '在线' : '离线'}`,
    '用户信息',
    {
      confirmButtonText: '确定',
      type: 'info'
    }
  )
}

const insertEmoji = (emoji) => {
  inputMessage.value += emoji
  showEmojiPicker.value = false
}

const loadConversations = async () => {
  try {
    // 加载系统用户作为联系人
    await loadSystemUsers()
    
    // 加载会话列表
    await loadConversationList()
    
    // 加载群聊列表
    await loadGroupList()
    
    // 计算总未读数
    totalUnreadCount.value = conversations.value.reduce((sum, conv) => sum + conv.unreadCount, 0)
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败')
  }
}

const loadSystemUsers = async () => {
  try {
    console.log('开始加载系统用户...')
    
    // 优先使用IM专用API（无需权限）
    let response
    try {
      response = await getImUserList()
      console.log('IM用户API响应:', response)
    } catch (apiError) {
      console.warn('IM用户API调用失败，尝试系统用户API:', apiError)
      // 如果IM API失败，尝试系统用户API
      try {
        response = await getUserListForIm({
          pageNum: 1,
          pageSize: 1000,
          status: '0'
        })
        console.log('系统用户API响应:', response)
      } catch (systemApiError) {
        console.warn('系统用户API也失败，使用备选方案:', systemApiError)
        throw systemApiError
      }
    }
    
    const users = response.data || response.rows || []
    console.log('用户列表:', users)
    console.log('用户列表详细信息:', users.map(user => ({
      userId: user.userId,
      userName: user.userName,
      nickName: user.nickName,
      imStatus: user.imStatus,
      status: user.status
    })))
    
    if (users.length === 0) {
      console.warn('API返回的用户列表为空，使用备选数据')
      throw new Error('用户列表为空')
    }
    
    // 过滤掉当前用户，只显示其他用户作为联系人
    contacts.value = users
      .filter(user => user.userId !== currentUserId.value)
      .map(user => {
        const contact = {
          userId: user.userId,
          nickname: user.nickName || user.userName,
          avatar: user.avatar || '',
          status: user.imStatus || 'offline' // 使用实际的IM状态
        }
        console.log(`处理用户 ${user.userName}: imStatus=${user.imStatus}, 最终status=${contact.status}`)
        return contact
      })
    
    console.log('加载联系人完成:', contacts.value.length, '个联系人')
    console.log('最终联系人状态:', contacts.value.map(c => ({ name: c.nickname, status: c.status })))
  } catch (error) {
    console.error('加载系统用户失败:', error)
    console.error('错误详情:', error.response?.data || error.message || error)
    
    // 如果API调用失败，初始化为空列表
    contacts.value = []
    console.log('联系人列表已清空，等待真实数据')
  }
}

const loadConversationList = async () => {
  try {
    // 调用后端API获取真实的会话列表
    const { getConversationList } = await import('@/api/im')
    const response = await getConversationList()
    
    if (response.code === 200 && response.data) {
      conversations.value = response.data.map(conv => ({
        id: conv.id,
        name: conv.name,
        avatar: conv.avatar,
        lastMessage: conv.lastMessage || '',
        lastTime: conv.lastTime ? new Date(conv.lastTime) : new Date(),
        unreadCount: conv.unreadCount || 0,
        type: conv.type,
        userId: conv.userId,
        groupId: conv.groupId,
        memberCount: conv.memberCount || 0,
        members: conv.members || [],
        status: conv.status
      }))
      console.log('会话列表已加载:', conversations.value.length, '个会话')
    } else {
      // 如果API失败，初始化为空列表
      conversations.value = []
      console.log('API获取会话列表失败，初始化为空列表')
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
    // 出错时初始化为空列表
    conversations.value = []
  }
}

const loadGroupList = async () => {
  try {
    // 调用后端API获取群聊列表
    const { getGroupList } = await import('@/api/im')
    const response = await getGroupList()
    
    if (response.code === 200) {
      groups.value = response.data || []
      console.log('加载群聊完成:', groups.value.length, '个群聊')
    } else {
      console.log('获取群聊列表失败:', response.msg)
      groups.value = []
    }
  } catch (error) {
    console.error('加载群聊列表失败:', error)
    groups.value = []
  }
}

// 群聊设置相关方法
const loadGroupMembers = async (groupId) => {
  try {
    const { getGroupMembers } = await import('@/api/im')
    const response = await getGroupMembers(groupId)
    
    if (response.code === 200) {
      const members = response.data || []
      return members
    } else {
      ElMessage.error('获取群成员失败: ' + response.msg)
      return []
    }
  } catch (error) {
    ElMessage.error('获取群成员失败: ' + error.message)
    return []
  }
}

const inviteMembers = () => {
  showAddMembersDialog.value = true
}

const removeMembers = () => {
  // 切换移除模式
  isRemovingMembers.value = !isRemovingMembers.value
  
  if (!isRemovingMembers.value) {
    // 如果退出移除模式，清空选择
    selectedMembers.value = []
    ElMessage.info('已退出移除模式')
  } else {
    ElMessage.info('进入移除模式，点击成员头像进行选择')
  }
}

// 检查成员是否被选中
const isMemberSelected = (userId) => {
  return selectedMembers.value.some(member => member.userId === userId)
}

// 切换成员选择状态
const toggleMemberSelection = (member) => {
  if (!isRemovingMembers.value) {
    return
  }
  
  // 不能选择群主和自己
  if (member.userId === currentUser?.userId || member.role === 'owner') {
    return
  }
  
  const index = selectedMembers.value.findIndex(m => m.userId === member.userId)
  if (index > -1) {
    selectedMembers.value.splice(index, 1)
  } else {
    selectedMembers.value.push(member)
  }
}

// 清空成员选择
const clearMemberSelection = () => {
  selectedMembers.value = []
}

// 确认移除成员
const confirmRemoveMembers = async () => {
  if (selectedMembers.value.length === 0) {
    ElMessage.warning('请选择要移除的成员')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要移除这 ${selectedMembers.value.length} 名成员吗？`,
      '确认移除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 执行移除操作
    await removeSelectedMembers()
  } catch (error) {
    // 用户取消操作
  }
}

// 移除选中的成员
const removeSelectedMembers = async () => {
  if (!currentConversation.value?.groupId) {
    ElMessage.error('群聊信息不完整')
    return
  }
  
  try {
    isRemovingMembers.value = false
    const { removeGroupMember } = await import('@/api/im')
    
    // 逐个移除成员
    const removePromises = selectedMembers.value.map(member => 
      removeGroupMember(currentConversation.value.groupId, member.userId)
    )
    
    const results = await Promise.allSettled(removePromises)
    
    // 检查结果
    const successCount = results.filter(result => 
      result.status === 'fulfilled' && result.value.code === 200
    ).length
    
    const failedCount = results.length - successCount
    
    if (successCount > 0) {
      ElMessage.success(`成功移除 ${successCount} 名成员`)
      
      // 重新加载群成员列表
      const members = await loadGroupMembers(currentConversation.value.groupId)
      if (currentConversation.value) {
        currentConversation.value.members = members
        currentConversation.value.memberCount = members.length
        
        // 同时更新conversations数组中的对应会话
        const conversationIndex = conversations.value.findIndex(conv => 
          conv.id === currentConversation.value.id
        )
        if (conversationIndex !== -1) {
          conversations.value[conversationIndex].members = members
          conversations.value[conversationIndex].memberCount = members.length
        }
      }
    }
    
    if (failedCount > 0) {
      ElMessage.warning(`${failedCount} 名成员移除失败`)
    }
    
    // 清空选择
    selectedMembers.value = []
    
  } catch (error) {
    console.error('移除群成员失败:', error)
    ElMessage.error('移除群成员失败: ' + error.message)
  }
}

const muteMember = () => {
  ElMessage.info('禁言功能待实现')
}

const unmuteMember = () => {
  ElMessage.info('解除禁言功能待实现')
}

const clearChatHistory = async () => {
  try {
    await ElMessageBox.confirm('确定要清空聊天记录吗？此操作不可恢复！', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // TODO: 调用后端API清空聊天记录
    currentMessages.value = []
    ElMessage.success('聊天记录已清空')
  } catch (error) {
    // 用户取消操作
  }
}

const reportGroup = () => {
  ElMessage.info('举报功能待实现')
}

// 监听弹窗显示状态
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    loadConversations()
  }
})

// 处理收到的消息
const handleMessageReceived = (event) => {
  const message = event.detail
  const messageKey = message?.messageId || `${message?.fromUserId}-${message?.toUserId || message?.groupId}-${message?.createTime || ''}-${message?.content || ''}`
  if (receivedMessageKeys.has(messageKey)) {
    return
  }
  receivedMessageKeys.add(messageKey)
  
  // 检查是否是发给当前用户的消息（确保类型一致）
  const isMessageForCurrentUser = Number(message.toUserId) === Number(currentUserId.value)
  const isMessageFromCurrentUser = Number(message.fromUserId) === Number(currentUserId.value)
  
  // 更新会话列表中的未读消息数量
  updateConversationUnreadCount(message)
  
  // 处理私聊消息
  if (message.toUserId && !message.groupId) {
    // 如果当前正在与发送者或接收者聊天，将消息添加到当前消息列表（确保类型一致）
    if (currentConversation.value && 
        (Number(currentConversation.value.userId) === Number(message.fromUserId) || 
         Number(currentConversation.value.userId) === Number(message.toUserId))) {
      
      const newMessage = {
        id: message.messageId || Date.now(),
        fromUserId: message.fromUserId,
        toUserId: message.toUserId,
        content: message.content,
        messageType: message.messageType || 'text',
        createTime: new Date(message.createTime || Date.now()),
        nickname: message.senderName || '未知用户',
        avatar: message.senderAvatar || '',
        status: 'received'
      }
      
      currentMessages.value.push(newMessage)
      
      // 滚动到底部
      nextTick(() => {
        scrollToBottom()
      })
      
      // 如果是当前会话，标记为已读
      markCurrentConversationAsRead()
    }
  }
  // 处理群聊消息
  else if (message.groupId) {
    // 如果当前正在查看这个群聊，将消息添加到当前消息列表
    if (currentConversation.value && 
        currentConversation.value.groupId === message.groupId) {
      
      const newMessage = {
        id: message.messageId || Date.now(),
        fromUserId: message.fromUserId,
        content: message.content,
        messageType: message.messageType || 'text',
        createTime: new Date(message.createTime || Date.now()),
        nickname: message.senderName || '未知用户',
        avatar: message.senderAvatar || '',
        status: 'received',
        groupId: message.groupId
      }
      
      currentMessages.value.push(newMessage)
      
      // 滚动到底部
      nextTick(() => {
        scrollToBottom()
      })
      
      // 如果是当前会话，标记为已读
      markCurrentConversationAsRead()
    }
  }
}

const updateConversationUnreadCount = (message) => {
  console.log('开始更新会话未读数量:', message)
  
  // 找到对应的会话并更新未读数量
  const conversation = conversations.value.find(conv => {
    if (message.groupId) {
      // 群聊消息
      console.log('检查群聊消息:', conv.groupId, message.groupId)
      return conv.type === 'group' && conv.groupId === message.groupId
    } else if (message.toUserId || message.fromUserId) {
      // 私聊消息 - 需要确定对方用户ID
      const currentUserIdNum = Number(currentUserId.value)
      const messageToUserId = Number(message.toUserId)
      const messageFromUserId = Number(message.fromUserId)
      
      // 确定对方用户ID：如果消息是发给我的，对方是发送者；如果消息是我发的，对方是接收者
      let otherUserId
      if (messageToUserId === currentUserIdNum) {
        // 消息是发给我的，对方是发送者
        otherUserId = messageFromUserId
      } else if (messageFromUserId === currentUserIdNum) {
        // 消息是我发的，对方是接收者
        otherUserId = messageToUserId
      } else {
        // 其他情况，使用发送者作为对方
        otherUserId = messageFromUserId
      }
      
      console.log('检查私聊消息:', {
        convUserId: conv.userId,
        otherUserId: otherUserId,
        convType: conv.type,
        messageToUserId: messageToUserId,
        messageFromUserId: messageFromUserId,
        currentUserIdNum: currentUserIdNum,
        isMatch: conv.type === 'private' && Number(conv.userId) === otherUserId
      })
      return conv.type === 'private' && Number(conv.userId) === otherUserId
    }
    return false
  })
  
  if (conversation) {
    // 增加未读数量
    conversation.unreadCount = (conversation.unreadCount || 0) + 1
    // 更新最后消息
    conversation.lastMessage = message.content
    conversation.lastTime = new Date(message.createTime || new Date())
    
    console.log('更新会话未读数量成功:', conversation.name, conversation.unreadCount)
  } else {
    console.log('未找到匹配的会话:', message)
  }
}

const markCurrentConversationAsRead = async () => {
  if (currentConversation.value) {
    try {
      const { markMessagesAsRead } = await import('@/api/im')
      
      if (currentConversation.value.type === 'private') {
        await markMessagesAsRead(currentConversation.value.userId, undefined)
      } else if (currentConversation.value.type === 'group') {
        await markMessagesAsRead(undefined, currentConversation.value.groupId)
      }
      
      // 更新当前会话的未读数量为0
      currentConversation.value.unreadCount = 0
      
      console.log('当前会话消息已标记为已读:', currentConversation.value.name)
    } catch (error) {
      console.error('标记当前会话消息已读失败:', error)
    }
  }
}

onMounted(() => {
  // 初始化
  loadConversations()
  
  // 监听消息接收事件
  window.addEventListener('im-message-received', handleMessageReceived)
  if (!imStore.isConnected) {
    imStore.login()
  }
})

onUnmounted(() => {
  // 清理
  window.removeEventListener('im-message-received', handleMessageReceived)
})
watch(() => userStore.name, (name) => {
  if (name && !imStore.isConnected) {
    imStore.login()
  }
})
</script>

<style scoped>
.im-chat-dialog {
  .el-dialog__header {
    padding: 0;
    background: #2e2e2e;
    border-bottom: 1px solid #3e3e3e;
  }
  
  .el-dialog__body {
    padding: 0;
    background: #f5f5f5;
  }
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #2e2e2e;
  color: white;
  
  .header-title {
    font-size: 16px;
    font-weight: 500;
  }
  
  .header-actions {
    display: flex;
    gap: 4px;
    
    .action-btn {
      color: white;
      padding: 8px;
      
      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }
}

.im-container {
  height: 560px;
  display: flex;
  background: #f5f5f5;
  
  &.minimized {
    height: 40px;
    background: #2e2e2e;
  }
}

.nav-sidebar {
  width: 80px;
  background: #2c3e50;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  
  .user-section {
    padding: 16px 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    border-bottom: 1px solid #34495e;
    margin-bottom: 16px;
    cursor: pointer;
    transition: background-color 0.2s;
    border-radius: 8px;
    
    &:hover {
      background-color: rgba(255, 255, 255, 0.1);
    }
    
    .user-avatar {
      border: 2px solid #ecf0f1;
    }
    
    .user-info {
      text-align: center;
      
      .username {
        font-weight: 500;
        font-size: 12px;
        color: #ecf0f1;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 60px;
        margin-bottom: 2px;
      }
      
      .user-status {
        font-size: 10px;
        color: #bdc3c7;
      }
    }
  }
  
  .search-section {
    padding: 8px;
    margin-bottom: 16px;
    
    .search-input {
      width: 100%;
      
      :deep(.el-input__wrapper) {
        background: #34495e;
        border: 1px solid #34495e;
        border-radius: 20px;
        box-shadow: none;
        
        &:hover, &.is-focus {
          border-color: #3498db;
        }
      }
      
      :deep(.el-input__inner) {
        color: #ecf0f1;
        text-align: center;
        font-size: 12px;
        
        &::placeholder {
          color: #bdc3c7;
        }
      }
      
      :deep(.el-input__prefix) {
        color: #bdc3c7;
      }
    }
  }
  
  .nav-menu {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    padding: 0 8px;
    
    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 8px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;
      color: #bdc3c7;
      
      &:hover {
        background: #34495e;
        color: #ecf0f1;
      }
      
      &.active {
        background: #3498db;
        color: white;
      }
      
      .el-icon {
        font-size: 20px;
        margin-bottom: 4px;
      }
      
      span {
        font-size: 10px;
        text-align: center;
        line-height: 1.2;
      }
    }
  }
}

.middle-content {
  width: 280px;
  background: white;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  
  .conversation-list,
  .contact-list,
  .group-list,
  .settings-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }
  
  .conversation-item,
  .contact-item,
  .group-item {
    display: flex;
    align-items: center;
    padding: 12px;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    margin-bottom: 4px;
    
    .conversation-avatar-wrapper {
      position: relative;
      margin-right: 12px;
      
      .unread-badge {
        position: absolute;
        top: -5px;
        right: -5px;
        background: #ff4444;
        color: white;
        border-radius: 10px;
        min-width: 18px;
        height: 18px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 11px;
        font-weight: bold;
        line-height: 1;
        padding: 0 4px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.3);
      }
    }
    
    &:hover {
      background: #f5f5f5;
    }
    
    &.active {
      background: #e3f2fd;
      border-left: 3px solid #2196f3;
    }
  }
  
  .conversation-avatar,
  .contact-avatar,
  .group-avatar {
    margin-right: 12px;
  }
  
  .conversation-info,
  .contact-info,
  .group-info {
    flex: 1;
    min-width: 0;
    
    .conversation-name,
    .contact-name,
    .group-name {
      font-weight: 500;
      font-size: 14px;
      color: #333;
      margin-bottom: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    
    .conversation-preview,
    .contact-status,
    .group-members {
      font-size: 12px;
      color: #666;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
  
  .conversation-meta,
  .contact-actions,
  .group-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    
    .conversation-time {
      font-size: 11px;
      color: #999;
    }
    
    .unread-badge {
      font-size: 10px;
    }
  }
  
  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: #333;
    padding: 16px 12px 8px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    
    .group-actions {
      display: flex;
      gap: 8px;
      
      .el-button {
        font-size: 12px;
        padding: 4px 8px;
      }
    }
  }
  
  .setting-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid #f0f0f0;
    
    .el-icon {
      margin-right: 12px;
      color: #666;
    }
    
    span {
      flex: 1;
      font-size: 14px;
      color: #333;
    }
    
    .el-switch {
      margin-left: auto;
    }
  }
}

.sidebar {
  width: 280px;
  background: white;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  
  .user-section {
    padding: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;
    transition: background-color 0.2s;
    border-radius: 8px;
    
    &:hover {
      background-color: #f5f5f5;
    }
    
    .user-avatar {
      border: 2px solid #4a4a4a;
    }
    
    .user-info {
      .username {
        font-weight: 500;
        color: #333;
        margin-bottom: 2px;
      }
      
      .user-status {
        font-size: 12px;
        color: #4caf50;
      }
    }
  }
  
  .search-section {
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;
    background: #fff;
    
    .search-input {
      :deep(.el-input__inner) {
        border-radius: 20px;
        border: 1px solid #e0e0e0;
      }
    }
  }
  
  .nav-menu {
    padding: 8px 0;
    border-bottom: 1px solid #f0f0f0;
    
    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      cursor: pointer;
      color: #666;
      
      &.active {
        background: #e3f2fd;
        color: #1976d2;
        border-right: 3px solid #1976d2;
      }
      
      &:hover:not(.active) {
        background: #f5f5f5;
      }
      
      span {
        font-size: 14px;
      }
    }
  }
  
  .content-area {
    flex: 1;
    overflow-y: auto;
    
    .conversation-list, .contact-list, .group-list, .settings-list {
      .conversation-item, .contact-item, .group-item {
        display: flex;
        align-items: center;
        padding: 12px 16px;
        cursor: pointer;
        border-bottom: 1px solid #f0f0f0;
        
        &.active {
          background: #e3f2fd;
          border-right: 3px solid #1976d2;
        }
        
        &:hover:not(.active) {
          background: #f5f5f5;
        }
        
        .conversation-avatar, .contact-avatar, .group-avatar {
          margin-right: 12px;
        }
        
        .conversation-info, .contact-info, .group-info {
          flex: 1;
          min-width: 0;
          
          .conversation-name, .contact-name, .group-name {
            font-weight: 500;
            color: #333;
            margin-bottom: 4px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
          
          .conversation-preview {
            font-size: 12px;
            color: #666;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
          
          .contact-status {
            font-size: 12px;
            color: #999;
            
            &.online {
              color: #4caf50;
            }
          }
          
          .group-members {
            font-size: 12px;
            color: #666;
          }
        }
        
        .conversation-meta, .contact-actions, .group-actions {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          
          .conversation-time {
            font-size: 11px;
            color: #999;
            margin-bottom: 4px;
          }
          
          .unread-badge {
            :deep(.el-badge__content) {
              background: #f56c6c;
              border: none;
            }
          }
          
          .el-button {
            color: #07c160;
          }
        }
      }
    }
    
    .contact-section, .group-section, .settings-section {
      .section-title {
        padding: 12px 16px 8px;
        font-size: 14px;
        font-weight: 500;
        color: #666;
        background: #f8f8f8;
        border-bottom: 1px solid #e0e0e0;
      }
    }
    
    .settings-list {
      .setting-item {
        display: flex;
        align-items: center;
        padding: 12px 16px;
        border-bottom: 1px solid #f0f0f0;
        
        .el-icon {
          margin-right: 12px;
          color: #666;
        }
        
        span {
          flex: 1;
          color: #333;
        }
        
        .el-switch, .el-icon:last-child {
          margin-left: auto;
        }
        
        &:hover {
          background: #f5f5f5;
        }
      }
    }
  }
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  
  .chat-header-area {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid #e0e0e0;
    background: white;
    
    .chat-user-info {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .chat-user-details {
        .chat-username {
          font-weight: 500;
          color: #333;
          margin-bottom: 2px;
        }
        
        .chat-status {
          font-size: 12px;
          color: #999;
          
          &.online {
            color: #4caf50;
          }
        }
      }
    }
    
    .chat-actions {
      display: flex;
      gap: 8px;
      
      .el-button {
        color: #666;
        
        &:hover {
          background: #f5f5f5;
        }
      }
    }
  }
  
  .message-area {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
    background: #f5f5f5;
    
    .message-item {
      display: flex;
      margin-bottom: 16px;
      align-items: flex-end;
      
      &.own {
        flex-direction: row-reverse;
      }
      
      .message-avatar {
        margin: 0 8px;
        flex-shrink: 0;
      }
      
      .message-content {
        max-width: 70%;
        display: flex;
        flex-direction: column;
        
        .message-bubble {
          background: white;
          padding: 12px 16px;
          border-radius: 8px;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
          
          &.own {
            background: #95ec69;
          }
          
          .text-message {
            word-wrap: break-word;
            line-height: 1.4;
          }
          
          .image-message {
            .message-image {
              max-width: 200px;
              max-height: 200px;
              border-radius: 4px;
            }
          }
          
          .file-message {
            display: flex;
            align-items: center;
            gap: 8px;
            
            .file-icon {
              font-size: 24px;
              color: #666;
            }
            
            .file-info {
              .file-name {
                font-weight: 500;
                color: #333;
              }
              
              .file-size {
                font-size: 12px;
                color: #666;
              }
            }
          }
        }
        
        .message-time {
          font-size: 11px;
          color: #999;
          margin-top: 4px;
          text-align: center;
        }
      }
    }
  }
  
  .input-area {
    background: white;
    border-top: 1px solid #e0e0e0;
    
    .input-toolbar {
      display: flex;
      padding: 8px 16px;
      border-bottom: 1px solid #f0f0f0;
      gap: 8px;
      
      .tool-btn {
        padding: 8px;
        color: #666;
        
        &:hover {
          background: #f5f5f5;
          color: #333;
        }
      }
    }
    
    .input-box {
      display: flex;
      padding: 12px 16px;
      gap: 12px;
      align-items: flex-end;
      
      .message-input {
        flex: 1;
        
        :deep(.el-textarea__inner) {
          border: none;
          box-shadow: none;
          resize: none;
          font-size: 14px;
          line-height: 1.4;
        }
      }
      
      .send-btn {
        height: 36px;
        padding: 0 20px;
        border-radius: 18px;
        background: #07c160;
        border-color: #07c160;
        
        &:hover {
          background: #06ad56;
          border-color: #06ad56;
        }
      }
    }
  }
  
  .empty-state {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #999;
    
    .empty-icon {
      font-size: 48px;
      margin-bottom: 16px;
    }
    
    .empty-text {
      font-size: 16px;
    }
  }
}

.minimized-content {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: #2e2e2e;
  cursor: pointer;
  color: white;
  
  .minimized-info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    
    .minimized-name {
      font-size: 14px;
      font-weight: 500;
    }
    
    .unread-badge {
      margin-left: auto;
    }
  }
}

.emoji-dialog {
  .emoji-picker {
    .emoji-grid {
      display: grid;
      grid-template-columns: repeat(8, 1fr);
      gap: 8px;
      
      .emoji-item {
        font-size: 24px;
        text-align: center;
        padding: 8px;
        cursor: pointer;
        border-radius: 4px;
        
        &:hover {
          background: #f5f5f5;
        }
      }
    }
  }
}

/* 会话菜单样式 */
:deep(.el-dropdown-menu) {
  padding: 8px 0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 1px solid #e4e7ed;
  
  .el-dropdown-menu__item {
    display: flex;
    align-items: center;
    padding: 8px 16px;
    font-size: 14px;
    color: #333;
    transition: all 0.2s;
    
    &:hover {
      background-color: #f5f7fa;
      color: #409eff;
    }
    
    .menu-icon {
      margin-right: 8px;
      font-size: 16px;
      color: #666;
    }
    
    span {
      font-size: 14px;
    }
  }
  
  .el-dropdown-menu__item--divided {
    border-top: 1px solid #e4e7ed;
    margin-top: 4px;
    padding-top: 8px;
  }
}

/* 成员列表样式 */
.chat-members {
  flex: 1;
  margin: 0 16px;
  
  .member-list {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow-x: auto;
    padding: 4px 0;
    
    .member-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 50px;
      cursor: pointer;
      transition: all 0.2s;
      
      &:hover {
        transform: translateY(-2px);
      }
      
      .member-name {
        font-size: 12px;
        color: #666;
        margin-top: 4px;
        text-align: center;
        max-width: 50px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      &.current-user {
        .member-name {
          color: #07c160;
          font-weight: 500;
        }
      }
      
      &.add-member {
        .add-icon {
          width: 32px;
          height: 32px;
          border: 2px dashed #ddd;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #999;
          transition: all 0.2s;
          
          &:hover {
            border-color: #07c160;
            color: #07c160;
            background-color: #f0f9f4;
          }
        }
        
        .member-name {
          color: #999;
        }
      }
    }
  }
}

/* 群聊设置对话框样式 */
.group-settings-dialog {
  :deep(.el-dialog) {
    max-height: 80vh;
  }
  
  :deep(.el-dialog__body) {
    padding: 0;
    max-height: 60vh;
    overflow-y: auto;
  }
}

.group-settings-content {
  max-height: 60vh;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  
  /* 自定义滚动条样式 */
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 3px;
    
    &:hover {
      background: #a8a8a8;
    }
  }
  .settings-section {
    border-bottom: 1px solid #f0f0f0;
    padding: 16px 0;
    
    &:last-child {
      border-bottom: none;
    }
    
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      padding: 0 20px;
      
      .section-title {
        font-size: 16px;
        font-weight: 600;
        color: #333;
      }
      
      .member-count {
        font-size: 14px;
        color: #666;
      }
    }
    
    .member-avatars {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      padding: 0 20px;
      margin-bottom: 12px;
      
      .member-avatar-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        position: relative;
        cursor: pointer;
        transition: all 0.2s;
        
        &:hover {
          transform: translateY(-2px);
        }
        
        &.removable {
          cursor: pointer;
          
          &:hover {
            background-color: #f5f7fa;
            border-radius: 8px;
            padding: 4px;
          }
        }
        
        &.selected {
          background-color: #e6f7ff;
          border: 2px solid #1890ff;
          border-radius: 8px;
          padding: 4px;
        }
        
        &.current-user {
          opacity: 0.6;
          cursor: not-allowed;
        }
        
        .member-name {
          font-size: 12px;
          color: #666;
          text-align: center;
          max-width: 80px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        
        .member-role {
          font-size: 10px;
          color: #ff6b35;
          font-weight: 500;
          background-color: #fff2e8;
          padding: 1px 4px;
          border-radius: 4px;
          margin-top: 2px;
        }
        
        .member-selected {
          position: absolute;
          top: -2px;
          right: -2px;
          width: 20px;
          height: 20px;
          background-color: #1890ff;
          color: white;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
          font-weight: bold;
        }
      }
      
      .remove-members-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 20px;
        background-color: #fff2e8;
        border: 1px solid #ffd591;
        border-radius: 8px;
        margin: 12px 20px;
        
        .selected-count {
          font-size: 14px;
          color: #d46b08;
          font-weight: 500;
        }
        
        .el-button {
          margin-left: 8px;
        }
      }
      
      .no-members {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        padding: 20px;
        
        .no-members-text {
          font-size: 14px;
          color: #999;
          text-align: center;
        }
      }
    }
    
    .member-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      padding: 0 20px;
      
      .action-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        cursor: pointer;
        transition: all 0.2s;
        
        &:hover {
          transform: scale(1.05);
        }
        
        &.active {
          .action-icon {
            background: #1890ff !important;
            color: white !important;
          }
          
          span {
            color: #1890ff;
            font-weight: 500;
          }
        }
        
        .action-icon {
          width: 40px;
          height: 40px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          font-weight: bold;
          
          &.invite {
            background: #f0f0f0;
            color: #333;
          }
          
          &.remove {
            background: #f0f0f0;
            color: #333;
          }
          
          &.mute {
            background: #f0f0f0;
            color: #333;
          }
          
          &.unmute {
            background: #f0f0f0;
            color: #333;
          }
        }
        
        span {
          font-size: 12px;
          color: #666;
        }
      }
    }
    
    .setting-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 20px;
      border-bottom: 1px solid #f8f8f8;
      
      &:last-child {
        border-bottom: none;
      }
      
      span {
        font-size: 14px;
        color: #333;
      }
      
      .setting-value {
        font-size: 14px;
        color: #666;
        
        &.placeholder {
          color: #999;
        }
      }
    }
    
    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #333;
      padding: 0 20px;
      margin-bottom: 12px;
    }
    
    .announcement-input {
      margin: 0 20px;
      
      :deep(.el-textarea__inner) {
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        resize: none;
      }
    }
  }
  
  .settings-actions {
    padding: 20px;
    text-align: center;
    
    .clear-button {
      width: 100%;
      margin-bottom: 12px;
    }
    
    .report-button {
      color: #409eff;
      font-size: 14px;
      margin-bottom: 12px;
    }
    
    .close-button {
      width: 100%;
      background: #f5f5f5;
      color: #666;
      border: 1px solid #ddd;
      
      &:hover {
        background: #e8e8e8;
        color: #333;
      }
    }
  }
}

/* 群聊头部人数显示样式 */
.member-count {
  color: #666;
  font-size: 12px;
  font-weight: normal;
  margin-left: 4px;
}

/* 个人信息对话框样式 */
.user-profile-content {
  .profile-header {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 20px 0;
    border-bottom: 1px solid #e0e0e0;
    margin-bottom: 20px;
    
    .profile-avatar {
      border: 3px solid #f0f0f0;
    }
    
    .profile-info {
      flex: 1;
      
      .profile-name {
        margin: 0 0 8px 0;
        font-size: 24px;
        font-weight: 600;
        color: #333;
      }
      
      .profile-username {
        margin: 0 0 12px 0;
        font-size: 14px;
        color: #666;
      }
      
      .profile-status {
        .el-tag {
          font-size: 12px;
        }
      }
    }
  }
  
  .profile-details {
    .el-descriptions {
      .el-descriptions__label {
        font-weight: 500;
        color: #666;
        width: 100px;
      }
      
      .el-descriptions__content {
        color: #333;
      }
    }
  }
}
</style>
