<template>
	<view class="manufacturing-section">
		<text class="section-title">智能制造</text>
		<view v-if="loading" class="loading-container">
			<text>加载中...</text>
		</view>
		<view v-else class="manufacturing-grid">
			<view 
				v-for="item in filteredItems" 
				:key="item.id" 
				class="manufacturing-item" 
				@click="onItemClick(item)"
			>
				<view class="item-icon" :class="item.color || getDynamicColor(item.id)">
					<text class="iconfont" :class="`icon-${item.icon || getDynamicIcon(item.id)}`"></text>
				</view>
				<view class="item-content">
					<text class="item-title">{{ item.title }}</text>
					<text class="item-desc">{{ item.desc }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { useUserStore } from '@/store/modules/user'

export default {
	name: 'ManufacturingSection',
	setup() {
		const userStore = useUserStore()
		return {
			userStore
		}
	},
	data() {
		return {
			loading: false,
			availableItems: [
				{ id: 'equipment', title: '设备管理', desc: '监控设备状态，维护记录', icon: 'shebei', color: 'equipment-bg', permission: 'equipment:view' },
				{ id: 'quality', title: '质量管理', desc: '产品质量检测与追溯', icon: 'zhiliang', color: 'quality-bg', permission: 'quality:view' },
				{ id: 'process', title: '工艺管理', desc: '生产工艺流程管控', icon: 'gongyi', color: 'process-bg', permission: 'process:view' },
				{ id: 'workshop', title: '车间管理', desc: '车间作业调度与跟踪', icon: 'cheshjian', color: 'workshop-bg', permission: 'workshop:view' }
			]
		}
	},
	props: {
		items: {
			type: Array,
			default: () => []
		}
	},
	computed: {
		filteredItems() {
			if (this.items && this.items.length > 0) {
				// 如果传入了items参数，使用传入的items
				return this.items
			} else {
				// 否则使用默认的items，但只显示有权限的
				// 暂时返回全部，待后端接口完成后启用权限控制
				return this.availableItems
				// return this.availableItems.filter(item => this.hasPermission(item.permission))
			}
		}
	},
	methods: {
		hasPermission(permission) {
			// 检查用户是否有指定权限
			// 暂时返回true，待后端接口完成后实现真实权限检查
			return true;
			// return this.userStore.permissions.includes(permission);
		},
		getDynamicIcon(itemId) {
			// 根据itemId返回默认图标
			const iconMap = {
				equipment: 'shebei',
				quality: 'zhiliang',
				process: 'gongyi',
				workshop: 'cheshjian'
			};
			return iconMap[itemId] || 'more';
		},
		getDynamicColor(itemId) {
			// 根据itemId返回默认颜色
			const colorMap = {
				equipment: 'red-bg',
				quality: 'teal-bg',
				process: 'lime-bg',
				workshop: 'cyan-bg'
			};
			return colorMap[itemId] || 'default-bg';
		},

		onItemClick(item) {
			this.$emit('item-click', item)
		}
	}
}
</script>

<style scoped>
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

/* 新增更多颜色选项 */

.process-bg {
	background: linear-gradient(135deg, #f6d365, #fda085);
}

.workshop-bg {
	background: linear-gradient(135deg, #84fab0, #8fd3f4);
}

.blue-bg {
	background: linear-gradient(135deg, #4facfe, #00f2fe);
}

.green-bg {
	background: linear-gradient(135deg, #42e695, #3bb2b8);
}

.orange-bg {
	background: linear-gradient(135deg, #ff9a9e, #fecfef);
}

.purple-bg {
	background: linear-gradient(135deg, #a6c0fe, #f68084);
}

.red-bg {
	background: linear-gradient(135deg, #ff6b6b, #ffa500);
}

.yellow-bg {
	background: linear-gradient(135deg, #ffd26f, #ffb952);
}

.teal-bg {
	background: linear-gradient(135deg, #5ee7df, #b490ca);
}

.lime-bg {
	background: linear-gradient(135deg, #d299c2, #fef9d7);
}

.cyan-bg {
	background: linear-gradient(135deg, #667eea, #764ba2);
}

.pink-bg {
	background: linear-gradient(135deg, #f093fb, #f5576c);
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

.loading-container {
	padding: 40rpx;
	text-align: center;
	color: #999;
}

.process-bg {
	background: linear-gradient(135deg, #f6d365, #fda085);
}

.workshop-bg {
	background: linear-gradient(135deg, #84fab0, #8fd3f4);
}
</style>