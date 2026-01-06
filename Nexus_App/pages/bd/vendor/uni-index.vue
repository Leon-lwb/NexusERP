<template>
	<view class="bd_vendor_container">
		<view >
		<!-- 筛选弹框 -->
		<fjj-condition ref='condition' @touchmove.stop :color="conditionColor" :list="screenList" :defaultValue="defaultValue" @result="resultConditon" />
		<!-- 供应商搜索 -->
		<uni-section title=" ">
			<uni-search-bar :focus="false" v-model="searchValue" @blur="searchBlur"
			placeholder="搜索供应商" @clear="searchClear" cancelButton="none">
			</uni-search-bar>
		</uni-section>
		</view>
		<view class="filter-bar" >
			<view class="filter-item" >
				<checkbox :checked="selectAllChecked" color="#0055ff" iconColor="#0055ff"
				 style="transform:scale(0.6)" @click="selectAll"/>
				 <button size="mini" type="default"
				 	style="color:#ff0000;background-color:#ffeded;border-color:#000000"
					@click="deleteBut" v-if="checkPermi(['bd:vendor:remove'])" >删除</button>
			</view>
			<view class="filter-item" @click="open">
				<button size="mini" type="default"
					style="color:#ffffff;background-color:#007aff;border-color:#000000">筛选</button>
			</view>
			<view class="filter-item" >
			  <button size="mini" type="default"
				style="color:#ffba00;background-color:#fff8e6;border-color:#000000" @click="editButton" v-if="checkPermi(['bd:vendor:edit'])" >编辑</button>
			</view>
		</view>
		<view class="data-list" >
			<!-- 供应商列表 -->
			<uni-section title=" "  >
				<checkbox-group @change="checkboxChange">
					  <uni-list v-for="(item, index) in vendorList" :key="item.vendorId">
							<uni-row>
								<uni-col :span="3" style="padding: 20px 10px;">
									<checkbox :ref="item.vendorId" :value="JSON.stringify(item.vendorId)" :checked="item.checked" color="#0055ff" iconColor="#0055ff" style="transform:scale(0.6)"/>
								</uni-col>
									<uni-col :span="21">
										<uni-list-item :title="item.vendorName" :note="item.createTime" showArrow
											:thumb="item.image" thumb-size="lg" clickable @click="detailEdit(item)" >
										</uni-list-item>
								</uni-col>
							</uni-row>
					  </uni-list>
			  </checkbox-group >
			</uni-section>
		</view>
		<view >
			<uni-section title=" " padding>
				<uni-pagination :total="total" :pageSize="queryParams.pageSize" :current="queryParams.pageNum" @change="pageChange"/>
			</uni-section>
		</view>
		<view >
		<uni-fab :horizontal="horizontal" :vertical="vertical"
		 :popMenu="popMenu" @fabClick="addButton" v-if="checkPermi(['bd:vendor:add'])" ></uni-fab>
		</view>
	</view>
</template>
 
<script>
	import fjjCondition from '@/components/fjj-condition/fjj-condition.vue';
	import { listVendor, delVendor} from "@/api/bd/vendor";
	import {showConfirm} from '@/utils/common';
import {getSelectDict} from "@/utils/dict";
	import {checkPermi}  from "@/utils/permission";// 权限工具类
	export default {
		components: {
			fjjCondition
		},
		data() {
        return {
						//行业字典类型筛选options
							industrySelectOptionsList:[],
							industryOptions:"bd_industry",
						//地区字典类型筛选options
							regionSelectOptionsList:[],
							regionOptions:"bd_region",
						//分类 (原材料/标准件/服务类)字典类型筛选options
							categorySelectOptionsList:[],
							categoryOptions:"bd_vendor_category",
						//合作状态 字典类型筛选options
							statusSelectOptionsList:[],
							statusOptions:"bd_vendor_status",
            /* 列表复选框 */
            selectAllChecked: false,
            checkBoxs: [],
            //供应商搜索
            searchValue: '',
            //筛选弹框
            conditionColor: '#4D7BFE',
            screenList: [],
            defaultValue: {},
            // 查询参数
            queryParams: {
                pageNum: 1, //当前页，
                pageSize: 5, //每页数量
                        vendorCode: undefined,
                        vendorName: undefined,
                        industry: undefined,
                        region: undefined,
                        status: undefined,
            },
            //列表数据
            vendorList:[],
            total: 0,  //总条数
			// current: 1,//初始化当前页为第一页
            //添加按钮
            horizontal: 'right',
            vertical: 'bottom',
            popMenu:false
        }
		},
		onShow(options) {//监听页面显示，页面每次出现在屏幕上都触发，包括从下级页面点返回露出当前页面
			this.getList();
		},
		onLoad () {
						//行业字典类型查询
						getSelectDict(this.industryOptions).then(response => {
							this.industrySelectOptionsList = response;
						});
						//地区字典类型查询
						getSelectDict(this.regionOptions).then(response => {
							this.regionSelectOptionsList = response;
						});
						//分类 (原材料/标准件/服务类)字典类型查询
						getSelectDict(this.categoryOptions).then(response => {
							this.categorySelectOptionsList = response;
						});
						//合作状态 字典类型查询
						getSelectDict(this.statusOptions).then(response => {
							this.statusSelectOptionsList = response;
						});
		//设置筛选条件
		setTimeout(() => {
			this.setScreenData();
		},500)
		},
		mounted() {
		},
		methods: {
			checkPermi,
			//打开筛选面板
			open(){
				this.$refs.condition.openPopup()
			},
			//确认筛选条件并开始搜索
			resultConditon(obj) {
				console.log(obj)
				for (var key in obj.str_result) {
					this.queryParams[key] = obj.str_result[key];
				}
				console.log(this.queryParams)
				this.getList();
			},
			//搜索框失去焦点，搜索供应商
			searchBlur(res) {
				this.queryParams.vendorId = res.value;
				this.getList();
			},
			//搜索框点击清除(X按钮)
			searchClear(res) {
				this.queryParams.vendorId = "";
				this.getList();
			},
			/** 查询供应商列表 */
			getList() {
				listVendor(this.queryParams).then(response => {
				  this.vendorList = response.rows;
					for(var vendor of this.vendorList){
						this.$set(vendor, "checked", false);
					}
				  this.total = response.total;
				}
			  );
			},
			//详情编辑
			detailEdit(res) {
				// var arr = ['bd:vendor:edit'];
				if(this.$auth.hasPermi('bd:vendor:edit')){
					this.$tab.navigateTo('/pages/bd/vendor/uni-edit?vendorId='+res.vendorId);
				}
			},
			//分页变化事件
			pageChange(res) {
				// this.current = res.current;
				this.queryParams.pageNum = res.current
				this.getList();
			},
			//添加按钮
			addButton() {
				this.$tab.navigateTo('/pages/bd/vendor/uni-add');
			},
			//编辑按钮
			editButton(res){
				if(this.checkBoxs.length==1){
					this.$tab.navigateTo('/pages/bd/vendor/uni-edit?vendorId='+this.checkBoxs[0]);
				}else{
					this.$modal.msgError("请选择一个供应商");
				}
			},
			//设置筛选数据
			setScreenData() {
				this.screenList = [
								{
									'title': '供应商编码',
									'type': 'input',
									'key': 'vendorCode'
								},
								{
									'title': '供应商名称',
									'type': 'input',
									'key': 'vendorName'
								},
								{
									'title':'行业',
									'type':'select',
									'key':'industry',
									'detailList':this.industrySelectOptionsList
								},
								{
									'title':'地区',
									'type':'select',
									'key':'region',
									'detailList':this.regionSelectOptionsList
								},
								{
									'title':'合作状态 ',
									'type':'select',
									'key':'status',
									'detailList':this.statusSelectOptionsList
								},
				];
			},
			//复选框change事件
			checkboxChange: function (e) {
				var items = this.vendorList,
					values = e.detail.value;
				for (var item of items) {
					if(values.includes(JSON.stringify(item.vendorId))){
						this.$set(item,'checked',true)
					}else{
						this.$set(item,'checked',false)
					}
				}
				this.checkBoxs=values
			},
			//全选反选
			selectAll(){
				var items = this.vendorList;
				var allChecked = this.selectAllChecked;//点击时，全选按钮是否选中
				this.checkBoxs = [];//将选中值清空
				if(!allChecked){//点击时，全选按钮状态未选中，将组下所有选中
				    var checkBoxs = this.checkBoxs;
					items.forEach(function(item) {
						item['checked']=true;
						checkBoxs.push(JSON.stringify(item.vendorId));
					});
					this.checkBoxs = checkBoxs;
				}else{//点击时，全选按钮状态已选中，将组下所有
					items.forEach(function(item) {
						item['checked']=false;
					});
				}
				this.selectAllChecked = !allChecked;
			},
			//删除按钮
			deleteBut(){
				if(this.checkBoxs.length>0){
					showConfirm('是否确认删除供应商编号为"'+this.checkBoxs.join(',')+'"的数据项？').then(res => {
						if (res.confirm) {
							delVendor(this.checkBoxs.join(',')).then(response => {
							 this.$modal.msgSuccess("删除成功")
							 this.checkBoxs=[];
							 //重新从第一页开始加载
							 // this.current = 1;//分页组件赋值为第一页
							 this.queryParams.pageNum = 1;//查询页面也为第一页
							 this.getList();
						   })
					   }
					})
				}
			}
		}
	}
</script>
 
<style lang="scss">
	page{
		height: 100%;
	}
	.bd_vendor_container{
		height: 100%;
		display: flex; 
		flex-direction: column;

		/* 筛选栏样式开始 */
		.filter-bar{
			/* height: 100rpx; */
			display: flex;
			justify-content: space-between;
			align-items: center;
			height: 50px;
			background-color: #fff;
			border-bottom: 1px solid #eee;
			padding: 0 10px;
			/* position: sticky; */
		}
		.filter-item {
			display: flex;
			align-items: center;
			font-size: 16px;
			color: #333;
			position: relative;
			cursor: pointer;
		}

		.filter-item.active {
			color: #5500ff;
		}

		.filter-item .iconfont {
			font-size: 12px;
			margin-left: 5px;
		}
		/* 筛选栏样式结束 */

		/* 数据列表样式 */
		.data-list{
			overflow-y: auto;
			flex: 1;
			width: 100%;
		}
		/* 小图样式 */
		.icon-triangle {
			width: 16rpx;
			height: 16rpx;
			margin-left: 10rpx;
		}
	}
</style>
