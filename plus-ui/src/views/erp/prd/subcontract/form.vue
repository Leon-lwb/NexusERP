<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保 存</el-button>
            <el-button @click="cancel">取 消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="subcontractRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="委外单号" prop="subcontractNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.subcontractNo" placeholder="请输入委外单号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode"
                ></el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorId">
              <el-input v-model="form.vendorName" placeholder="请选择供应商" readonly>
                <template #append>
                  <el-button icon="Search" @click="openVendorSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="加工单价" prop="unitPrice">
              <el-input-number v-model="form.unitPrice" :precision="4" :min="0" placeholder="请输入加工单价" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">产出物料与 BOM</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请选择 PCBA 物料" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="关联BOM" prop="bomId">
              <el-input v-model="bomDisplay" placeholder="请选择BOM" readonly>
                <template #append>
                  <el-button icon="Search" @click="openBomSelect" :disabled="isViewMode || !form.materialId" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BOM版本" prop="bomVersion">
              <el-input v-model="bomVersionDisplay" placeholder="选择BOM后自动带出" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="form.plannedQty" :min="0" :precision="4" :step="1" placeholder="请输入计划数量" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">计划与收货仓库</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker
                v-model="form.planStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划结束日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货目标仓库" prop="targetWarehouseId">
              <el-select
                v-model="form.targetWarehouseId"
                placeholder="默认半成品仓路由"
                clearable
                filterable
                style="width: 100%"
                @change="onWarehouseChange"
              >
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.warehouseId"
                  :label="`${w.warehouseCode} - ${w.warehouseName}`"
                  :value="w.warehouseId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工艺要求" prop="processRequirement">
              <el-input v-model="form.processRequirement" type="textarea" :rows="3" placeholder="质量标准/特殊工艺/AOI/FCT等(询价与发料资料包随附)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 物料选择 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择 PCBA 物料" @select="handleMaterialSelected" />

    <!-- 供应商选择 -->
    <el-dialog title="选择供应商" v-model="vendorSelectOpen" width="900px" append-to-body>
      <el-form :inline="true">
        <el-form-item label="供应商名称">
          <el-input v-model="vendorQueryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="getVendorList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getVendorList">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="vendorLoading" :data="vendorList" @row-dblclick="selectVendor" height="380">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="供应商编码" align="center" prop="vendorCode" />
        <el-table-column label="供应商名称" align="center" prop="vendorName" show-overflow-tooltip />
        <el-table-column label="联系人" align="center" prop="contactName" />
        <el-table-column label="联系电话" align="center" prop="contactPhone" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectVendor(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="vendorTotal > 0"
        :total="vendorTotal"
        v-model:page="vendorQueryParams.pageNum"
        v-model:limit="vendorQueryParams.pageSize"
        @pagination="getVendorList"
      />
      <template #footer>
        <el-button @click="vendorSelectOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- BOM 选择 -->
    <el-dialog title="选择BOM" v-model="bomSelectOpen" width="960px" append-to-body>
      <el-form :inline="true">
        <el-form-item label="BOM名称">
          <el-input v-model="bomQueryParams.bomName" placeholder="请输入BOM名称" clearable @keyup.enter="getBomList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getBomList">搜索</el-button>
          <el-button
            icon="Refresh"
            @click="
              bomQueryParams.bomName = null;
              getBomList();
            "
            >重置</el-button
          >
        </el-form-item>
      </el-form>
      <el-table v-loading="bomLoading" :data="bomList" @row-dblclick="selectBom" highlight-current-row height="380">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="BOM版本号" align="center" prop="bomVersion" />
        <el-table-column label="BOM名称" align="center" prop="bomName" show-overflow-tooltip />
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="140">
          <template #default="scope">
            <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectBom(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="bomTotal > 0"
        :total="bomTotal"
        v-model:page="bomQueryParams.pageNum"
        v-model:limit="bomQueryParams.pageSize"
        @pagination="getBomList"
      />
      <template #footer>
        <el-button @click="bomSelectOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PrdSubcontractForm">
import { getSubcontract, addSubcontract, updateSubcontract, getMaterialInfo } from '@/api/erp/prd/subcontract';
import { listVendor } from '@/api/erp/bd/vendor';
import { listBom } from '@/api/erp/bd/bom';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { previewCode } from '@/api/system/autocode/rule';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { useRoute } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();

const title = ref('');
const isViewMode = ref(false);
const autoGenFlag = ref(false);
const subcontractRef = ref<any>();
const materialSelectDialogRef = ref<any>();
const warehouseOptions = ref<any[]>([]);

// BOM 回显(名称/版本仅展示, 订单落库 bomId/routingId)
const bomDisplay = ref('');
const bomVersionDisplay = ref('');

const vendorSelectOpen = ref(false);
const vendorLoading = ref(false);
const vendorList = ref<any[]>([]);
const vendorTotal = ref(0);
const vendorQueryParams = ref<any>({ pageNum: 1, pageSize: 10, vendorName: null });

const bomSelectOpen = ref(false);
const bomLoading = ref(false);
const bomList = ref<any[]>([]);
const bomTotal = ref(0);
const bomQueryParams = ref<any>({ pageNum: 1, pageSize: 10, bomName: null });

const form = ref<any>({
  subcontractId: null,
  subcontractNo: null,
  vendorId: null,
  vendorCode: null,
  vendorName: null,
  materialId: null,
  materialCode: null,
  materialName: null,
  materialSpec: null,
  baseUomName: null,
  bomId: null,
  routingId: null,
  processRequirement: null,
  plannedQty: null,
  unitPrice: null,
  totalAmount: null,
  planStartDate: null,
  planEndDate: null,
  targetWarehouseId: null,
  targetWarehouseName: null,
  remark: null
});

const rules = {
  vendorId: [{ required: true, message: '供应商不能为空', trigger: 'change' }],
  materialId: [{ required: true, message: '物料不能为空', trigger: 'change' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
};

const parseTime = (proxy as any)?.parseTime;

onMounted(() => {
  const id = route.query.subcontractId as string;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (id) {
    title.value = isViewMode.value ? '查看委外订单' : '编辑委外订单';
    getInfo(id);
  } else {
    title.value = '新增委外订单';
  }
  listWarehouse({ pageNum: 1, pageSize: 100 })
    .then((res: any) => {
      warehouseOptions.value = res.rows || [];
    })
    .catch(() => {
      warehouseOptions.value = [];
    });
});

function getInfo(id: string | number) {
  getSubcontract(id).then((res: any) => {
    form.value = res.data;
    if (form.value.bomId) {
      refreshBomDisplay(form.value.bomId);
    }
  });
}

/** 编辑回显: 按物料当前生效BOM匹配名称, 不一致时展示ID */
function refreshBomDisplay(bomId: number | string) {
  if (!form.value.materialId) {
    bomDisplay.value = String(bomId);
    return;
  }
  getMaterialInfo(form.value.materialId)
    .then((res: any) => {
      const bom = res.data?.bom;
      if (bom && bom.bomId === bomId) {
        bomDisplay.value = bom.bomName || '当前生效BOM';
        bomVersionDisplay.value = bom.bomVersion || '-';
      } else {
        bomDisplay.value = `BOM#${bomId}`;
        bomVersionDisplay.value = '-';
      }
    })
    .catch(() => {
      bomDisplay.value = `BOM#${bomId}`;
    });
}

/** 单号生成开关: 预览编码(提交时清空, 由后端编码规则兜底生成, 避免预览消耗流水号) */
function handleAutoGenChange(flag: boolean) {
  if (flag) {
    previewCode('PRD_SUBCONTRACT_CODE').then((response: any) => {
      form.value.subcontractNo = response.data || response;
    });
  } else {
    form.value.subcontractNo = null;
  }
}

// ==================== 供应商选择 ====================
function openVendorSelect() {
  vendorSelectOpen.value = true;
  vendorQueryParams.value.pageNum = 1;
  getVendorList();
}

function getVendorList() {
  vendorLoading.value = true;
  listVendor({ ...vendorQueryParams.value })
    .then((res: any) => {
      vendorList.value = res.rows || [];
      vendorTotal.value = res.total || 0;
      vendorLoading.value = false;
    })
    .catch(() => {
      vendorLoading.value = false;
    });
}

function resetVendorQuery() {
  vendorQueryParams.value.vendorName = null;
  getVendorList();
}

function selectVendor(row: any) {
  form.value.vendorId = row.vendorId;
  form.value.vendorCode = row.vendorCode;
  form.value.vendorName = row.vendorName;
  vendorSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已选择供应商：${row.vendorName}`);
}

// ==================== 物料选择 ====================
function openMaterialSelect() {
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调: 带出物料并加载当前生效BOM */
function handleMaterialSelected(row: any) {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  form.value.materialSpec = row.materialSpec;
  form.value.baseUomName = row.baseUomName || form.value.baseUomName;
  bomDisplay.value = '';
  bomVersionDisplay.value = '';
  form.value.bomId = null;
  form.value.routingId = null;
  if (row.materialId) {
    getMaterialInfo(row.materialId)
      .then((res: any) => {
        const bom = res.data?.bom;
        if (bom) {
          form.value.bomId = bom.bomId;
          form.value.routingId = bom.routingId || null;
          bomDisplay.value = bom.bomName || '当前生效BOM';
          bomVersionDisplay.value = bom.bomVersion || '-';
        }
      })
      .catch(() => {});
  }
  proxy.$modal.msgSuccess(`已选择物料：${row.materialName}`);
}

// ==================== BOM 选择 ====================
function openBomSelect() {
  bomSelectOpen.value = true;
  bomQueryParams.value.pageNum = 1;
  getBomList();
}

function getBomList() {
  bomLoading.value = true;
  listBom({ ...bomQueryParams.value, parentMaterialId: form.value.materialId, isCurrent: 1, status: 1 })
    .then((response: any) => {
      bomList.value = response.rows || [];
      bomTotal.value = response.total || 0;
      bomLoading.value = false;
    })
    .catch(() => {
      bomLoading.value = false;
    });
}

function selectBom(row: any) {
  form.value.bomId = row.bomId;
  form.value.routingId = row.routingId || null;
  bomDisplay.value = row.bomName || '当前生效BOM';
  bomVersionDisplay.value = row.bomVersion || '-';
  bomSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已选择BOM：${row.bomName || row.bomId}`);
}

/** 选中仓库后回填名称 */
function onWarehouseChange(warehouseId: number) {
  const w = warehouseOptions.value.find((i: any) => i.warehouseId === warehouseId);
  form.value.targetWarehouseName = w?.warehouseName || null;
}

/** 保存 */
function submitForm() {
  (proxy.$refs['subcontractRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    // 生成开关打开时清空单号, 由后端编码规则兜底生成, 避免预览消耗流水号
    if (autoGenFlag.value) {
      form.value.subcontractNo = undefined;
    }
    if (form.value.targetWarehouseId) {
      onWarehouseChange(form.value.targetWarehouseId);
    }
    const req = form.value.subcontractId ? updateSubcontract(form.value) : addSubcontract(form.value);
    req
      .then(() => {
        proxy.$modal.msgSuccess(form.value.subcontractId ? '修改成功' : '新增成功');
        proxy.$tab.closeOpenPage({ path: '/erp/prdSub/subcontract' });
      })
      .catch(() => {});
  });
}

/** 取消返回列表 */
function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/prdSub/subcontract' });
}
</script>

<style scoped>
.card-title {
  font-size: 16px;
  font-weight: 600;
}
</style>
