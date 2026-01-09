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
		<view class="location-section">
			<view class="location-header">
				<text class="location-icon">📍</text>
				<text class="location-title">当前位置</text>
				<text class="location-status" :class="locationInfo.isValid ? 'valid' : 'invalid'">
					{{ locationInfo.isValid ? '在打卡范围内' : '不在打卡范围内' }}
				</text>
			</view>
			
			<!-- 位置加载状态 -->
			<view v-if="locationLoading" class="location-loading">
				<text>正在获取位置...</text>
			</view>
			
			<!-- 位置获取失败 -->
			<view v-else-if="locationError" class="location-error">
				<text class="error-message">{{ locationError }}</text>
				<view class="error-actions">
					<text class="retry-btn" @click="refreshLocation">重试</text>
					<text class="settings-btn" @click="openLocationSettings">去设置</text>
				</view>
			</view>
			
			<!-- 位置信息显示 -->
			<view v-else-if="locationInfo.address" class="location-content">
				<view class="location-address">{{ locationInfo.address }}</view>
				<view class="location-detail">
					<text>精度: {{ locationInfo.accuracy }}米</text>
					<text class="refresh-btn" @click="refreshLocation">刷新位置</text>
				</view>
			</view>
			
			<!-- 无位置信息 -->
			<view v-else class="location-empty">
				<text>未获取到位置信息</text>
				<text class="refresh-btn" @click="refreshLocation">点击获取</text>
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
						:class="{ 'disabled': !locationInfo.isValid || locationError }"
						@click="handleClockIn">
						<text class="btn-text">上班打卡</text>
						<text class="btn-subtext" v-if="!locationInfo.isValid || locationError">
							{{ locationError ? '定位失败' : '需在打卡范围内' }}
						</text>
					</view>
					<view class="punch-tip" v-if="!locationInfo.isValid && !locationError">
						请移动到打卡范围内再进行打卡
					</view>
					<view class="punch-tip error" v-if="locationError">
						{{ locationError }}
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
						:class="{ 'disabled': !locationInfo.isValid || locationError }"
						@click="handleClockOut">
						<text class="btn-text">下班打卡</text>
						<text class="btn-subtext" v-if="!locationInfo.isValid || locationError">
							{{ locationError ? '定位失败' : '需在打卡范围内' }}
						</text>
					</view>
					<view class="punch-tip" v-if="!locationInfo.isValid && !locationError">
						请移动到打卡范围内再进行打卡
					</view>
					<view class="punch-tip error" v-if="locationError">
						{{ locationError }}
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

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getTodaySchedule, getTodayAttendanceStatus, clockIn, clockOut, validateLocation } from "@/api/sch/attendance";
import { formatDate, formatTime } from "@/utils/common";
import { getCurrentLocation, checkLocationPermission, requestLocationPermission } from "@/utils/location";

// uni-app 页面级别的生命周期
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';

// 响应式数据
const currentTime = ref('');
const currentDate = ref('');
const currentWeekday = ref('');
const todaySchedule = ref(null);
const clockInStatus = ref(false);
const clockOutStatus = ref(false);
const clockInTime = ref('');
const clockOutTime = ref('');
const lateMinutes = ref(0);
const overtimeHours = ref(0);
const locationLoading = ref(false);
const locationError = ref('');
const countdownInfo = reactive({
	clockIn: '',
	clockOut: ''
});
const timer = ref(null);
const locationTimer = ref(null);

// 使用 reactive 定义复杂对象
const locationInfo = reactive({
	address: '',
	latitude: null,
	longitude: null,
	accuracy: 0,
	isValid: false
});

// 页面生命周期
onLoad(() => {
	initPage();
});

onShow(() => {
	getCurrentStatus();
});

onHide(() => {
	clearTimers();
});

onUnload(() => {
	clearTimers();
});

// 清除定时器
function clearTimers() {
	if (timer.value) {
		clearInterval(timer.value);
		timer.value = null;
	}
	if (locationTimer.value) {
		clearInterval(locationTimer.value);
		locationTimer.value = null;
	}
}

// 初始化页面
async function initPage() {
	console.log('开始初始化页面...');
	
	// 设置当前时间
	updateCurrentTime();
	timer.value = setInterval(() => {
		updateCurrentTime();
		updateCountdown();
	}, 1000);
	
	// 设置当前日期和星期
	const now = new Date();
	currentDate.value = formatDate(now, 'yyyy年MM月dd日');
	const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
	currentWeekday.value = weekdays[now.getDay()];
	
	// 获取地理位置（不等待，避免阻塞接口调用）
	console.log('开始获取地理位置...');
	getLocation().catch(error => {
		console.error('地理位置获取失败，但不影响页面初始化:', error);
	});
	
	// 设置定时更新位置
	locationTimer.value = setInterval(() => {
		getLocation().catch(error => {
			console.error('定时更新位置失败:', error);
		});
	}, 30000); // 每30秒更新一次位置
	
	// 获取今日排班和打卡状态 - 并行执行以提高性能
	console.log('开始并行调用接口...');
	await Promise.allSettled([
		fetchTodaySchedule(),
		getCurrentStatus()
	]).then(results => {
		console.log('接口调用结果:', results);
		const scheduleResult = results[0];
		const statusResult = results[1];
		
		console.log('排班接口结果状态:', scheduleResult.status);
		console.log('状态接口结果状态:', statusResult.status);
		
		if (scheduleResult.status === 'rejected') {
			console.error('排班接口调用失败:', scheduleResult.reason);
			// 即使排班失败，也显示默认数据
			todaySchedule.value = {
				shiftName: '未知',
				isWorkingDay: true,
				startTime: new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9, 0, 0),
				endTime: new Date(now.getFullYear(), now.getMonth(), now.getDate(), 18, 0, 0)
			};
			console.log('使用默认排班数据:', todaySchedule.value);
		} else if (scheduleResult.status === 'fulfilled') {
			console.log('排班接口调用成功，数据已设置');
		}
		
		if (statusResult.status === 'rejected') {
			console.error('状态接口调用失败:', statusResult.reason);
		} else if (statusResult.status === 'fulfilled') {
			console.log('状态接口调用成功，数据已设置');
		}
	}).catch(error => {
		console.error('Promise.allSettled 执行出错:', error);
	});
	
	console.log('页面初始化完成');
}

// 更新当前时间
function updateCurrentTime() {
	const now = new Date();
	const hours = now.getHours().toString().padStart(2, '0');
	const minutes = now.getMinutes().toString().padStart(2, '0');
	const seconds = now.getSeconds().toString().padStart(2, '0');
	currentTime.value = `${hours}:${minutes}:${seconds}`;
}

// 更新倒计时
function updateCountdown() {
	if (!todaySchedule.value || !todaySchedule.value.startTime || !todaySchedule.value.endTime) {
		return;
	}
	
	const now = new Date();
	const startTime = new Date(todaySchedule.value.startTime);
	const endTime = new Date(todaySchedule.value.endTime);
	
	// 计算距离上班时间
	if (!clockInStatus.value && now < startTime) {
		const diff = startTime - now;
		const hours = Math.floor(diff / (1000 * 60 * 60));
		const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
		const seconds = Math.floor((diff % (1000 * 60)) / 1000);
		countdownInfo.clockIn = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
	} else {
		countdownInfo.clockIn = '';
	}
	
	// 计算距离下班时间
	if (!clockOutStatus.value && now < endTime) {
		const diff = endTime - now;
		const hours = Math.floor(diff / (1000 * 60 * 60));
		const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
		const seconds = Math.floor((diff % (1000 * 60)) / 1000);
		countdownInfo.clockOut = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
	} else {
		countdownInfo.clockOut = '';
	}
}

// 获取位置
async function getLocation() {
	console.log('开始执行getLocation方法...');
	locationLoading.value = true;
	locationError.value = '';
	
	try {
		console.log('检查定位权限...');
		// 检查定位权限
		const hasPermission = await checkLocationPermission();
		console.log('定位权限状态:', hasPermission);
		
		if (!hasPermission) {
			console.log('请求定位权限...');
			const granted = await requestLocationPermission();
			console.log('权限请求结果:', granted);
			
			if (!granted) {
				locationError.value = '定位权限未授权，请开启定位权限';
				locationLoading.value = false;
				console.log('定位权限未授权，设置错误信息:', locationError.value);
				return;
			}
		}
		
		console.log('开始获取当前位置...');
		// 获取当前位置
		const location = await getCurrentLocation();
		console.log('获取到位置信息:', location);
		
		// 获取地址信息（简化版，实际项目中应该使用逆地理编码API）
		const address = `纬度: ${location.latitude.toFixed(6)}, 经度: ${location.longitude.toFixed(6)}`;
		console.log('生成地址信息:', address);
		
		// 验证位置是否在打卡范围内
		let isValid = false;
		try {
			console.log('开始验证位置...');
			const validation = await validateLocation({
				latitude: location.latitude,
				longitude: location.longitude
			});
			console.log('位置验证结果:', validation);
			isValid = validation.data?.isValid || false;
		} catch (error) {
			console.warn('位置验证失败，使用默认值', error);
			isValid = true; // 开发阶段默认通过
		}
		
		console.log('位置有效性:', isValid);
		
		Object.assign(locationInfo, {
			address: address,
			latitude: location.latitude,
			longitude: location.longitude,
			accuracy: Math.round(location.accuracy || 0),
			isValid: isValid
		});
		
		console.log('位置信息设置完成:', locationInfo);
		
	} catch (error) {
		console.error('获取位置失败', error);
		console.error('错误堆栈:', error.stack);
		locationError.value = error.message || '获取位置失败，请检查定位权限';
		Object.assign(locationInfo, {
			address: locationError.value,
			latitude: null,
			longitude: null,
			accuracy: 0,
			isValid: false
		});
	} finally {
		locationLoading.value = false;
		console.log('getLocation方法执行完成，loading状态:', locationLoading.value);
	}
}

// 刷新位置
function refreshLocation() {
	uni.showLoading({ title: '刷新位置中' });
	getLocation().finally(() => {
		uni.hideLoading();
	});
}

// 打开位置设置
function openLocationSettings() {
	// 尝试打开设置页面，如果不可用则显示提示
	if (typeof uni.openSetting === 'function') {
		uni.openSetting({
			success: (res) => {
				if (res.authSetting && res.authSetting['scope.userLocation']) {
					// 用户已授权，重新获取位置
					refreshLocation();
				}
			},
			fail: () => {
				uni.showToast({
					title: '无法打开设置页面',
					icon: 'none'
				});
			}
		});
	} else {
		// 如果openSetting不可用，显示提示信息
		uni.showModal({
			title: '定位权限设置',
			content: '请在手机系统设置中开启定位权限，然后返回应用重试',
			showCancel: false,
			confirmText: '知道了'
		});
	}
}

// 获取今日排班
async function fetchTodaySchedule() {
	try {
		console.log('开始调用排班接口...');
		const response = await getTodaySchedule();
		console.log('排班接口响应:', response);
		
		// 检查响应格式
		if (response && response.code === 200 && response.data) {
			todaySchedule.value = response.data;
			console.log('排班数据:', todaySchedule.value);
			
			// 确保必要字段存在
			if (!todaySchedule.value.shiftName) {
				todaySchedule.value.shiftName = '默认班次';
			}
			if (!todaySchedule.value.startTime) {
				const now = new Date();
				todaySchedule.value.startTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9, 0, 0);
			}
			if (!todaySchedule.value.endTime) {
				const now = new Date();
				todaySchedule.value.endTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 18, 0, 0);
			}
			if (todaySchedule.value.isWorkingDay === undefined) {
				todaySchedule.value.isWorkingDay = true;
			}
		} else {
			console.warn('排班接口返回异常格式:', response);
			// 使用默认数据
			const now = new Date();
			todaySchedule.value = {
				shiftName: '未知',
				isWorkingDay: true,
				startTime: new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9, 0, 0),
				endTime: new Date(now.getFullYear(), now.getMonth(), now.getDate(), 18, 0, 0)
			};
		}
		
		updateCountdown();
		return response;
	} catch (error) {
		console.error('获取今日排班失败', error);
		console.error('错误详情:', error.response || error.message);
		uni.showToast({
			title: '获取排班信息失败',
			icon: 'none'
		});
		throw error; // 重新抛出错误以便Promise.allSettled捕获
	}
}

// 获取当前状态
async function getCurrentStatus() {
	try {
		console.log('开始调用状态接口...');
		const response = await getTodayAttendanceStatus();
		console.log('状态接口响应:', response);
		const status = response.data;
		console.log('状态数据:', status);
		
		if (status.clockInTime) {
			clockInStatus.value = true;
			clockInTime.value = formatTime(new Date(status.clockInTime));
			lateMinutes.value = status.lateMinutes || 0;
		}
		
		if (status.clockOutTime) {
			clockOutStatus.value = true;
			clockOutTime.value = formatTime(new Date(status.clockOutTime));
			overtimeHours.value = status.overtimeHours || 0;
		}
		return response;
	} catch (error) {
		console.error('获取打卡状态失败', error);
		console.error('错误详情:', error.response || error.message);
		uni.showToast({
			title: '获取打卡状态失败',
			icon: 'none'
		});
		throw error; // 重新抛出错误以便Promise.allSettled捕获
	}
}

// 处理上班打卡
async function handleClockIn() {
	if (!locationInfo.isValid || locationError.value) {
		uni.showToast({
			title: locationError.value || '请在打卡范围内打卡',
			icon: 'none'
		});
		return;
	}
	
	try {
		uni.showLoading({ title: '打卡中...' });
		
		// 调用带位置的打卡接口
		await clockIn({
			latitude: locationInfo.latitude,
			longitude: locationInfo.longitude,
			address: locationInfo.address,
			accuracy: locationInfo.accuracy
		});
		
		uni.hideLoading();
		uni.showToast({
			title: '上班打卡成功',
			icon: 'success'
		});
		
		// 更新状态
		const now = new Date();
		clockInStatus.value = true;
		clockInTime.value = formatTime(now);
		
		// 重新获取状态以获取迟到信息
		getCurrentStatus();
	} catch (error) {
		uni.hideLoading();
		console.error('上班打卡失败', error);
		uni.showToast({
			title: error.message || '打卡失败',
			icon: 'none'
		});
	}
}

// 处理下班打卡
async function handleClockOut() {
	if (!locationInfo.isValid || locationError.value) {
		uni.showToast({
			title: locationError.value || '请在打卡范围内打卡',
			icon: 'none'
		});
		return;
	}
	
	try {
		uni.showLoading({ title: '打卡中...' });
		
		// 调用带位置的打卡接口
		await clockOut({
			latitude: locationInfo.latitude,
			longitude: locationInfo.longitude,
			address: locationInfo.address,
			accuracy: locationInfo.accuracy
		});
		
		uni.hideLoading();
		uni.showToast({
			title: '下班打卡成功',
			icon: 'success'
		});
		
		// 更新状态
		const now = new Date();
		clockOutStatus.value = true;
		clockOutTime.value = formatTime(now);
		
		// 重新获取状态以获取加班信息
		getCurrentStatus();
	} catch (error) {
		uni.hideLoading();
		console.error('下班打卡失败', error);
		uni.showToast({
			title: error.message || '打卡失败',
			icon: 'none'
		});
	}
}
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
		
		.location-loading,
		.location-error,
		.location-empty {
			text-align: center;
			padding: 30rpx 0;
			color: #666;
			font-size: 28rpx;
			
			.error-message {
				color: #ff5722;
				display: block;
				margin-bottom: 20rpx;
			}
			
			.error-actions {
				display: flex;
				justify-content: center;
				gap: 40rpx;
				margin-top: 20rpx;
				
				.retry-btn,
				.settings-btn {
					padding: 10rpx 30rpx;
					border-radius: 20rpx;
					font-size: 26rpx;
				}
				
				.retry-btn {
					background-color: #4d7bfe;
					color: white;
				}
				
				.settings-btn {
					background-color: #f0f0f0;
					color: #333;
				}
			}
			
			.refresh-btn {
				color: #4d7bfe;
				text-decoration: underline;
				margin-left: 20rpx;
			}
		}
		
		.location-content {
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
					
					&.error {
						color: #ff5722;
					}
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