<template>
  <view class="bd_vendor_add_container">
    <!-- 状态栏占位 -->
    <view class="status-bar-placeholder"></view>
    <view class="example">
      <uni-forms ref="form" :model="form" labelWidth="80px">
    <uni-forms-item label="供应商编码" name="vendorCode" required >
      <uni-easyinput v-model="form.vendorCode" placeholder="请输入供应商编码" />
    </uni-forms-item>
    <uni-forms-item label="供应商名称" name="vendorName" required >
      <uni-easyinput v-model="form.vendorName" placeholder="请输入供应商名称" />
    </uni-forms-item>
    <uni-forms-item label="税号" name="taxId"  >
      <uni-easyinput v-model="form.taxId" placeholder="请输入税号" />
    </uni-forms-item>
  <uni-forms-item label="地址" name="address"  >
  <uni-easyinput type="textarea" v-model="form.address" placeholder="请输入地址" />
  </uni-forms-item>

    <uni-forms-item label="联系人" name="contactPerson"  >
      <uni-easyinput v-model="form.contactPerson" placeholder="请输入联系人" />
    </uni-forms-item>
    <uni-forms-item label="电话" name="phone"  >
      <uni-easyinput v-model="form.phone" placeholder="请输入电话" />
    </uni-forms-item>
    <uni-forms-item label="银行账户" name="bankAccount"  >
      <uni-easyinput v-model="form.bankAccount" placeholder="请输入银行账户" />
    </uni-forms-item>
  <uni-forms-item label="行业" name="industry"  >
    <uni-data-select v-model="form.industry" :localdata="industrySelectOptionsList"></uni-data-select>
  </uni-forms-item>
  <uni-forms-item label="地区" name="region"  >
    <uni-data-select v-model="form.region" :localdata="regionSelectOptionsList"></uni-data-select>
  </uni-forms-item>
  <uni-forms-item label="分类 (原材料/标准件/服务类)" name="category"  >
    <uni-data-select v-model="form.category" :localdata="categorySelectOptionsList"></uni-data-select>
  </uni-forms-item>
  <uni-forms-item label="合作状态 " name="status"  >
    <uni-data-select v-model="form.status" :localdata="statusSelectOptionsList"></uni-data-select>
  </uni-forms-item>
  <uni-forms-item label="备注" name="remark"  >
  <uni-easyinput type="textarea" v-model="form.remark" placeholder="请输入备注" />
  </uni-forms-item>

      </uni-forms>
      <view class="button-group">
        <button type="default" style="padding:0 3em;font-size:14px"  class="button" @click="reset">重置</button>
        <button type="primary" style="padding:0 3em;font-size:14px"  class="button" @click="submit">提交</button>
      </view>
    </view>
  </view>
</template>


<script>
    import { addVendor,getVendor,listVendor } from "@/api/bd/vendor";
    import {getSelectDict} from "@/utils/dict";
export default {
components: {
},
data() {
  return {
    //字典类型筛选options
      industrySelectOptionsList:[],
      industryOptions:"bd_industry",
      regionSelectOptionsList:[],
      regionOptions:"bd_region",
      categorySelectOptionsList:[],
      categoryOptions:"bd_vendor_category",
      statusSelectOptionsList:[],
      statusOptions:"bd_vendor_status",
    form: {},
    rules: {
            vendorCode: {
                rules: [{ required: true, errorMessage: "供应商编码不能为空" }]
            },
            vendorName: {
                rules: [{ required: true, errorMessage: "供应商名称不能为空" }]
            },
    }
  }
},
onLoad(option) {
  //字典类型查询
        getSelectDict(this.industryOptions).then(response => {
          this.industrySelectOptionsList = response;
        });
        getSelectDict(this.regionOptions).then(response => {
          this.regionSelectOptionsList = response;
        });
        getSelectDict(this.categoryOptions).then(response => {
          this.categorySelectOptionsList = response;
        });
        getSelectDict(this.statusOptions).then(response => {
          this.statusSelectOptionsList = response;
        });
},
onReady() {
  this.$refs.form.setRules(this.rules)
},
methods: {
  /* 提交*/
  submit() {
    console.log(this.form)
    this.$refs.form.validate().then(res => {
      addVendor(this.form).then(response => {
        this.$modal.msgSuccess("新增成功")
        setTimeout(() => {
          // 返回到上一级父页面
          this.$tab.navigateBack();
        },500)
      })
    })
  },
  /* 表单重置*/
  reset(){
    this.form = {
       vendorId: undefined,
       vendorCode: undefined,
       vendorName: undefined,
       taxId: undefined,
       address: undefined,
       contactPerson: undefined,
       phone: undefined,
       bankAccount: undefined,
       industry: undefined,
       region: undefined,
       category: undefined,
       status: undefined,
       remark: undefined,
       attr1: undefined,
       attr2: undefined,
       attr3: undefined,
       attr4: undefined,
       createBy: undefined,
       createTime: undefined,
       updateBy: undefined,
       updateTime: undefined
    };
  }
}
}
</script>

<style lang="scss">
  page {
    background-color: #ffffff;
  }

  .bd_vendor_add_container {
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