<template>
	<view class="attendance-main-container">
		<view class="header-section">
			<view class="header-title">考勤打卡</view>
			<button class="punch-card-btn" @click="goToPunchCard">去打卡</button>
		</view>
		<view class="function-grid">
			<view class="grid-item" @click="goToMyAttendance">
				<view class="icon">📅</view>
				<view class="text">我的考勤</view>
			</view>
			<view class="grid-item" @click="goToMySchedule">
				<view class="icon">📋</view>
				<view class="text">我的排班</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {};
	},
	methods: {
		goToPunchCard() {
			uni.navigateTo({
				url: '/pages/sch/attendance/punch-card'
			});
		},
		goToMyAttendance() {
			uni.navigateTo({
				url: '/pages/sch/attendance/my-attendance'
			});
		},
		goToMySchedule() {
			uni.navigateTo({
				url: '/pages/sch/attendance/my-schedule'
			});
		}
	}
};
</script>

<style lang="scss">
.attendance-main-container {
	padding: 20rpx;
	height: 100%;
	background-color: #f5f5f5;

	.header-section {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 20rpx 30rpx;
		background: linear-gradient(135deg, #4d7bfe, #3a5de9);
		border-radius: 20rpx;
		color: white;
		margin-bottom: 30rpx;

		.header-title {
			font-size: 36rpx;
			font-weight: bold;
		}

		.punch-card-btn {
			background-color: #ffd464;
			color: #333;
			border: none;
			padding: 10rpx 25rpx;
			border-radius: 50rpx;
			font-size: 28rpx;
			font-weight: bold;
		}
	}

	.function-grid {
		display: flex;
		justify-content: space-between;

		.grid-item {
			width: 48%;
			background-color: white;
			border-radius: 20rpx;
			padding: 40rpx 20rpx;
			text-align: center;
			box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);

			.icon {
				font-size: 60rpx;
				margin-bottom: 20rpx;
			}

			.text {
				font-size: 30rpx;
				color: #333;
			}
		}
	}
}
</style>