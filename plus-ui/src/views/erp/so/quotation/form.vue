<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="quotationRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="报价单编号" prop="quotationNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.quotationNo" placeholder="请输入报价单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="报价日期" prop="quotationDate">
              <el-date-picker
                clearable
                v-model="form.quotationDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择报价日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="有效期至" prop="validUntil">
              <el-date-picker
                clearable
                v-model="form.validUntil"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择有效期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="客户" prop="clientName">
              <el-input v-model="form.clientName" placeholder="请选择客户" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="clientSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售员" prop="salesRepName">
              <el-input v-model="form.salesRepName" placeholder="请选择销售员" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openUserSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币别" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币别">
                <el-option v-for="dict in sys_currency" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">价格信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="汇率" prop="exchangeRate">
              <el-input-number v-model="form.exchangeRate" :min="0" :precision="4" :step="0.0001" placeholder="请输入汇率" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣率(%)" prop="discountRate">
              <el-input-number
                v-model="form.discountRate"
                :min="0"
                :max="100"
                :precision="2"
                :step="1"
                placeholder="请输入折扣率"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款条件" prop="paymentTerms">
              <el-input v-model="form.paymentTerms" placeholder="请输入付款条件" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="交货条件" prop="deliveryTerms">
              <el-input v-model="form.deliveryTerms" placeholder="请输入交货条件" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">报价行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择物料" v-if="!isViewMode" readonly>
                <template #append><el-button icon="Search" @click="openMaterialSelect(scope.$index)" /></template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="物料名称" v-if="!isViewMode" readonly />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" width="120">
            <template #default="scope"
              ><span>{{ scope.row.materialSpec }}</span></template
            >
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantity"
                :min="0"
                :precision="2"
                :step="1"
                :controls="false"
                placeholder="数量"
                v-if="!isViewMode"
                @change="calcLine(scope.row)"
              />
              <span v-else>{{ scope.row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.unitPrice"
                :min="0"
                :precision="2"
                :step="0.01"
                :controls="false"
                placeholder="单价"
                v-if="!isViewMode"
                @change="calcLine(scope.row)"
              />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="100">
            <template #default="scope"
              ><span>{{ scope.row.amount }}</span></template
            >
          </el-table-column>
          <el-table-column label="税率(%)" prop="taxRate" width="100">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.taxRate"
                :min="0"
                :max="100"
                :precision="2"
                :step="1"
                :controls="false"
                placeholder="税率"
                v-if="!isViewMode"
              />
              <span v-else>{{ scope.row.taxRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="交货日期" prop="deliveryDate" width="120">
            <template #default="scope">
              <el-date-picker v-model="scope.row.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="交货日期" v-if="!isViewMode" />
              <span v-else>{{ scope.row.deliveryDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" v-if="!isViewMode" />
              <span v-else>{{ scope.row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <el-dialog title="选择客户" v-model="clientSelectOpen" width="800px" append-to-body>
      <el-form :model="clientQueryParams" :inline="true">
        <el-form-item label="客户编码"
          ><el-input v-model="clientQueryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="getClientList"
        /></el-form-item>
        <el-form-item label="客户名称"
          ><el-input v-model="clientQueryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="getClientList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getClientList">搜索</el-button
          ><el-button icon="Refresh" @click="resetClientQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="clientLoading" :data="clientList" highlight-current-row @current-change="handleClientSelect">
        <el-table-column label="客户编码" align="center" prop="clientCode" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="联系人" align="center" prop="contactPerson" />
        <el-table-column label="操作" align="center"
          ><template #default="scope"
            ><el-button link type="primary" @click="handleClientSelect(scope.row)">选择</el-button></template
          ></el-table-column
        >
      </el-table>
      <pagination
        v-show="clientTotal > 0"
        :total="clientTotal"
        v-model:page="clientQueryParams.pageNum"
        v-model:limit="clientQueryParams.pageSize"
        @pagination="getClientList"
      />
    </el-dialog>

    <el-dialog title="选择销售员" v-model="userSelectOpen" width="800px" append-to-body>
      <el-form :model="userQueryParams" :inline="true">
        <el-form-item label="用户编码"
          ><el-input v-model="userQueryParams.userName" placeholder="请输入用户编码" clearable @keyup.enter="getUserList"
        /></el-form-item>
        <el-form-item label="用户名称"
          ><el-input v-model="userQueryParams.nickName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getUserList">搜索</el-button
          ><el-button icon="Refresh" @click="resetUserQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="userLoading" :data="userList" highlight-current-row @current-change="handleUserSelect">
        <el-table-column label="用户编码" align="center" prop="userName" />
        <el-table-column label="用户名称" align="center" prop="nickName" />
        <el-table-column label="部门" align="center" prop="deptName" />
        <el-table-column label="操作" align="center"
          ><template #default="scope"><el-button link type="primary" @click="handleUserSelect(scope.row)">选择</el-button></template></el-table-column
        >
      </el-table>
      <pagination
        v-show="userTotal > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />
    </el-dialog>

    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="QuotationForm">
import { getQuotation, addQuotation, updateQuotation } from '@/api/erp/so/quotation';
import { listClient } from '@/api/erp/bd/client';
import { getMeasure } from '@/api/erp/bd/measure';
import { listUser } from '@/api/system/user';
import { previewCode } from '@/api/system/autocode/rule';
import { useRoute, useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';

const { proxy } = getCurrentInstance() as any;
const { sys_currency } = toRefs<any>(proxy.useDict('sys_currency'));
const route = useRoute();
const router = useRouter();

const form = ref<any>({});
const autoGenFlag = ref(false);
const isViewMode = ref(false);
const title = ref('');
const lineList = ref<any[]>([]);
const checkedLines = ref<any[]>([]);

const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
const currentMaterialRowIndex = ref(-1);

const clientSelectOpen = ref(false);
const clientList = ref([]);
const clientLoading = ref(false);
const clientTotal = ref(0);
const clientQueryParams = ref({ pageNum: 1, pageSize: 10, clientCode: null, clientName: null });

const userSelectOpen = ref(false);
const userList = ref([]);
const userLoading = ref(false);
const userTotal = ref(0);
const userQueryParams = ref({ pageNum: 1, pageSize: 10, userName: null, nickName: null });

const rules = {
  quotationNo: [{ required: true, message: '报价单编号不能为空', trigger: 'blur' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增报价单';
    reset();
  } else if (action === 'edit') {
    title.value = '修改报价单';
    if (id) getInfo(id);
  } else if (action === 'view') {
    title.value = '查看报价单';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getQuotation(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.soQuotationLineList || [];
  });
}

function reset() {
  autoGenFlag.value = false;
  form.value = {
    quotationId: null,
    quotationNo: null,
    clientId: null,
    clientName: null,
    salesRepId: null,
    salesRepName: null,
    quotationDate: null,
    validUntil: null,
    currency: 'CNY',
    exchangeRate: 1,
    totalAmount: null,
    taxAmount: null,
    discountAmount: null,
    discountRate: null,
    paymentTerms: null,
    deliveryTerms: null,
    status: null,
    remark: null
  };
  lineList.value = [];
  proxy.resetForm('quotationRef');
}

function submitForm() {
  proxy.$refs['quotationRef'].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.quotationNo = undefined;
      }
      form.value.soQuotationLineList = lineList.value;
      if (form.value.quotationId != null) {
        updateQuotation(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/so/quotation');
        });
      } else {
        addQuotation(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/so/quotation');
        });
      }
    }
  });
}

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('SO_QUOTATION_CODE').then((response) => {
      form.value.quotationNo = response;
    });
  } else {
    form.value.quotationNo = null;
  }
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/so/quotation' });
}

function rowLineIndex({ row, rowIndex }: any): string {
  row.index = rowIndex + 1;
  return '';
}

function handleAddLine() {
  lineList.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    baseUomId: '',
    baseUomName: '',
    quantity: null,
    unitPrice: null,
    amount: null,
    taxRate: null,
    deliveryDate: null,
    remark: ''
  });
}

/** 计算行金额 = 数量 × 单价（保留两位小数） */
function calcLine(line: any) {
  const qty = Number(line.quantity) || 0;
  const price = Number(line.unitPrice) || 0;
  line.amount = Math.round(qty * price * 100) / 100;
}

function handleDeleteLine() {
  if (checkedLines.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((item: any) => checkedLines.value.indexOf(item.index) == -1);
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection.map((item: any) => item.index);
}

function openMaterialSelect(rowIndex: number) {
  currentMaterialRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(row: any) {
  const idx = currentMaterialRowIndex.value;
  if (idx >= 0 && lineList.value[idx]) {
    lineList.value[idx].materialId = row.materialId;
    lineList.value[idx].materialCode = row.materialCode;
    lineList.value[idx].materialName = row.materialName;
    lineList.value[idx].materialSpec = row.materialSpec;
    if (row.baseUomId) {
      lineList.value[idx].baseUomId = row.baseUomId;
      getMeasure(row.baseUomId).then((r: any) => {
        if (idx < lineList.value.length) lineList.value[idx].baseUomName = r.data.uomName;
      });
    }
  }
  currentMaterialRowIndex.value = -1;
}

function getClientList() {
  clientLoading.value = true;
  listClient(clientQueryParams.value).then((response: any) => {
    clientList.value = response.rows;
    clientTotal.value = response.total;
    clientLoading.value = false;
  });
}

function resetClientQuery() {
  clientQueryParams.value = { pageNum: 1, pageSize: 10, clientCode: null, clientName: null };
  getClientList();
}

function handleClientSelect(row: any) {
  form.value.clientId = row.clientId;
  form.value.clientName = row.clientName;
  clientSelectOpen.value = false;
}

function openUserSelect() {
  userSelectOpen.value = true;
  userQueryParams.value = { pageNum: 1, pageSize: 10, userName: null, nickName: null };
  getUserList();
}

function getUserList() {
  userLoading.value = true;
  listUser(userQueryParams.value).then((response: any) => {
    userList.value = response.rows;
    userTotal.value = response.total;
    userLoading.value = false;
  });
}

function resetUserQuery() {
  userQueryParams.value = { pageNum: 1, pageSize: 10, userName: null, nickName: null };
  getUserList();
}

function handleUserSelect(row: any) {
  form.value.salesRepId = row.userId;
  form.value.salesRepName = row.nickName;
  userSelectOpen.value = false;
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
.mb8 {
  margin-bottom: 8px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
