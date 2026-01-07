<template>
	<view class="punch-card-container">
		<!-- 顶部时间显示 -->
		<view class="time-section">
			<view class="current-time">{{ currentTime }}</view>
			<view class="date-week">
				<text class="date">{{ currentDate }}</text>
				<text class="weekday">{{ currentWeekday }}</text>
			</view>
		</view>
		
		<!-- 地理位置信息 -->
		<view class="location-section" v-if="locationInfo.address">
			<view class="location-header">
				<text class="location-icon">📍</text>
				<text class="location-title">当前位置</text>
				<text class="location-status" :class="locationInfo.isValid ? 'valid' : 'invalid'">
					{{ locationInfo.isValid ? '在打卡范围内' : '不在打卡范围内' }}
				</text>
			</view>
			<view class="location-address">{{ locationInfo.address }}</view>
			<view class="location-detail">
				<text>精度: {{ locationInfo.accuracy }}米</text>
				<text class="refresh-btn" @click="refreshLocation">刷新位置</text>
			</view>
		</view>
		
		<!-- 排班信息 -->
		<view class="schedule-section" v-if="todaySchedule">
			<view class="schedule-header">
				<text class="schedule-title">今日排班</text>
				<text class="shift-name">{{ todaySchedule.shiftName || '休息' }}</text>
			</view>
			
			<view class="schedule-times" v-if="todaySchedule.shiftName">
				<view class="time-item">
					<text class="time-label">上班时间</text>
					<text class="time-value">{{ formatTime(todaySchedule.startTime) }}</text>
					<view class="countdown" v-if="!clockInStatus && countdownInfo.clockIn">
						<text class="countdown-label">距离上班</text>
						<text class="countdown-value">{{ countdownInfo.clockIn }}</text>
					</view>
				</view>
				
				<view class="time-item">
					<text class="time-label">下班时间</text>
					<text class="time-value">{{ formatTime(todaySchedule.endTime) }}</text>
					<view class="countdown" v-if="!clockOutStatus && countdownInfo.clockOut">
						<text class="countdown-label">距离下班</text>
						<text class="countdown-value">{{ countdownInfo.clockOut }}</text>
					</view>
				</view>
			</view>
		</view>
		
		<!-- 打卡区域 -->
		<view class="punch-section" v-if="todaySchedule && todaySchedule.isWorkingDay">
			<!-- 上班打卡 -->
			<view class="punch-card" :class="{ 
				'punched': clockInStatus, 
				'disabled': !locationInfo.isValid && !clockInStatus 
			}">
				<view class="punch-header">
					<text class="punch-title">上班打卡</text>
					<text class="punch-status-tag" :class="clockInStatus ? 'success' : 'pending'">
						{{ clockInStatus ? '已打卡' : '待打卡' }}
					</text>
				</view>
				
				<view class="punch-content" v-if="!clockInStatus">
					<view class="punch-btn" 
						:class="{ 'disabled': !locationInfo.isValid }"
						@click="handleClockIn">
						<text class="btn-text">上班打卡</text>
						<text class="btn-subtext" v-if="!locationInfo.isValid">(需在打卡范围内)</text>
					</view>
					<view class="punch-tip" v-if="!locationInfo.isValid">
						请移动到打卡范围内再进行打卡
					</view>
				</view>
				
				<view class="punch-content" v-else>
					<view class="punch-success">
						<text class="success-icon">✓</text>
						<text class="success-time">{{ clockInTime }}</text>
					</view>
					<view class="punch-detail" v-if="lateMinutes > 0">
						<text class="late-tag">迟到 {{ lateMinutes }} 分钟</text>
					</view>
				</view>
			</view>
			
			<!-- 分隔线 -->
			<view class="divider">
				<text>──────────</text>
			</view>
			
			<!-- 下班打卡 -->
			<view class="punch-card" :class="{ 
				'punched': clockOutStatus, 
				'disabled': !locationInfo.isValid && !clockOutStatus 
			}">
				<view class="punch-header">
					<text class="punch-title">下班打卡</text>
					<text class="punch-status-tag" :class="clockOutStatus ? 'success' : 'pending'">
						{{ clockOutStatus ? '已打卡' : '待打卡' }}
					</text>
				</view>
				
				<view class="punch-content" v-if="!clockOutStatus">
					<view class="punch-btn" 
						:class="{ 'disabled': !locationInfo.isValid }"
						@click="handleClockOut">
						<text class="btn-text">下班打卡</text>
						<text class="btn-subtext" v-if="!locationInfo.isValid">(需在打卡范围内)</text>
					</view>
					<view class="punch-tip" v-if="!locationInfo.isValid">
						请移动到打卡范围内再进行打卡
					</view>
				</view>
				
				<view class="punch-content" v-else>
					<view class="punch-success">
						<text class="success-icon">✓</text>
						<text class="success-time">{{ clockOutTime }}</text>
					</view>
					<view class="punch-detail" v-if="overtimeHours > 0">
						<text class="overtime-tag">加班 {{ overtimeHours }} 小时</text>
					</view>
				</view>
			</view>
			
			<!-- 功能预留区域 -->
			<view class="reserved-functions">
				<view class="reserved-title">其他功能（开发中）</view>
				<view class="function-buttons">
					<view class="func-btn disabled">
						<text class="func-icon">📝</text>
						<text class="func-text">补卡申请</text>
					</view>
					<view class="func-btn disabled">
						<text class="func-icon">🚗</text>
						<text class="func-text">外勤打卡</text>
					</view>
					<view class="func-btn disabled">
						<text class="func-icon">📋</text>
						<text class="func-text">请假申请</text>
					</view>
				</view>
			</view>
		</view>
		
		<!-- 休息日显示 -->
		<view class="rest-section" v-else>
			<view class="rest-content">
				<text class="rest-icon">🎉</text>
				<text class="rest-message">今天是休息日</text>
				<text class="rest-submessage">好好享受休息时光吧</text>
			</view>
		</view>
	</view>
</template>

<script>
import { getTodaySchedule, getTodayAttendanceStatus, clockIn, clockOut, validateLocation } from "@/api/sch/attendance";
import { formatDate, formatTime } from "@/utils/common";

export default {
	data() {
		return {
			currentTime: '',
			currentDate: '',
			currentWeekday: '',
			todaySchedule: null,
			clockInStatus: false,
			clockOutStatus: false,
			clockInTime: '',
			clockOutTime: '',
			lateMinutes: 0,
			overtimeHours: 0,
			locationInfo: {
				address: '',
				latitude: null,
				longitude: null,
				accuracy: 0,
				isValid: false
			},
			countdownInfo: {
				clockIn: '',
				clockOut: ''
			},
			timer: null,
			locationTimer: null
		};
	},
	onLoad() {
		this.initPage();
	},
	onShow() {
		this.getCurrentStatus();
	},
	onHide() {
		this.clearTimers();
	},
	onUnload() {
		this.clearTimers();
	},
	methods: {
		formatTime,
		clearTimers() {
			if (this.timer) {
				clearInterval(this.timer);
				this.timer = null;
			}
			if (this.locationTimer) {
				clearInterval(this.locationTimer);
				this.locationTimer = null;
			}
		},
		initPage() {
			// 设置当前时间
			this.updateCurrentTime();
			this.timer = setInterval(() => {
				this.updateCurrentTime();
				this.updateCountdown();
			}, 1000);
			
			// 设置当前日期和星期
			const now = new Date();
			this.currentDate = formatDate(now, 'yyyy年MM月dd日');
			const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
			this.currentWeekday = weekdays[now.getDay()];
			
			// 获取地理位置
			this.getLocation();
			this.locationTimer = setInterval(() => {
				this.getLocation();
			}, 30000); // 每30秒更新一次位置
			
			// 获取今日排班和打卡状态
			this.getTodaySchedule();
			this.getCurrentStatus();
		},
		updateCurrentTime() {
			const now = new Date();
			const hours = now.getHours().toString().padStart(2, '0');
			const minutes = now.getMinutes().toString().padStart(2, '0');
			const seconds = now.getSeconds().toString().padStart(2, '0');
			this.currentTime = `${hours}:${minutes}:${seconds}`;
		},
		updateCountdown() {
			if (!this.todaySchedule || !this.todaySchedule.startTime || !this.todaySchedule.endTime) {
				return;
			}
			
			const now = new Date();
			const startTime = new Date(this.todaySchedule.startTime);
			const endTime = new Date(this.todaySchedule.endTime);
			
			// 计算距离上班时间
			if (!this.clockInStatus && now < startTime) {
				const diff = startTime - now;
				const hours = Math.floor(diff / (1000 * 60 * 60));
				const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
				const seconds = Math.floor((diff % (1000 * 60)) / 1000);
				this.countdownInfo.clockIn = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
			} else {
				this.countdownInfo.clockIn = '';
			}
			
			// 计算距离下班时间
			if (!this.clockOutStatus && now < endTime) {
				const diff = endTime - now;
				const hours = Math.floor(diff / (1000 * 60 * 60));
				const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
				const seconds = Math.floor((diff % (1000 * 60)) / 1000);
				this.countdownInfo.clockOut = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
			} else {
				this.countdownInfo.clockOut = '';
			}
		},
		async getLocation() {
			try {
				// 获取地理位置
				const location = await new Promise((resolve, reject) => {
					uni.getLocation({
						type: 'wgs84',
						success: resolve,
						fail: reject
					});
				});
				
				// 获取地址信息
				const addressInfo = await new Promise((resolve, reject) => {
					uni.chooseLocation({
						latitude: location.latitude,
						longitude: location.longitude,
						success: resolve,
						fail: () => resolve({ address: '未知位置' })
					});
				});
				
				// 验证位置是否在打卡范围内
				let isValid = false;
				try {
					const validation = await validateLocation({
						latitude: location.latitude,
						longitude: location.longitude
					});
					isValid = validation.data.isValid || false;
				} catch (error) {
					console.warn('位置验证失败，使用默认值', error);
					isValid = true; // 开发阶段默认通过
				}
				
				this.locationInfo = {
					address: addressInfo.address || '未知位置',
					latitude: location.latitude,
					longitude: location.longitude,
					accuracy: location.accuracy ? Math.round(location.accuracy) : 0,
					isValid: isValid
				};
				
			} catch (error) {
				console.error('获取位置失败', error);
				this.locationInfo = {
					address: '获取位置失败，请检查定位权限',
					latitude: null,
					longitude: null,
					accuracy: 0,
					isValid: false
				};
			}
		},
		refreshLocation() {
			uni.showLoading({ title: '刷新位置中' });
			this.getLocation().finally(() => {
				uni.hideLoading();
			});
		},
		async getTodaySchedule() {
			try {
				const response = await getTodaySchedule();
				this.todaySchedule = response.data;
				this.updateCountdown();
			} catch (error) {
				console.error('获取今日排班失败', error);
				uni.showToast({
					title: '获取排班信息失败',
					icon: 'none'
				});
			}
		},
		async getCurrentStatus() {
			try {
				const response = await getTodayAttendanceStatus();
				const status = response.data;
				
				if (status.clockInTime) {
					this.clockInStatus = true;
					this.clockInTime = formatTime(new Date(status.clockInTime));
					this.lateMinutes = status.lateMinutes || 0;
				}
				
				if (status.clockOutTime) {
					this.clockOutStatus = true;
					this.clockOutTime = formatTime(new Date(status.clockOutTime));
					this.overtimeHours = status.overtimeHours || 0;
				}
			} catch (error) {
				console.error('获取打卡状态失败', error);
				uni.showToast({
					title: '获取打卡状态失败',
					icon: 'none'
				});
			}
		},
		async handleClockIn() {
			if (!this.locationInfo.isValid) {
				uni.showToast({
					title: '请在打卡范围内打卡',
					icon: 'none'
				});
				return;
			}
			
			try {
				uni.showLoading({ title: '打卡中...' });
				
				// 调用带位置的打卡接口
				await clockIn({
					latitude: this.locationInfo.latitude,
					longitude: this.locationInfo.longitude,
					address: this.locationInfo.address,
					accuracy: this.locationInfo.accuracy
				});
				
				uni.hideLoading();
				uni.showToast({
					title: '上班打卡成功',
					icon: 'success'
				});
				
				// 更新状态
				const now = new Date();
				this.clockInStatus = true;
				this.clockInTime = formatTime(now);
				
				// 重新获取状态以获取迟到信息
				this.getCurrentStatus();
			} catch (error) {
				uni.hideLoading();
				console.error('上班打卡失败', error);
				uni.showToast({
					title: error.message || '打卡失败',
					icon: 'none'
				});
			}
		},
		async handleClockOut() {
			if (!this.locationInfo.isValid) {
				uni.showToast({
					title: '请在打卡范围内打卡',
					icon: 'none'
				});
				return;
			}
			
			try {
				uni.showLoading({ title: '打卡中...' });
				
				// 调用带位置的打卡接口
				await clockOut({
					latitude: this.locationInfo.latitude,
					longitude: this.locationInfo.longitude,
					address: this.locationInfo.address,
					accuracy: this.locationInfo.accuracy
				});
				
				uni.hideLoading();
				uni.showToast({
					title: '下班打卡成功',
					icon: 'success'
				});
				
				// 更新状态
				const now = new Date();
				this.clockOutStatus = true;
				this.clockOutTime = formatTime(now);
				
				// 重新获取状态以获取加班信息
				this.getCurrentStatus();
			} catch (error) {
				uni.hideLoading();
				console.error('下班打卡失败', error);
				uni.showToast({
					title: error.message || '打卡失败',
					icon: 'none'
				});
			}
		}
	}
};
</script>

<style lang="scss">
.punch-card-container {
	padding: 30rpx;
	height: 100%;
	background: linear-gradient(to bottom, #f0f4ff, #ffffff);
	
	.time-section {
		text-align: center;
		margin-bottom: 40rpx;
		
		.current-time {
			font-size: 56rpx;
			font-weight: bold;
			color: #333;
			font-family: 'Courier New', monospace;
		}
		
		.date-week {
			margin-top: 10rpx;
			
			.date {
				font-size: 28rpx;
				color: #666;
				margin-right: 20rpx;
			}
			
			.weekday {
				font-size: 28rpx;
				color: #4d7bfe;
				font-weight: bold;
			}
		}
	}
	
	.location-section {
		background-color: white;
		border-radius: 20rpx;
		padding: 30rpx;
		margin-bottom: 40rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
		
		.location-header {
			display: flex;
			align-items: center;
			margin-bottom: 15rpx;
			
			.location-icon {
				font-size: 32rpx;
				margin-right: 10rpx;
			}
			
			.location-title {
				font-size: 30rpx;
				font-weight: bold;
				color: #333;
				flex: 1;
			}
			
			.location-status {
				font-size: 24rpx;
				padding: 6rpx 15rpx;
				border-radius: 20rpx;
				
				&.valid {
					background-color: #e8f5e9;
					color: #00c853;
				}
				
				&.invalid {
					background-color: #ffebee;
					color: #ff5722;
				}
			}
		}
		
		.location-address {
			font-size: 28rpx;
			color: #333;
			margin-bottom: 15rpx;
			line-height: 1.4;
		}
		
		.location-detail {
			display: flex;
			justify-content: space-between;
			align-items: center;
			font-size: 24rpx;
			color: #666;
			
			.refresh-btn {
				color: #4d7bfe;
				text-decoration: underline;
			}
		}
	}
	
	.schedule-section {
		background-color: white;
		border-radius: 20rpx;
		padding: 30rpx;
		margin-bottom: 40rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
		
		.schedule-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			margin-bottom: 30rpx;
			
			.schedule-title {
				font-size: 28rpx;
				color: #666;
			}
			
			.shift-name {
				font-size: 32rpx;
				font-weight: bold;
				color: #4d7bfe;
			}
		}
		
		.schedule-times {
			.time-item {
				display: flex;
				align-items: center;
				justify-content: space-between;
				margin-bottom: 25rpx;
				padding-bottom: 25rpx;
				border-bottom: 1rpx solid #f0f0f0;
				
				&:last-child {
					margin-bottom: 0;
					padding-bottom: 0;
					border-bottom: none;
				}
				
				.time-label {
					font-size: 28rpx;
					color: #666;
					width: 150rpx;
				}
				
				.time-value {
					font-size: 32rpx;
					font-weight: bold;
					color: #333;
					flex: 1;
				}
				
				.countdown {
					text-align: right;
					
					.countdown-label {
						display: block;
						font-size: 24rpx;
						color: #666;
					}
					
					.countdown-value {
						display: block;
						font-size: 28rpx;
						font-weight: bold;
						color: #ff9800;
						font-family: 'Courier New', monospace;
					}
				}
			}
		}
	}
	
	.punch-section {
		.punch-card {
			background-color: white;
			border-radius: 20rpx;
			padding: 30rpx;
			margin-bottom: 30rpx;
			box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
			transition: all 0.3s ease;
			
			&.punched {
				background-color: #f0f8ff;
				border: 2rpx solid #4d7bfe;
			}
			
			&.disabled {
				opacity: 0.7;
			}
			
			.punch-header {
				display: flex;
				justify-content: space-between;
				align-items: center;
				margin-bottom: 25rpx;
				
				.punch-title {
					font-size: 32rpx;
					font-weight: bold;
					color: #333;
				}
				
				.punch-status-tag {
					font-size: 24rpx;
					padding: 6rpx 15rpx;
					border-radius: 20rpx;
					
					&.success {
						background-color: #e8f5e9;
						color: #00c853;
					}
					
					&.pending {
						background-color: #fff3e0;
						color: #ff9800;
					}
				}
			}
			
			.punch-content {
				.punch-btn {
					width: 100%;
					height: 100rpx;
					line-height: 100rpx;
					background: linear-gradient(135deg, #4d7bfe, #3a5de9);
					color: white;
					border-radius: 50rpx;
					font-size: 36rpx;
					font-weight: bold;
					text-align: center;
					
					&.disabled {
						background: linear-gradient(135deg, #cccccc, #aaaaaa);
						opacity: 0.7;
					}
					
					.btn-text {
						display: block;
					}
					
					.btn-subtext {
						display: block;
						font-size: 24rpx;
						opacity: 0.9;
					}
				}
				
				.punch-tip {
					font-size: 24rpx;
					color: #ff5722;
					text-align: center;
					margin-top: 15rpx;
				}
				
				.punch-success {
					text-align: center;
					
					.success-icon {
						font-size: 60rpx;
						color: #00c853;
						display: block;
						margin-bottom: 10rpx;
					}
					
					.success-time {
						font-size: 36rpx;
						font-weight: bold;
						color: #333;
						display: block;
					}
				}
				
				.punch-detail {
					text-align: center;
					margin-top: 15rpx;
					
					.late-tag, .overtime-tag {
						display: inline-block;
						font-size: 26rpx;
						padding: 8rpx 20rpx;
						border-radius: 20rpx;
					}
					
					.late-tag {
						background-color: #ffebee;
						color: #ff5722;
					}
					
					.overtime-tag {
						background-color: #e3f2fd;
						color: #2196f3;
					}
				}
			}
		}
		
		.divider {
			text-align: center;
			color: #ccc;
			font-size: 24rpx;
			margin: 20rpx 0;
		}
		
		.reserved-functions {
			background-color: white;
			border-radius: 20rpx;
			padding: 30rpx;
			margin-top: 40rpx;
			box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
			opacity: 0.7;
			
			.reserved-title {
				font-size: 28rpx;
				color: #999;
				text-align: center;
				margin-bottom: 25rpx;
			}
			
			.function-buttons {
				display: flex;
				justify-content: space-around;
				
				.func-btn {
					text-align: center;
					
					&.disabled {
						opacity: 0.5;
					}
					
					.func-icon {
						display: block;
						font-size: 50rpx;
						margin-bottom: 10rpx;
					}
					
					.func-text {
						font-size: 24rpx;
						color: #666;
					}
				}
			}
		}
	}
	
	.rest-section {
		background-color: white;
		border-radius: 20rpx;
		padding: 80rpx 30rpx;
		text-align: center;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
		
		.rest-content {
			.rest-icon {
				font-size: 80rpx;
				display: block;
				margin-bottom: 20rpx;
			}
			
			.rest-message {
				font-size: 36rpx;
				font-weight: bold;
				color: #4d7bfe;
				display: block;
				margin-bottom: 10rpx;
			}
			
			.rest-submessage {
				font-size: 28rpx;
				color: #666;
				display: block;
			}
		}
	}
}
</style>