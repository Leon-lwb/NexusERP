<template>
  <view class="so_customer_order_edit_container">
    <!-- 状态栏占位 -->
    <view class="status-bar-placeholder"></view>
    <view class="example">
      <uni-forms ref="form" :model="form" labelWidth="80px">
            <uni-forms-item label="客户订单号" name="coNo" required >
              <uni-easyinput v-model="form.coNo" placeholder="请输入客户订单号" />
            </uni-forms-item>
            <uni-forms-item label="客户ID" name="clientId" required >
              <uni-easyinput v-model="form.clientId" placeholder="请输入客户ID" />
            </uni-forms-item>
            <uni-forms-item label="联系人" name="contactName"  >
              <uni-easyinput v-model="form.contactName" placeholder="请输入联系人" />
            </uni-forms-item>
            <uni-forms-item label="联系人电话" name="contactPhone"  >
              <uni-easyinput v-model="form.contactPhone" placeholder="请输入联系人电话" />
            </uni-forms-item>
            <uni-forms-item label="客户下单日期" name="orderDate" required >
              <uni-datetime-picker type="datetime" v-model="form.orderDate" return-type="string" />
            </uni-forms-item>
                          <uni-forms-item label="客户要求交货日" name="requestedDate"  >
              <uni-datetime-picker type="datetime" v-model="form.requestedDate" return-type="string" />
            </uni-forms-item>
                          <uni-forms-item label="货币" name="currency" required >
              <uni-data-select v-model="form.currency" :localdata="currencySelectOptionsList"></uni-data-select>
            </uni-forms-item>
            <uni-forms-item label="订单总金额" name="totalAmount"  >
              <uni-easyinput v-model="form.totalAmount" placeholder="请输入订单总金额" />
            </uni-forms-item>
            <uni-forms-item label="订单状态" name="status"  >
              <uni-data-checkbox v-model="form.status" :localdata="statusSelectOptionsList"  />
            </uni-forms-item>
            <uni-forms-item label="订单优先级" name="priority"  >
              <uni-data-select v-model="form.priority" :localdata="prioritySelectOptionsList"></uni-data-select>
            </uni-forms-item>
            <uni-forms-item label="客户采购订单号" name="customerPoNo"  >
              <uni-easyinput v-model="form.customerPoNo" placeholder="请输入客户采购订单号" />
            </uni-forms-item>
            <uni-forms-item label="订单来源" name="sourceChannel"  >
              <uni-data-select v-model="form.sourceChannel" :localdata="sourceChannelSelectOptionsList"></uni-data-select>
            </uni-forms-item>
            <uni-forms-item label="备注" name="remark"  >
              <uni-easyinput type="textarea" v-model="form.remark" placeholder="请输入备注" />
            </uni-forms-item>

      </uni-forms>
      <view class="button-group">
        <button type="default" style="padding:0 3em;font-size:14px"  class="button" @click="cancel">取消</button>
        <button type="primary" style="padding:0 3em;font-size:14px"  class="button" @click="submit">提交</button>
      </view>
    </view>
  </view>
</template>


<script>
import { updateCustomerOrder,getCustomerOrder,listCustomerOrder } from "@/api/so/customerOrder";
    import {getSelectDict} from "@/utils/dict";
export default {
  components: {
  },
  data() {
    return {
          //字典类型筛选options
                        currencySelectOptionsList:[],
                        currencyOptions:"so_currency_type",
                        statusSelectOptionsList:[],
                        statusOptions:"so_customer_order_status",
                        prioritySelectOptionsList:[],
                        priorityOptions:"so_order_priority",
                        sourceChannelSelectOptionsList:[],
                        sourceChannelOptions:"so_source_channel",
    form: {},
    rules: {
                  coNo: {
              rules: [{ required: true, errorMessage: "客户订单号不能为空" }]
            },
                                clientId: {
              rules: [{ required: true, errorMessage: "客户ID不能为空" }]
            },
                                orderDate: {
              rules: [{ required: true, errorMessage: "客户下单日期不能为空" }]
            },
                                currency: {
              rules: [{ required: true, errorMessage: "货币不能为空" }]
            },
                    }
    }
  },
onLoad(option) {
      //字典类型查询
              getSelectDict(this.currencyOptions).then(response => {
                this.currencySelectOptionsList = response;
              });
              getSelectDict(this.statusOptions).then(response => {
                this.statusSelectOptionsList = response;
              });
              getSelectDict(this.priorityOptions).then(response => {
                this.prioritySelectOptionsList = response;
              });
              getSelectDict(this.sourceChannelOptions).then(response => {
                this.sourceChannelSelectOptionsList = response;
              });
  //客户订单详细
  this.getCustomerOrderInfo(option.coId);
},
  onReady() {
    this.$refs.form.setRules(this.rules)
  },
  methods: {
    /* 获取客户订单详情*/
    getCustomerOrderInfo(coId) {
      getCustomerOrder(coId).then(response => {
        this.form = response.data;
      })
    },
/* 提交*/
submit() {
  this.$refs.form.validate().then(res => {
    updateCustomerOrder(this.form).then(response => {
      this.$modal.msgSuccess("编辑成功")
      setTimeout(() => {
        // 返回到上一级父页面
        this.$tab.navigateBack();
      },500)
    })
  })
},
/* 取消*/
cancel(){
  this.$tab.navigateBack();
}
}
}
</script>

<style lang="scss">
  page {
    background-color: #ffffff;
  }

  .so_customer_order_edit_container {
    // 状态栏占位
    .status-bar-placeholder {
      height: var(--status-bar-height);
    }

    .example {
      padding: 15px;
      background-color: #fff;
    }

    .button-group {
      margin-top: 15px;
      display: flex;
      justify-content: space-around;
    }
  }
</style>