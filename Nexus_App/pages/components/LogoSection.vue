<template>
	<view class="logo-section">
		<image class="logo" src="@/static/logo.png"></image>
		<text class="system-title">{{ title }}</text>
		<text class="system-subtitle">{{ subtitle }}</text>
	</view>
</template>

<script>
export default {
	name: 'LogoSection',
	props: {
		title: {
			type: String,
			default: 'Nexus智能ERP系统'
		},
		subtitle: {
			type: String,
			default: '智能制造 · 数字化管理'
		}
	}
}
</script>

<style scoped>
.logo-section {
	text-align: center;
	margin: 40rpx 0;
}

.logo {
	width: 150rpx;
	height: 150rpx;
	border-radius: 20rpx;
	box-shadow: 0 10rpx 30rpx rgba(0, 0, 0, 0.1);
}

.system-title {
	display: block;
	font-size: 36rpx;
	font-weight: bold;
	color: #333;
	margin-top: 20rpx;
}

.system-subtitle {
	display: block;
	font-size: 28rpx;
	color: #666;
	margin-top: 10rpx;
}
</style>