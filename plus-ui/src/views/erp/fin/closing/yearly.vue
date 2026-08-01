<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计年度">
        <el-select v-model="fiscalYear" placeholder="选择年度" style="width: 160px">
          <el-option v-for="y in yearList" :key="y" :label="`${y} 年`" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="danger" icon="Calendar" :loading="loading" @click="doYearlyClose">执行年结</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      title="年结流程:12 月月结(试算校验→损益结转→外币重估→关账)→ 本年利润结转未分配利润 → 资产/负债/权益余额结转次年 1 月期初 → 12 月期间锁定"
      type="warning"
      :closable="false"
      show-icon
    />
  </div>
</template>

<script setup lang="ts" name="FinClosingYearly">
import { yearlyClose } from '@/api/erp/fin/closing';

const { proxy } = getCurrentInstance() as any;

const currentYear = new Date().getFullYear();
const yearList = [currentYear - 1, currentYear, currentYear + 1];
const fiscalYear = ref(currentYear);
const loading = ref(false);

function doYearlyClose() {
  proxy.$modal
    .confirm(`确认对 ${fiscalYear.value} 年执行年结?年结后 12 月期间将被锁定,不可再改动。`)
    .then(() => {
      loading.value = true;
      return yearlyClose({ fiscalYear: fiscalYear.value });
    })
    .then(() => {
      proxy.$modal.msgSuccess('年结完成');
    })
    .finally(() => {
      loading.value = false;
    });
}
</script>
