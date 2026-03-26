<template>
	<view class="attendance-main-container">
		<!-- 状态栏占位 -->
		<view class="status-bar-placeholder"></view>
		<!-- 顶部状态栏 -->
		<view class="status-bar">
			<view class="current-time">
				<view class="time">{{ currentTime }}</view>
				<view class="date">{{ currentDate }}</view>
			</view>
			<view class="attendance-status" :class="attendanceStatus">
				{{ statusText }}
			</view>
		</view>

		<!-- 今日考勤统计 -->
		<view class="stats-card">
			<view class="stats-header">
				<text class="stats-title">今日考勤</text>
				<text class="stats-subtitle">{{ todayDate }}</text>
			</view>
			<view class="stats-content">
				<view class="stat-item">
					<view class="stat-value">{{ workHours }}</view>
					<view class="stat-label">工作时长</view>
				</view>
				<view class="stat-item">
					<view class="stat-value">{{ punchCount }}</view>
					<view class="stat-label">打卡次数</view>
				</view>
				<view class="stat-item">
					<view class="stat-value" :class="{ 'late': isLate }">
						{{ attendanceStatus === 'normal' ? '正常' : '异常' }}
					</view>
					<view class="stat-label">考勤状态</view>
				</view>
			</view>
		</view>

		<!-- 快速打卡按钮 -->
		<view class="quick-punch-section">
			<button class="quick-punch-btn" @click="goToPunchCard" :class="{ 'punch-disabled': !canPunch }">
				<view class="punch-icon">🕒</view>
				<view class="punch-text">{{ punchButtonText }}</view>
				<view class="punch-time" v-if="nextPunchTime">下次打卡: {{ nextPunchTime }}</view>
			</button>
		</view>

		<!-- 功能网格 -->
		<view class="function-section">
			<view class="section-title">考勤功能</view>
			<view class="function-grid">
				<view class="grid-item" @click="goToMyAttendance">
					<view class="icon-bg" style="background-color: #4d7bfe;">
						<view class="icon">📅</view>
					</view>
					<view class="text">我的考勤</view>
					<view class="subtext">查看考勤记录</view>
				</view>
				<view class="grid-item" @click="goToMySchedule">
					<view class="icon-bg" style="background-color: #ff9500;">
						<view class="icon">📋</view>
					</view>
					<view class="text">我的排班</view>
					<view class="subtext">查看工作安排</view>
				</view>
				<view class="grid-item" @click="goToAttendanceReport">
					<view class="icon-bg" style="background-color: #34c759;">
						<view class="icon">📊</view>
					</view>
					<view class="text">考勤报表</view>
					<view class="subtext">月度统计分析</view>
				</view>
				<view class="grid-item" @click="goToApplyLeave">
					<view class="icon-bg" style="background-color: #ff3b30;">
						<view class="icon">📝</view>
					</view>
					<view class="text">请假申请</view>
					<view class="subtext">提交请假申请</view>
				</view>
			</view>
		</view>

		<!-- 快捷操作 -->
		<view class="quick-actions">
			<view class="section-title">快捷操作</view>
			<view class="action-buttons">
				<button class="action-btn" @click="goToLocationCheck">
					<view class="action-icon">📍</view>
					<view class="action-text">位置校验</view>
				</button>
				<button class="action-btn" @click="goToAttendanceRules">
					<view class="action-icon">📖</view>
					<view class="action-text">考勤规则</view>
				</button>
				<button class="action-btn" @click="goToSettings">
					<view class="action-icon">⚙️</view>
					<view class="action-text">设置</view>
				</button>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { getTodayAttendanceStatus, getTodaySchedule } from '@/api/sch/attendance';

// 响应式数据
const currentTime = ref('00:00');
const currentDate = ref('2026年1月8日');
const todayDate = ref('1月8日 星期三');
const attendanceStatus = ref('normal'); // normal, late, absent, leave
const workHours = ref('0.0h');
const punchCount = ref(0);
const isLate = ref(false);
const canPunch = ref(true);
const nextPunchTime = ref('');
const punchButtonText = ref('立即打卡');
const timer = ref(null);

// 计算属性
const statusText = computed(() => {
	const statusMap = {
		normal: '正常',
		late: '迟到',
		early_leave:'早退',
		late_early_leave:'迟到早退',
		absent: '缺勤',
		leave: '请假'
	};
	return statusMap[attendanceStatus.value] || '未知';
});

// 方法
const updateTime = () => {
	const now = new Date();
	currentTime.value = now.getHours().toString().padStart(2, '0') + ':' +
					   now.getMinutes().toString().padStart(2, '0');
	currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`;
	todayDate.value = `${now.getMonth() + 1}月${now.getDate()}日 ${getWeekday(now)}`;
};

const getWeekday = (date) => {
	const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
	return weekdays[date.getDay()];
};

const fetchAttendanceData = async () => {
	try {
		// 获取今日考勤数据
		const response = await getTodayAttendanceStatus();
		const data = response.data || {};
		
		// 更新考勤统计数据
		workHours.value = data.workHours || '0.0h';
		punchCount.value = data.punchCount || 0;
		attendanceStatus.value = data.attendanceStatus || 'absent';
		isLate.value = data.isLate || false;
		canPunch.value = data.canPunch !== undefined ? data.canPunch : true;
		nextPunchTime.value = data.nextPunchTime || '';
		
		// 根据考勤状态更新按钮文本
		if (canPunch.value) {
			punchButtonText.value = '立即打卡';
		} else {
			punchButtonText.value = '暂不可打';
		}
	} catch (error) {
		console.error('获取考勤数据失败:', error);
		uni.showToast({
			title: '获取考勤数据失败',
			icon: 'none',
			duration: 2000
		});
	}
};

const goToPunchCard = () => {
	if (!canPunch.value) {
		uni.showToast({
			title: '当前不可打卡',
			icon: 'none'
		});
		return;
	}
	uni.navigateTo({
		url: '/pages/sch/attendance/punch-card'
	});
};

const goToMyAttendance = () => {
	uni.navigateTo({
		url: '/pages/sch/attendance/my-attendance'
	});
};

const goToMySchedule = () => {
	uni.navigateTo({
		url: '/pages/sch/attendance/my-schedule'
	});
};

const goToAttendanceReport = () => {
	uni.showToast({
		title: '功能开发中',
		icon: 'none'
	});
};

const goToApplyLeave = () => {
	uni.showToast({
		title: '功能开发中',
		icon: 'none'
	});
};

const goToLocationCheck = () => {
	uni.showToast({
		title: '位置校验中...',
		icon: 'none'
	});
};

const goToAttendanceRules = () => {
	uni.showToast({
		title: '查看考勤规则',
		icon: 'none'
	});
};

const goToSettings = () => {
	uni.showToast({
		title: '打开设置',
		icon: 'none'
	});
};

// 生命周期
onMounted(async () => {
	updateTime();
	timer.value = setInterval(() => {
		updateTime();
	}, 60000); // 每分钟更新一次时间
	
	// 获取考勤数据
	await fetchAttendanceData();
	
	// 如果需要同时获取排班信息，可以额外调用
	try {
		const scheduleResponse = await getTodaySchedule();
		// 如果需要处理排班信息，可以在这里处理
		// const scheduleData = scheduleResponse.data;
	} catch (error) {
		console.error('获取今日排班失败:', error);
	}
});

onUnmounted(() => {
	if (timer.value) {
		clearInterval(timer.value);
	}
});
</script>

<style lang="scss">
.attendance-main-container {
	padding: 0 20rpx 20rpx;
	height: 100%;
	background: linear-gradient(180deg, #f5f7fa 0%, #e4e8f0 100%);
	min-height: 100vh;

	// 状态栏占位
	.status-bar-placeholder {
		height: var(--status-bar-height);
	}

	// 状态栏
	.status-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 30rpx;
		background: white;
		border-radius: 20rpx;
		margin-bottom: 30rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

		.current-time {
			.time {
				font-size: 48rpx;
				font-weight: bold;
				color: #333;
				line-height: 1.2;
			}
			.date {
				font-size: 24rpx;
				color: #666;
				margin-top: 8rpx;
			}
		}

		.attendance-status {
			padding: 12rpx 24rpx;
			border-radius: 50rpx;
			font-size: 24rpx;
			font-weight: 500;

			&.normal {
				background-color: #e8f5e9;
				color: #34c759;
			}
			&.late {
				background-color: #fff3e0;
				color: #ff9500;
			}
			&.absent {
				background-color: #ffebee;
				color: #ff3b30;
			}
			&.leave {
				background-color: #e3f2fd;
				color: #4d7bfe;
			}
		}
	}

	// 统计卡片
	.stats-card {
		background: white;
		border-radius: 20rpx;
		padding: 30rpx;
		margin-bottom: 30rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

		.stats-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			margin-bottom: 30rpx;

			.stats-title {
				font-size: 32rpx;
				font-weight: bold;
				color: #333;
			}

			.stats-subtitle {
				font-size: 24rpx;
				color: #999;
			}
		}

		.stats-content {
			display: flex;
			justify-content: space-between;

			.stat-item {
				flex: 1;
				text-align: center;

				.stat-value {
					font-size: 36rpx;
					font-weight: bold;
					color: #333;
					margin-bottom: 8rpx;

					&.late {
						color: #ff9500;
					}
				}

				.stat-label {
					font-size: 24rpx;
					color: #666;
				}
			}
		}
	}

	// 快速打卡
	.quick-punch-section {
		margin-bottom: 30rpx;

		.quick-punch-btn {
			background: linear-gradient(135deg, #4d7bfe, #3a5de9);
			border: none;
			border-radius: 20rpx;
			padding: 40rpx 30rpx;
			color: white;
			display: flex;
			flex-direction: column;
			align-items: center;
			justify-content: center;
			width: 100%;
			position: relative;
			overflow: hidden;
			box-shadow: 0 8rpx 25rpx rgba(77, 123, 254, 0.3);
			transition: all 0.3s ease;

			&:active {
				transform: scale(0.98);
				box-shadow: 0 4rpx 15rpx rgba(77, 123, 254, 0.3);
			}

			&.punch-disabled {
				background: linear-gradient(135deg, #cccccc, #aaaaaa);
				box-shadow: 0 8rpx 25rpx rgba(0, 0, 0, 0.1);
			}

			.punch-icon {
				font-size: 60rpx;
				margin-bottom: 20rpx;
			}

			.punch-text {
				font-size: 36rpx;
				font-weight: bold;
				margin-bottom: 10rpx;
			}

			.punch-time {
				font-size: 24rpx;
				opacity: 0.9;
			}
		}
	}

	// 功能区域标题
	.section-title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 30rpx;
		padding-left: 10rpx;
		position: relative;

		&::before {
			content: '';
			position: absolute;
			left: 0;
			top: 50%;
			transform: translateY(-50%);
			width: 6rpx;
			height: 30rpx;
			background: #4d7bfe;
			border-radius: 3rpx;
		}
	}

	// 功能网格
	.function-section {
		background: white;
		border-radius: 20rpx;
		padding: 30rpx;
		margin-bottom: 30rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

		.function-grid {
			display: grid;
			grid-template-columns: repeat(2, 1fr);
			gap: 30rpx;

			.grid-item {
				background: #f8f9fa;
				border-radius: 16rpx;
				padding: 30rpx;
				text-align: center;
				transition: all 0.3s ease;
				border: 2rpx solid transparent;

				&:active {
					transform: translateY(-4rpx);
					box-shadow: 0 8rpx 25rpx rgba(0, 0, 0, 0.1);
					border-color: #4d7bfe;
				}

				.icon-bg {
					width: 80rpx;
					height: 80rpx;
					border-radius: 50%;
					display: flex;
					align-items: center;
					justify-content: center;
					margin: 0 auto 20rpx;

					.icon {
						font-size: 40rpx;
					}
				}

				.text {
					font-size: 28rpx;
					font-weight: 600;
					color: #333;
					margin-bottom: 8rpx;
				}

				.subtext {
					font-size: 22rpx;
					color: #999;
				}
			}
		}
	}

	// 快捷操作
	.quick-actions {
		background: white;
		border-radius: 20rpx;
		padding: 30rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

		.action-buttons {
			display: flex;
			justify-content: space-between;

			.action-btn {
				flex: 1;
				background: #f8f9fa;
				border: none;
				border-radius: 16rpx;
				padding: 30rpx 20rpx;
				margin: 0 10rpx;
				display: flex;
				flex-direction: column;
				align-items: center;
				justify-content: center;
				transition: all 0.3s ease;

				&:first-child {
					margin-left: 0;
				}
				&:last-child {
					margin-right: 0;
				}

				&:active {
					background: #e9ecef;
					transform: translateY(-2rpx);
				}

				.action-icon {
					font-size: 40rpx;
					margin-bottom: 15rpx;
				}

				.action-text {
					font-size: 24rpx;
					color: #333;
					font-weight: 500;
				}
			}
		}
	}
}
</style>