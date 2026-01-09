<template>
	<view class="container">
		<!-- 顶部导航栏 -->
		<view class="top-bar">
			<view class="welcome-section">
				<text class="greeting">欢迎回来，</text>
				<text class="username">管理员</text>
			</view>
			<view class="date-section">
				<text class="current-date">{{ currentDate }}</text>
			</view>
		</view>

		<!-- Logo和系统标题 -->
		<view class="logo-section">
			<image class="logo" src="@/static/logo.png"></image>
			<text class="system-title">Nexus智能ERP系统</text>
			<text class="system-subtitle">智能制造 · 数字化管理</text>
		</view>

		<!-- 快捷功能区 -->
		<view class="quick-actions">
			<text class="section-title">核心功能</text>
			<view class="actions-grid">
				<view class="action-item" @click="goToPage('采购管理')">
					<view class="action-icon purchase-bg">
						<text class="iconfont icon-caigou"></text>
					</view>
					<text class="action-label">采购管理</text>
				</view>
				<view class="action-item" @click="goToPage('销售管理')">
					<view class="action-icon sales-bg">
						<text class="iconfont icon-xiaoshou"></text>
					</view>
					<text class="action-label">销售管理</text>
				</view>
				<view class="action-item" @click="goToPage('库存管理')">
					<view class="action-icon inventory-bg">
						<text class="iconfont icon-kucun"></text>
					</view>
					<text class="action-label">库存管理</text>
				</view>
				<view class="action-item" @click="goToPage('生产管理')">
					<view class="action-icon production-bg">
						<text class="iconfont icon-shengchan"></text>
					</view>
					<text class="action-label">生产管理</text>
				</view>
			</view>
		</view>

		<!-- 智能制造模块 -->
		<view class="manufacturing-section">
			<text class="section-title">智能制造</text>
			<view class="manufacturing-grid">
				<view class="manufacturing-item" @click="goToPage('设备管理')">
					<view class="item-icon equipment-bg">
						<text class="iconfont icon-shebei"></text>
					</view>
					<view class="item-content">
						<text class="item-title">设备管理</text>
						<text class="item-desc">监控设备状态，维护记录</text>
					</view>
				</view>
				<view class="manufacturing-item" @click="goToPage('质量管理')">
					<view class="item-icon quality-bg">
						<text class="iconfont icon-zhiliang"></text>
					</view>
					<view class="item-content">
						<text class="item-title">质量管理</text>
						<text class="item-desc">产品质量检测与追溯</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 数据统计卡片 -->
		<view class="stats-section">
			<view class="stat-card">
				<view class="stat-header">
					<text class="stat-title">今日生产数据</text>
				</view>
				<view class="stat-values">
					<view class="stat-item">
						<text class="stat-number">1,245</text>
						<text class="stat-label">今日产量</text>
					</view>
					<view class="stat-item">
						<text class="stat-number">98.7%</text>
						<text class="stat-label">合格率</text>
					</view>
					<view class="stat-item">
						<text class="stat-number">2</text>
						<text class="stat-label">异常数</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			currentDate: ''
		}
	},
	onLoad() {
		this.getCurrentDate();
	},
	methods: {
		getCurrentDate() {
			const now = new Date();
			const year = now.getFullYear();
			const month = String(now.getMonth() + 1).padStart(2, '0');
			const day = String(now.getDate()).padStart(2, '0');
			const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
			this.currentDate = `${year}年${month}月${day}日 星期${week}`;
		},
		goToPage(moduleName) {
			uni.showToast({
				title: `跳转到${moduleName}`,
				icon: 'none'
			});
			// 实际项目中可以跳转到相应页面
			// uni.navigateTo({
			// 	url: `/pages/${moduleName.toLowerCase()}/index`
			// });
		}
	}
}
</script>

<style>
@import '@/static/font/iconfont.css';

.container {
	padding: 0;
	background-color: #f5f5f5;
	min-height: 100vh;
}

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

.section-title {
	font-size: 32rpx;
	font-weight: bold;
	color: #333;
	margin: 20rpx 40rpx 30rpx;
	display: block;
}

.quick-actions .actions-grid {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 20rpx;
	padding: 0 30rpx;
}

.action-item {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.action-icon {
	width: 80rpx;
	height: 80rpx;
	border-radius: 20rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 10rpx;
}

.purchase-bg {
	background: linear-gradient(135deg, #ff9a9e, #fecfef);
}

.sales-bg {
	background: linear-gradient(135deg, #a8edea, #fed6e3);
}

.inventory-bg {
	background: linear-gradient(135deg, #ffecd2, #fcb69f);
}

.production-bg {
	background: linear-gradient(135deg, #a8e6cf, #dcedc1);
}

.action-label {
	font-size: 24rpx;
	color: #666;
}

.manufacturing-section {
	margin: 30rpx 40rpx;
}

.manufacturing-grid {
	background: white;
	border-radius: 20rpx;
	overflow: hidden;
	box-shadow: 0 10rpx 30rpx rgba(0, 0, 0, 0.05);
}

.manufacturing-item {
	display: flex;
	align-items: center;
	padding: 30rpx;
	border-bottom: 1rpx solid #eee;
}

.manufacturing-item:last-child {
	border-bottom: none;
}

.item-icon {
	width: 80rpx;
	height: 80rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 20rpx;
}

.equipment-bg {
	background: linear-gradient(135deg, #ff9a9e, #fad0c4);
}

.quality-bg {
	background: linear-gradient(135deg, #a8edea, #fed6e3);
}

.item-content {
	flex: 1;
}

.item-title {
	font-size: 30rpx;
	font-weight: bold;
	color: #333;
	display: block;
}

.item-desc {
	font-size: 24rpx;
	color: #999;
	display: block;
	margin-top: 10rpx;
}

.stats-section {
	padding: 0 40rpx 40rpx;
}

.stat-card {
	background: white;
	border-radius: 20rpx;
	padding: 30rpx;
	box-shadow: 0 10rpx 30rpx rgba(0, 0, 0, 0.05);
}

.stat-header {
	margin-bottom: 20rpx;
}

.stat-title {
	font-size: 30rpx;
	font-weight: bold;
	color: #333;
}

.stat-values {
	display: flex;
	justify-content: space-around;
}

.stat-item {
	text-align: center;
	flex: 1;
}

.stat-number {
	display: block;
	font-size: 36rpx;
	font-weight: bold;
	color: #667eea;
	margin-bottom: 10rpx;
}

.stat-label {
	display: block;
	font-size: 24rpx;
	color: #999;
}
</style>