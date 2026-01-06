<template>
  <view class="so_customer_order_table_container">
    <view>
      <!-- 筛选弹框 -->
      <fjj-condition ref='condition' @touchmove.stop :color="conditionColor" :list="screenList"
                     :defaultValue="defaultValue" @result="resultConditon"/>
      <!-- 客户订单搜索 -->
      <uni-section title=" ">
        <uni-search-bar :focus="false" v-model="searchValue" @blur="searchBlur"
                        placeholder="搜索客户订单" @clear="searchClear" cancelButton="none">
        </uni-search-bar>
      </uni-section>
    </view>
    <view class="filter-bar">
      <view class="filter-item">
        <button size="mini" type="default" style="color:#ffba00;background-color:#fff8e6;
					border-color:#000000" @click="delCheckbox"
                v-if="checkPermi(['so:customerOrder:remove'])">删除
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
                @click="addButton" v-if="checkPermi(['so:customerOrder:add'])">新增
        </button>
      </view>
    </view>
    <view class="data-list">
      <!-- 客户订单列表 -->
      <uni-table ref="table" :loading="loading" border stripe type="selection" emptyText="暂无更多数据"
                 @selection-change="selectionChange">
        <uni-tr>
                  <uni-th width="80" align="center">客户订单号</uni-th>
                  <uni-th width="80" align="center">客户ID</uni-th>
                  <uni-th width="80" align="center">客户下单日期</uni-th>
                  <uni-th width="80" align="center">订单总金额</uni-th>
                  <uni-th width="80" align="center">订单状态</uni-th>
                  <uni-th width="80" align="center">订单优先级</uni-th>
                  <uni-th width="80" align="center">客户采购订单号</uni-th>
                  <uni-th width="80" align="center">订单来源</uni-th>
          <uni-th width="100" align="center">操作</uni-th>
        </uni-tr>
        <uni-tr v-for="(item, index) in customerOrderList" :key="item.coId">
                  <uni-td>
                        <view align="center">{{ item.coNo }}</view>
                  </uni-td>
                  <uni-td>
                        <view align="center">{{ item.clientId }}</view>
                  </uni-td>
                  <uni-td>
                        <view align="center">{{ item.orderDate }}</view>
                  </uni-td>
                  <uni-td>
                        <view align="center">{{ item.totalAmount }}</view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="statusOptionsList" :value="item.status"/>
                                </view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="priorityOptionsList" :value="item.priority"/>
                                </view>
                  </uni-td>
                  <uni-td>
                        <view align="center">{{ item.customerPoNo }}</view>
                  </uni-td>
                  <uni-td>
                                <view align="center">
                                  <dict-tag :options="sourceChannelOptionsList" :value="item.sourceChannel"/>
                                </view>
                  </uni-td>
          <uni-td>
            <view class="uni-group">
              <uni-icons type="compose" color="blue" size="24" @click="editButton(item)"
                         v-if="checkPermi(['so:customerOrder:edit'])"/>
              <uni-icons type="trash-filled" color="blue" size="24" @click="deleteButton(item)"
                         v-if="checkPermi(['so:customerOrder:remove'])"/>
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
  import {listCustomerOrder, delCustomerOrder} from "@/api/so/customerOrder";
  import {showConfirm} from '@/utils/common';
  import {checkPermi} from "@/utils/permission";// 权限工具类
      import {getDict,getSelectDict} from "@/utils/dict";
  export default {
    components: {
      fjjCondition
    },
    data() {
      return {
                    //货币字典类型筛选options
                          currencyOptionsList:[],
                          currencySelectOptionsList:[],
                          currencyOptions:"so_currency_type",
                    //订单状态字典类型筛选options
                          statusOptionsList:[],
                          statusSelectOptionsList:[],
                          statusOptions:"so_customer_order_status",
                    //订单优先级字典类型筛选options
                          priorityOptionsList:[],
                          prioritySelectOptionsList:[],
                          priorityOptions:"so_order_priority",
                    //订单来源字典类型筛选options
                          sourceChannelOptionsList:[],
                          sourceChannelSelectOptionsList:[],
                          sourceChannelOptions:"so_source_channel",
        //客户订单搜索
        searchValue: '',
        //筛选弹框
        conditionColor: '#4D7BFE',
        screenList: [],
        defaultValue: {},
        // 查询参数
        queryParams: {
          pageNum: 1, //当前页，
          pageSize: 5, //每页数量
                        coNo: undefined,
                        clientId: undefined,
                        orderDate: undefined,
                        status: undefined,
                        priority: undefined,
                        customerPoNo: undefined,
                        sourceChannel: undefined,
        },
        //列表数据
        loading: false,
        selectedIndexs: [],
              customerOrderList: [],
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
                  //货币字典类型查询
                  getDict(this.currencyOptions).then(response => {
                    this.currencyOptionsList = response;
                  });
                  getSelectDict(this.currencyOptions).then(response => {
                    this.currencySelectOptionsList = response;
                  });
                  //订单状态字典类型查询
                  getDict(this.statusOptions).then(response => {
                    this.statusOptionsList = response;
                  });
                  getSelectDict(this.statusOptions).then(response => {
                    this.statusSelectOptionsList = response;
                  });
                  //订单优先级字典类型查询
                  getDict(this.priorityOptions).then(response => {
                    this.priorityOptionsList = response;
                  });
                  getSelectDict(this.priorityOptions).then(response => {
                    this.prioritySelectOptionsList = response;
                  });
                  //订单来源字典类型查询
                  getDict(this.sourceChannelOptions).then(response => {
                    this.sourceChannelOptionsList = response;
                  });
                  getSelectDict(this.sourceChannelOptions).then(response => {
                    this.sourceChannelSelectOptionsList = response;
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
      //搜索框失去焦点，搜索客户订单
      searchBlur(res) {
        this.queryParams.coId = res.value;
        this.getList();
      },
      //搜索框点击清除(X按钮)
      searchClear(res) {
        this.queryParams.coId = "";
        this.getList();
      },
      //刷新
      refresh() {
        this.queryParams = {
          pageNum: 1, //当前页，
          pageSize: 5, //每页数量
                coNo:undefined, 
                clientId:undefined, 
                orderDate:undefined, 
                status:undefined, 
                priority:undefined, 
                customerPoNo:undefined, 
                sourceChannel:undefined, 
        }
        this.getList();
      },
      /** 查询客户订单列表 */
      getList() {
        this.loading = true;
        listCustomerOrder(this.queryParams).then(response => {
              this.customerOrderList = response.rows;
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
                      'title': '客户订单号',
                      'type': 'input',
                      'key': 'coNo'
                    },
                    {
                      'title': '客户ID',
                      'type': 'input',
                      'key': 'clientId'
                    },
                    {
                      'title': '客户下单日期',
                      'type': 'datetime',
                      'key': 'orderDate'
                    },
                    {
                      'title':'订单状态',
                      'type':'radio',
                      'key':'status',
                      'detailList':this.statusSelectOptionsList
                    },
                    {
                      'title':'订单优先级',
                      'type':'select',
                      'key':'priority',
                      'detailList':this.prioritySelectOptionsList
                    },
                    {
                      'title': '客户采购订单号',
                      'type': 'input',
                      'key': 'customerPoNo'
                    },
                    {
                      'title':'订单来源',
                      'type':'select',
                      'key':'sourceChannel',
                      'detailList':this.sourceChannelSelectOptionsList
                    },
      ];
      },
      //编辑
      editButton(item) {
        this.$tab.navigateTo('/pages/so/customerOrder/uni-edit?coId=' + item.coId);
      },
      //删除
      deleteButton(item) {
        showConfirm('是否确认删除客户订单编号为"' + item.coId +'"的数据项？').then(res => {
          if (res.confirm) {
            delCustomerOrder(item.coId
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
        this.$tab.navigateTo('/pages/so/customerOrder/uni-add');
      },
      // 多选
      selectionChange(e) {
        this.selectedIndexs = e.detail.index
      },
      // 多选处理
      selectedItems() {
        return this.selectedIndexs.map(i => this.customerOrderList[i].coId)
      },
      //批量删除
      delCheckbox() {
        if (this.selectedItems() && this.selectedItems().length > 0) {
          showConfirm('是否确认删除客户订单编号为"' + this.selectedItems().join(",") + '"的数据项？').then(res => {
            if (res.confirm) {
              delCustomerOrder(this.selectedItems().join(",")).then(response => {
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
  .so_customer_order_table_container{
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
