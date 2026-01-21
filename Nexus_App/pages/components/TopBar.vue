<template>
	<view class="top-bar">
		<view class="welcome-section">
			<text class="greeting">欢迎回来，</text>
			<text class="username">{{ currentUserInfo.name }}</text>
		</view>
		<view class="date-section">
			<text class="current-date">{{ currentDate }}</text>
		</view>
	</view>
</template>

<script>
import { useUserStore } from '@/store/modules/user'

export default {
	name: 'TopBar',
	setup() {
		const userStore = useUserStore()
		return {
			userStore
		}
	},
	computed: {
		currentUserInfo() {
			return {
				name: this.userStore.name || '管理员'
			}
		}
	},
	props: {
		currentDate: {
			type: String,
			default: ''
		}
	}
}
</script>

<style scoped>
.top-bar {
	background: linear-gradient(135deg, #667eea, #764ba2);
	color: white;
	padding: 30rpx 40rpx 60rpx;
	border-radius: 0 0 40rpx 40rpx;
	margin-bottom: 30rpx;
}

.welcome-section {
	display: flex;
	align-items: baseline;
}

.greeting {
	font-size: 32rpx;
	opacity: 0.9;
}

.username {
	font-size: 36rpx;
	font-weight: bold;
	margin-left: 10rpx;
}

.date-section {
	margin-top: 10rpx;
	font-size: 26rpx;
	opacity: 0.8;
}
</style>