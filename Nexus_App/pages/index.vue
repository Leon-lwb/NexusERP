<template>
	<view class="container">
		<TopBar :current-date="currentDate" />
		<LogoSection :title="systemTitle" :subtitle="systemSubtitle" />
		<QuickActions :actions="quickActions" @action-click="handleActionClick" />
		<ManufacturingSection :items="manufacturingItems" @item-click="handleItemClick" />
		<StatsSection :stats="statsData" />
	</view>
</template>

<script>
import TopBar from './components/TopBar.vue'
import LogoSection from './components/LogoSection.vue'
import QuickActions from './components/QuickActions.vue'
import ManufacturingSection from './components/ManufacturingSection.vue'
import StatsSection from './components/StatsSection.vue'

export default {
	components: {
		TopBar,
		LogoSection,
		QuickActions,
		ManufacturingSection,
		StatsSection
	},
	data() {
		return {
			currentDate: '',

			systemTitle: 'Nexus智能ERP系统',
			systemSubtitle: '智能制造 · 数字化管理',
			quickActions: [
				{ id: 'purchase', label: '采购管理', icon: 'caigou', color: 'blue-bg', permission: 'purchase:view' },
				{ id: 'sales', label: '销售管理', icon: 'xiaoshou', color: 'green-bg', permission: 'sales:view' },
				{ id: 'inventory', label: '库存管理', icon: 'kucun', color: 'orange-bg', permission: 'inventory:view' },
				{ id: 'production', label: '生产管理', icon: 'shengchan', color: 'purple-bg', permission: 'production:view' }
			],
			manufacturingItems: [
				{
					id: 'equipment',
					title: '设备管理',
					desc: '监控设备状态，维护记录',
					icon: 'shebei',
					color: 'red-bg',
					permission: 'equipment:view'
				},
				{
					id: 'quality',
					title: '质量管理',
					desc: '产品质量检测与追溯',
					icon: 'zhiliang',
					color: 'teal-bg',
					permission: 'quality:view'
				}
			],
			statsData: {
				title: '今日生产数据',
				items: [
					{ number: '1,245', label: '今日产量', color: '#667eea' },
					{ number: '98.7%', label: '合格率', color: '#667eea' },
					{ number: '2', label: '异常数', color: '#667eea' }
				]
			}
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
		handleActionClick(action) {
			this.goToPage(action.label, action.id)
		},
		handleItemClick(item) {
			this.goToPage(item.title, item.id)
		},
		goToPage(title, pageId) {
			uni.showToast({
				title: `跳转到${title}`,
				icon: 'none'
			});
			// 实际项目中可以跳转到相应页面
			// this.navigateToPage(pageId)
		},
		navigateToPage(pageId) {
			// 根据页面ID生成对应的路由路径
			const routes = {
				purchase: '/pages/purchase/index',
				sales: '/pages/sales/index',
				inventory: '/pages/inventory/index',
				production: '/pages/production/index',
				equipment: '/pages/equipment/index',
				quality: '/pages/quality/index'
			};
			
			const url = routes[pageId] || `/pages/${pageId}/index`;
			uni.navigateTo({ url });
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
</style>