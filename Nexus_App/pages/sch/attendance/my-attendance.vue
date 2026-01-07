<template>
	<view class="my-attendance-container">
		<view class="month-selector">
			<text>{{ selectedMonth }}</text>
		</view>
		
		<view class="calendar-section">
			<view class="week-header">
				<text class="week-day">日</text>
				<text class="week-day">一</text>
				<text class="week-day">二</text>
				<text class="week-day">三</text>
				<text class="week-day">四</text>
				<text class="week-day">五</text>
				<text class="week-day">六</text>
			</view>
			<view class="calendar-grid">
				<view 
					v-for="(day, index) in calendarDays" 
					:key="index" 
					class="calendar-day"
					:class="getDayClass(day)"
					@click="onDayClick(day)"
				>
					<text v-if="day.date">{{ day.date.getDate() }}</text>
				</view>
			</view>
		</view>
		
		<view class="attendance-summary">
			<view class="summary-item">
				<text class="label">本月出勤</text>
				<text class="value">{{ attendanceDays }}天</text>
			</view>
			<view class="summary-item">
				<text class="label">迟到次数</text>
				<text class="value">{{ lateCount }}次</text>
			</view>
			<view class="summary-item">
				<text class="label">加班时长</text>
				<text class="value">{{ overtimeHours }}小时</text>
			</view>
		</view>
	</view>
</template>

<script>
import { getMonthlyAttendance } from "@/api/sch/attendance";

export default {
	data() {
		return {
			selectedMonth: '',
			calendarDays: [],
			attendanceData: {},
			attendanceDays: 0,
			lateCount: 0,
			overtimeHours: 0
		};
	},
	onLoad() {
		this.initCalendar();
	},
	methods: {
		initCalendar() {
			const now = new Date();
			this.selectedMonth = `${now.getFullYear()}年${now.getMonth() + 1}月`;
			this.generateCalendar(now.getFullYear(), now.getMonth());
			this.loadAttendanceData(now.getFullYear(), now.getMonth() + 1);
		},
		generateCalendar(year, month) {
			// 获取当月第一天和最后一天
			const firstDay = new Date(year, month, 1);
			const lastDay = new Date(year, month + 1, 0);
			
			// 获取第一天是星期几
			const startDay = firstDay.getDay();
			
			// 生成日历数组
			this.calendarDays = [];
			
			// 添加上个月的日期
			const prevMonthLastDay = new Date(year, month, 0).getDate();
			for (let i = startDay - 1; i >= 0; i--) {
				const date = new Date(year, month - 1, prevMonthLastDay - i);
				this.calendarDays.push({ date, isCurrentMonth: false });
			}
			
			// 添加当月日期
			for (let i = 1; i <= lastDay.getDate(); i++) {
				const date = new Date(year, month, i);
				this.calendarDays.push({ date, isCurrentMonth: true });
			}
			
			// 添加下个月的日期
			const totalCells = 42; // 6行7列
			const remainingCells = totalCells - this.calendarDays.length;
			for (let i = 1; i <= remainingCells; i++) {
				const date = new Date(year, month + 1, i);
				this.calendarDays.push({ date, isCurrentMonth: false });
			}
		},
		async loadAttendanceData(year, month) {
			try {
				const monthStr = `${year}-${month.toString().padStart(2, '0')}`;
				const response = await getMonthlyAttendance(monthStr);
				
				// 处理考勤数据
				this.attendanceData = response.data.attendanceDays || {};
				this.attendanceDays = response.data.attendanceDaysCount || 0;
				this.lateCount = response.data.lateCount || 0;
				this.overtimeHours = response.data.overtimeHours || 0;
			} catch (error) {
				console.error('获取考勤数据失败', error);
				uni.showToast({
					title: '获取考勤数据失败',
					icon: 'none'
				});
			}
		},
		getDayClass(day) {
			if (!day.date || !day.isCurrentMonth) {
				return 'empty-day';
			}
			
			const dateStr = this.formatDate(day.date);
			const attendance = this.attendanceData[dateStr];
			
			if (!attendance) {
				return 'normal';
			}
			
			// 根据考勤状态返回不同样式
			switch (attendance.attendanceStatus) {
				case 'normal':
					return 'normal';
				case 'late':
					return 'late';
				case 'early_leave':
					return 'early-leave';
				case 'absent':
					return 'absent';
				case 'leave':
					return 'leave';
				default:
					return 'normal';
			}
		},
		onDayClick(day) {
			if (!day.date || !day.isCurrentMonth) {
				return;
			}
			
			const dateStr = this.formatDate(day.date);
			const attendance = this.attendanceData[dateStr];
			
			if (attendance) {
				uni.showModal({
					title: '考勤详情',
					content: `日期：${dateStr}\n状态：${this.getStatusText(attendance.attendanceStatus)}\n上班：${attendance.clockInTime || '未打卡'}\n下班：${attendance.clockOutTime || '未打卡'}`,
					showCancel: false,
					confirmText: '确定'
				});
			} else {
				uni.showToast({
					title: '该日期无考勤记录',
					icon: 'none'
				});
			}
		},
		getStatusText(status) {
			const statusMap = {
				'normal': '正常',
				'late': '迟到',
				'early_leave': '早退',
				'absent': '旷工',
				'leave': '请假'
			};
			return statusMap[status] || status;
		},
		formatDate(date) {
			const year = date.getFullYear();
			const month = (date.getMonth() + 1).toString().padStart(2, '0');
			const day = date.getDate().toString().padStart(2, '0');
			return `${year}-${month}-${day}`;
		}
	}
};
</script>

<style lang="scss">
.my-attendance-container {
	padding: 30rpx;
	height: 100%;
	background-color: #f5f5f5;
	
	.month-selector {
		text-align: center;
		font-size: 36rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 30rpx;
	}
	
	.calendar-section {
		background-color: white;
		border-radius: 20rpx;
		padding: 20rpx;
		margin-bottom: 40rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
		
		.week-header {
			display: flex;
			justify-content: space-between;
			padding: 10rpx 0;
			border-bottom: 1rpx solid #eee;
			
			.week-day {
				flex: 1;
				text-align: center;
				font-size: 28rpx;
				color: #666;
			}
		}
		
		.calendar-grid {
			display: flex;
			flex-wrap: wrap;
			justify-content: space-between;
			
			.calendar-day {
				width: 14.28%;
				height: 80rpx;
				display: flex;
				justify-content: center;
				align-items: center;
				text-align: center;
				font-size: 28rpx;
				color: #333;
				margin: 5rpx 0;
				
				&.empty-day {
					color: #ddd;
				}
				
				&.normal {
					background-color: #e8f4ff;
					border-radius: 50%;
				}
				
				&.late {
					background-color: #ffebee;
					border-radius: 50%;
				}
				
				&.early-leave {
					background-color: #fff3e0;
					border-radius: 50%;
				}
				
				&.absent {
					background-color: #ffcdd2;
					border-radius: 50%;
				}
				
				&.leave {
					background-color: #e8f5e9;
					border-radius: 50%;
				}
			}
		}
	}
	
	.attendance-summary {
		display: flex;
		justify-content: space-around;
		background-color: white;
		border-radius: 20rpx;
		padding: 30rpx;
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08);
		
		.summary-item {
			text-align: center;
			
			.label {
				display: block;
				font-size: 26rpx;
				color: #666;
				margin-bottom: 10rpx;
			}
			
			.value {
				font-size: 32rpx;
				font-weight: bold;
				color: #333;
			}
		}
	}
}
</style>