<template>
	<view class="stats-section">
		<view class="stat-card">
			<view class="stat-header">
				<text class="stat-title">{{ stats.title }}</text>
			</view>
			<view class="stat-values">
				<view 
					v-for="(item, index) in stats.items" 
					:key="index" 
					class="stat-item"
				>
					<text class="stat-number" :style="{ color: item.color }">{{ item.number }}</text>
					<text class="stat-label">{{ item.label }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'StatsSection',
	props: {
		stats: {
			type: Object,
			default: () => ({
				title: '今日生产数据',
				items: [
					{ number: '1,245', label: '今日产量', color: '#667eea' },
					{ number: '98.7%', label: '合格率', color: '#667eea' },
					{ number: '2', label: '异常数', color: '#667eea' }
				]
			})
		}
	}
}
</script>

<style scoped>
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
	margin-bottom: 10rpx;
}

.stat-label {
	display: block;
	font-size: 24rpx;
	color: #999;
}
</style>