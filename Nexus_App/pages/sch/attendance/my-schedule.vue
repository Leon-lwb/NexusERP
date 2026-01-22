<template>
	<view class="my-schedule-container" @touchstart="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd">
		<view class="schedule-header">
			<text>我的排班</text>
			<text class="total-count" v-if="totalCount > 0">共{{totalCount}}条</text>
		</view>
		
		<!-- 下拉刷新提示 -->
		<view v-if="pullDownRefresh" class="pull-down-refresh">
			<text>松开刷新显示历史排班</text>
		</view>
		
		<!-- 加载状态 -->
		<view v-if="loading" class="loading-container">
			<text>加载中...</text>
		</view>
		
		<!-- 空状态 -->
		<view v-else-if="displayList.length === 0" class="empty-container">
			<text>暂无排班数据</text>
		</view>
		
		<!-- 排班列表 -->
		<view v-else class="schedule-list">
			<view 
				v-for="schedule in displayList" 
				:key="schedule.scheduleId" 
				class="schedule-item"
				:class="{ 'today': isToday(schedule.scheduledDate) }"
			>
				<view class="date-info">
					<text class="date">{{ formatDate(schedule.scheduledDate) }}</text>
					<text class="weekday">{{ getWeekday(schedule.scheduledDate) }}</text>
				</view>
				<view class="shift-info">
					<text v-if="schedule.isWorkingDay">
						{{ schedule.shiftName }} {{ formatTime(schedule.startTime) }}-{{ formatTime(schedule.endTime) }}
					</text>
					<text v-else style="color: #999;">休息</text>
				</view>
				<view class="status-badge" :class="getStatusClass(schedule.scheduleStatus)">
					{{ getStatusText(schedule.scheduleStatus) }}
				</view>
			</view>
		</view>
		
		<!-- 分页加载更多 -->
		<view v-if="hasMore && !loading" class="load-more" @click="loadMore">
			<text>加载更多</text>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { getMySchedule } from "@/api/sch/attendance";

// 响应式数据
const scheduleList = ref([]);
const totalCount = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(20);
const hasMore = ref(true);
const showHistory = ref(false);
const pullDownRefresh = ref(false);
const startY = ref(0);
const currentDate = ref(new Date().setHours(0, 0, 0, 0));

// 计算当前及未来的排班列表
const displayList = computed(() => {
	if (showHistory.value) {
		// 显示所有排班
		return scheduleList.value;
	} else {
		// 只显示当前及未来的排班
		return scheduleList.value.filter(item => {
			const scheduledDate = new Date(item.scheduledDate).setHours(0, 0, 0, 0);
			return scheduledDate >= currentDate.value;
		});
	}
});

// 页面加载时获取数据
onMounted(() => {
	loadScheduleList();
});

// 加载排班列表
async function loadScheduleList() {
	loading.value = true;
	try {
		const response = await getMySchedule({
			pageNum: pageNum.value,
			pageSize: pageSize.value
		});
		
		// 处理响应数据
		if (response.code === 200) {
			const data = response.data || {};
			
			if (pageNum.value === 1) {
				// 第一页，重置数据
				scheduleList.value = data.rows || [];
			} else {
				// 追加数据
				scheduleList.value = [...scheduleList.value, ...(data.rows || [])];
			}
			
			totalCount.value = data.total || 0;
			hasMore.value = scheduleList.value.length < totalCount.value;
		} else {
			uni.showToast({
				title: response.msg || '获取排班列表失败',
				icon: 'none'
			});
		}
	} catch (error) {
		console.error('获取排班列表失败', error);
		uni.showToast({
			title: '网络错误，请重试',
			icon: 'none'
		});
	} finally {
		loading.value = false;
		pullDownRefresh.value = false;
	}
}

// 加载更多
async function loadMore() {
	if (loading.value || !hasMore.value) return;
	
	pageNum.value++;
	await loadScheduleList();
}

// 下拉刷新开始
function onTouchStart(e) {
	startY.value = e.touches[0].clientY;
}

// 下拉刷新中
function onTouchMove(e) {
	const touchY = e.touches[0].clientY;
	const scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
	
	// 只有当页面滚动到顶部时才触发下拉刷新
	if (scrollTop === 0 && touchY - startY.value > 50) {
		pullDownRefresh.value = true;
	}
}

// 下拉刷新结束
async function onTouchEnd() {
	if (pullDownRefresh.value) {
		// 显示历史排班
		showHistory.value = true;
		// 重新加载数据，确保获取完整的排班列表
		pageNum.value = 1;
		await loadScheduleList();
	}
}

// 判断是否是今天
function isToday(dateString) {
	const today = new Date();
	const date = new Date(dateString);
	return today.getFullYear() === date.getFullYear() &&
		today.getMonth() === date.getMonth() &&
		today.getDate() === date.getDate();
}

// 格式化日期
function formatDate(dateString) {
	const date = new Date(dateString);
	const month = (date.getMonth() + 1).toString().padStart(2, '0');
	const day = date.getDate().toString().padStart(2, '0');
	return `${month}月${day}日`;
}

// 获取星期
function getWeekday(dateString) {
	const date = new Date(dateString);
	const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
	return weekdays[date.getDay()];
}

// 格式化时间
function formatTime(dateTimeString) {
	if (!dateTimeString) return '';
	const date = new Date(dateTimeString);
	const hours = date.getHours().toString().padStart(2, '0');
	const minutes = date.getMinutes().toString().padStart(2, '0');
	return `${hours}:${minutes}`;
}

// 获取状态类名
function getStatusClass(status) {
	const statusMap = {
		'normal': 'status-normal',
		'swapped': 'status-swapped',
		'leave': 'status-leave',
		'absent': 'status-absent',
		'rest': 'status-rest'
	};
	return statusMap[status] || 'status-normal';
}

// 获取状态文本
function getStatusText(status) {
	const statusTextMap = {
		'normal': '正常',
		'swapped': '调班',
		'leave': '请假',
		'absent': '缺勤',
		'rest': '休息'
	};
	return statusTextMap[status] || '正常';
}
</script>

<style lang="scss">
.my-schedule-container {
	padding: 30rpx;
	height: 100%;
	background-color: #f5f5f5;
	
	.pull-down-refresh {
		text-align: center;
		padding: 20rpx;
		color: #4d7bfe;
		font-size: 28rpx;
		background-color: white;
		border-radius: 20rpx;
		margin-bottom: 20rpx;
		animation: bounce 0.5s ease-in-out;
	}
	
	@keyframes bounce {
		0% {
			transform: translateY(-10rpx);
			opacity: 0;
		}
		50% {
			transform: translateY(0);
			opacity: 1;
		}
		100% {
			transform: translateY(0);
			opacity: 1;
		}
	}
	
	.schedule-header {
		text-align: center;
		font-size: 36rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 30rpx;
		position: relative;
		
		.total-count {
			position: absolute;
			right: 0;
			top: 50%;
			transform: translateY(-50%);
			font-size: 24rpx;
			color: #666;
			font-weight: normal;
		}
	}
	
	.loading-container,
	.empty-container {
		text-align: center;
		padding: 100rpx 0;
		color: #999;
		font-size: 28rpx;
	}
	
	.schedule-list {
		.schedule-item {
			background-color: white;
			border-radius: 20rpx;
			padding: 30rpx;
			margin-bottom: 20rpx;
			box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
			display: flex;
			justify-content: space-between;
			align-items: center;
			position: relative;
			
			&.today {
				border-left: 8rpx solid #4d7bfe;
				background-color: #f0f4ff;
			}
			
			.date-info {
				min-width: 140rpx;
				
				.date {
					display: block;
					font-size: 32rpx;
					font-weight: bold;
					color: #333;
				}
				
				.weekday {
					display: block;
					font-size: 24rpx;
					color: #666;
				}
			}
			
			.shift-info {
				flex: 1;
				text-align: center;
				font-size: 28rpx;
				color: #333;
				padding: 0 20rpx;
			}
			
			.status-badge {
				padding: 6rpx 16rpx;
				border-radius: 20rpx;
				font-size: 22rpx;
				min-width: 60rpx;
				text-align: center;
				
				&.status-normal {
					background-color: #e8f5e9;
					color: #2e7d32;
				}
				
				&.status-swapped {
					background-color: #fff3e0;
					color: #f57c00;
				}
				
				&.status-leave {
					background-color: #e3f2fd;
					color: #1976d2;
				}
				
				&.status-absent {
					background-color: #ffebee;
					color: #c62828;
				}
				
				&.status-rest {
					background-color: #f5f5f5;
					color: #757575;
				}
			}
		}
	}
	
	.load-more {
		text-align: center;
		padding: 30rpx;
		color: #4d7bfe;
		font-size: 28rpx;
		background-color: white;
		border-radius: 20rpx;
		margin-top: 20rpx;
		
		&:active {
			background-color: #f0f4ff;
		}
	}
}
</style>