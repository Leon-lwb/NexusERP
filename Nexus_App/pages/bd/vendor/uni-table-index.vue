<template>
  <view class="bd_vendor_table_container">
    <view>
      <!-- 筛选弹框 -->
      <fjj-condition ref='condition' @touchmove.stop :color="conditionColor" :list="screenList"
                     :defaultValue="defaultValue" @result="resultConditon"/>
      <!-- 供应商搜索 -->
      <uni-section title=" ">
        <uni-search-bar :focus="false" v-model="searchValue" @blur="searchBlur"
                        placeholder="搜索供应商" @clear="searchClear" cancelButton="none">
        </uni-search-bar>
      </uni-section>
    </view>
    <view class="filter-bar">
      <view class="filter-item">
        <button size="mini" type="default" style="color:#ffba00;background-color:#fff8e6;
					border-color:#000000" @click="delCheckbox"
                v-if="checkPermi(['bd:vendor:remove'])">删除
        </button>
      </view>
      <view class="filter-item" @click="open">
        <button size="mini" type="default"
                style="color:#ffffff;background-color:#007aff;border-color:#000000">筛选
        </button>
      </view>
      <view class="filter-item">
        <button size="mini" type="default"
                style="color:#ff0000;background-color:#ffeded;border-color:#000000"
                @click="addButton" v-if="checkPermi(['bd:vendor:add'])">新增
        </button>
      </view>
    </view>
    <view class="data-list">
      <!-- 供应商列表 -->
      <uni-table ref="table" :loading="loading" border stripe type="selection" emptyText="暂无更多数据"
                 @selection-change="selectionChange">
        <uni-tr>
                  <uni-th width="80" align="center">供应商编码</uni-th>
                  <uni-th width="80" align="center">供应商名称</uni-th>
                  <uni-th width="80" align="center">行业</uni-th>
                  <uni-th width="80" align="center">地区</uni-th>
                  <uni-th width="80" align="center">合作状态 </uni-th>
          <uni-th width="100" align="center">操作</uni-th>
        </uni-tr>
        <uni-tr v-for="(item, index) in vendorList" :key="item.vendorId">
                  <uni-td>
                        <view align="center">{{ item.vendorCode }}</view>
                  </uni-td>
                  <uni-td>
                        <view align="center">{{ item.vendorName }}</view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="industryOptionsList" :value="item.industry"/>
                                </view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="regionOptionsList" :value="item.region"/>
                                </view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="statusOptionsList" :value="item.status"/>
                                </view>
                  </uni-td>
          <uni-td>
            <view class="uni-group">
              <uni-icons type="compose" color="blue" size="24" @click="editButton(item)"
                         v-if="checkPermi(['bd:vendor:edit'])"/>
              <uni-icons type="trash-filled" color="blue" size="24" @click="deleteButton(item)"
                         v-if="checkPermi(['bd:vendor:remove'])"/>
            </view>
          </uni-td>
        </uni-tr>
      </uni-table>
    </view>
    <view>
      <uni-section title=" " padding>
        <uni-pagination :total="total" :pageSize="queryParams.pageSize" :current="queryParams.pageNum"
                        @change="pageChange"/>
      </uni-section>
    </view>
  </view>
</template>

<script>
  import fjjCondition from '@/components/fjj-condition/fjj-condition.vue';
  import {listVendor, delVendor} from "@/api/bd/vendor";
  import {showConfirm} from '@/utils/common';
  import {checkPermi} from "@/utils/permission";// 权限工具类
      import {getDict,getSelectDict} from "@/utils/dict";
  export default {
    components: {
      fjjCondition
    },
    data() {
      return {
                    //行业字典类型筛选options
                          industryOptionsList:[],
                          industrySelectOptionsList:[],
                          industryOptions:"bd_industry",
                    //地区字典类型筛选options
                          regionOptionsList:[],
                          regionSelectOptionsList:[],
                          regionOptions:"bd_region",
                    //分类 (原材料/标准件/服务类)字典类型筛选options
                          categoryOptionsList:[],
                          categorySelectOptionsList:[],
                          categoryOptions:"bd_vendor_category",
                    //合作状态 字典类型筛选options
                          statusOptionsList:[],
                          statusSelectOptionsList:[],
                          statusOptions:"bd_vendor_status",
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
        loading: false,
        selectedIndexs: [],
              vendorList: [],
        total: 0,  //总条数
        // current: 1,//初始化当前页为第一页
        //添加按钮
        horizontal: 'right',
        vertical: 'bottom',
        popMenu: false
      }
    },
    onShow(options) {//监听页面显示，页面每次出现在屏幕上都触发，包括从下级页面点返回露出当前页面
      this.getList();
    },
    onLoad() {
                  //行业字典类型查询
                  getDict(this.industryOptions).then(response => {
                    this.industryOptionsList = response;
                  });
                  getSelectDict(this.industryOptions).then(response => {
                    this.industrySelectOptionsList = response;
                  });
                  //地区字典类型查询
                  getDict(this.regionOptions).then(response => {
                    this.regionOptionsList = response;
                  });
                  getSelectDict(this.regionOptions).then(response => {
                    this.regionSelectOptionsList = response;
                  });
                  //分类 (原材料/标准件/服务类)字典类型查询
                  getDict(this.categoryOptions).then(response => {
                    this.categoryOptionsList = response;
                  });
                  getSelectDict(this.categoryOptions).then(response => {
                    this.categorySelectOptionsList = response;
                  });
                  //合作状态 字典类型查询
                  getDict(this.statusOptions).then(response => {
                    this.statusOptionsList = response;
                  });
                  getSelectDict(this.statusOptions).then(response => {
                    this.statusSelectOptionsList = response;
                  });
      //设置筛选条件
      setTimeout(() => {
        this.setScreenData();
      }, 500)
      this.selectedIndexs = [];
    },
    mounted() {
    },
    methods: {
      checkPermi,
      //打开筛选面板
      open() {
        this.$refs.condition.openPopup()
      },
      //确认筛选条件并开始搜索
      resultConditon(obj) {
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
      //刷新
      refresh() {
        this.queryParams = {
          pageNum: 1, //当前页，
          pageSize: 5, //每页数量
                vendorCode:undefined, 
                vendorName:undefined, 
                industry:undefined, 
                region:undefined, 
                status:undefined, 
        }
        this.getList();
      },
      /** 查询供应商列表 */
      getList() {
        this.loading = true;
        listVendor(this.queryParams).then(response => {
              this.vendorList = response.rows;
              this.total = response.total;
              this.loading = false;
            }
        );
      },
      //分页变化事件
      pageChange(res) {
        //清空复选框
        this.$refs.table.clearSelection()
        this.selectedIndexs.length = 0
        this.queryParams.pageNum = res.current
        this.getList();
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
      //编辑
      editButton(item) {
        this.$tab.navigateTo('/pages/bd/vendor/uni-edit?vendorId=' + item.vendorId);
      },
      //删除
      deleteButton(item) {
        showConfirm('是否确认删除供应商编号为"' + item.vendorId +'"的数据项？').then(res => {
          if (res.confirm) {
            delVendor(item.vendorId
          ).then(response => {
              this.$modal.msgSuccess("删除成功");
              //清空复选框
              this.$refs.table.clearSelection()
              this.selectedIndexs.length = 0
              //重新从第一页开始加载
              this.queryParams.pageNum = 1;//查询页面也为第一页
              this.getList();
            })
          }
        })
      },
      //新增
      addButton() {
        this.$tab.navigateTo('/pages/bd/vendor/uni-add');
      },
      // 多选
      selectionChange(e) {
        this.selectedIndexs = e.detail.index
      },
      // 多选处理
      selectedItems() {
        return this.selectedIndexs.map(i => this.vendorList[i].vendorId)
      },
      //批量删除
      delCheckbox() {
        if (this.selectedItems() && this.selectedItems().length > 0) {
          showConfirm('是否确认删除供应商编号为"' + this.selectedItems().join(",") + '"的数据项？').then(res => {
            if (res.confirm) {
              delVendor(this.selectedItems().join(",")).then(response => {
                this.$modal.msgSuccess("删除成功");
                //清空复选框
                this.$refs.table.clearSelection()
                this.selectedIndexs.length = 0
                //重新从第一页开始加载
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
  page {
    height: 100%;
  }
  .bd_vendor_table_container{
    .container {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    /* 筛选栏样式开始 */
    .filter-bar {
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
    .data-list {
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
