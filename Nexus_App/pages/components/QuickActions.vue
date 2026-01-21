<template>
	<view class="quick-actions">
		<text class="section-title">核心功能</text>
		<view v-if="loading" class="loading-container">
			<text>加载中...</text>
		</view>
		<view v-else class="actions-grid">
			<view 
				v-for="action in filteredActions" 
				:key="action.id" 
				class="action-item" 
				@click="onActionClick(action)"
			>
				<view class="action-icon" :class="action.color || getDynamicColor(action.id)">
					<text class="iconfont" :class="`icon-${action.icon || getDynamicIcon(action.id)}`"></text>
				</view>
				<text class="action-label">{{ action.label }}</text>
			</view>
		</view>
	</view>
</template>

<script>
import { useUserStore } from '@/store/modules/user'

export default {
	name: 'QuickActions',
	setup() {
		const userStore = useUserStore()
		return {
			userStore
		}
	},
	data() {
		return {
			loading: false,
			availableActions: [
				{ id: 'purchase', label: '采购管理', icon: 'caigou', color: 'purchase-bg', permission: 'purchase:view' },
				{ id: 'sales', label: '销售管理', icon: 'xiaoshou', color: 'sales-bg', permission: 'sales:view' },
				{ id: 'inventory', label: '库存管理', icon: 'kucun', color: 'inventory-bg', permission: 'inventory:view' },
				{ id: 'production', label: '生产管理', icon: 'shengchan', color: 'production-bg', permission: 'production:view' },
				{ id: 'finance', label: '财务管理', icon: 'caiwu', color: 'finance-bg', permission: 'finance:view' },
				{ id: 'hr', label: '人事管理', icon: 'renshi', color: 'hr-bg', permission: 'hr:view' },
				{ id: 'oa', label: '办公协同', icon: 'bangong', color: 'oa-bg', permission: 'oa:view' },
				{ id: 'report', label: '报表中心', icon: 'baobiao', color: 'report-bg', permission: 'report:view' }
			]
		}
	},
	props: {
		actions: {
			type: Array,
			default: () => []
		}
	},
	computed: {
		filteredActions() {
			if (this.actions && this.actions.length > 0) {
				// 如果传入了actions参数，使用传入的actions
				return this.actions
			} else {
				// 否则使用默认的actions，但只显示有权限的
				// 暂时返回全部，待后端接口完成后启用权限控制
				return this.availableActions
				// return this.availableActions.filter(action => this.hasPermission(action.permission))
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
		getDynamicIcon(actionId) {
			// 根据actionId返回默认图标
			const iconMap = {
				purchase: 'caigou',
				sales: 'xiaoshou',
				inventory: 'kucun',
				production: 'shengchan',
				finance: 'caiwu',
				hr: 'renshi',
				oa: 'bangong',
				report: 'baobiao'
			};
			return iconMap[actionId] || 'more';
		},
		getDynamicColor(actionId) {
			// 根据actionId返回默认颜色
			const colorMap = {
				purchase: 'blue-bg',
				sales: 'green-bg',
				inventory: 'orange-bg',
				production: 'purple-bg',
				finance: 'cyan-bg',
				hr: 'pink-bg',
				oa: 'teal-bg',
				report: 'yellow-bg'
			};
			return colorMap[actionId] || 'default-bg';
		},

		onActionClick(action) {
			this.$emit('action-click', action)
		}
	}
}
</script>

<style scoped>
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

/* 新增更多颜色选项 */

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

.action-label {
	font-size: 24rpx;
	color: #666;
}

.loading-container {
	padding: 40rpx;
	text-align: center;
	color: #999;
}

.finance-bg {
	background: linear-gradient(135deg, #a1c4fd, #c2e9fb);
}

.hr-bg {
	background: linear-gradient(135deg, #d4fc79, #96e6a1);
}

.oa-bg {
	background: linear-gradient(135deg, #fccb90, #d57eeb);
}

.report-bg {
	background: linear-gradient(135deg, #e0c3fc, #9bb5ff);
}

.default-bg {
	background: linear-gradient(135deg, #cdb4db, #ffc8dd);
}
</style>