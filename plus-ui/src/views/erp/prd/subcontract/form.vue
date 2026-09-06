<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm">保 存</el-button>
            <el-button @click="cancel">取 消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="subcontractRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="委外单号" prop="subcontractNo">
              <el-input v-model="form.subcontractNo" placeholder="留空由系统自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorId">
              <el-select
                v-model="form.vendorId"
                filterable
                remote
                :remote-method="searchVendor"
                placeholder="请选择供应商"
                style="width: 100%"
                @change="onVendorChange"
              >
                <el-option v-for="v in vendorOptions" :key="v.vendorId" :label="v.vendorName" :value="v.vendorId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="加工单价" prop="unitPrice">
              <el-input-number v-model="form.unitPrice" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">产出物料与 BOM</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料" prop="materialId">
              <el-select
                v-model="form.materialId"
                filterable
                remote
                :remote-method="searchMaterial"
                placeholder="请选择 PCBA 物料"
                style="width: 100%"
                @change="onMaterialChange"
              >
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode} - ${m.materialName}`" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联BOM" prop="bomId">
              <el-select v-model="form.bomId" placeholder="选择物料后带出当前生效BOM" clearable style="width: 100%" @change="onBomChange">
                <el-option v-for="b in bomOptions" :key="b.bomId" :label="`${b.bomName || 'BOM'} V${b.bomVersion || '-'}`" :value="b.bomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="form.plannedQty" :precision="4" :min="0" style="width: 100%" />
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
  </div>
</template>

<script setup lang="ts" name="PrdSubcontractForm">
import { getSubcontract, addSubcontract, updateSubcontract, getMaterialInfo } from '@/api/erp/prd/subcontract';
import { listVendor } from '@/api/erp/bd/vendor';
import { listMaterial } from '@/api/erp/bd/material';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const title = ref('');
const subcontractRef = ref<any>();
const vendorOptions = ref<any[]>([]);
const materialOptions = ref<any[]>([]);
const bomOptions = ref<any[]>([]);
const warehouseOptions = ref<any[]>([]);

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

onMounted(() => {
  const id = route.query.subcontractId as string;
  const action = route.query.action as string;
  if (id) {
    title.value = '编辑委外订单';
    getInfo(id);
  } else if (action === 'add') {
    title.value = '新增委外订单';
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
    // 编辑时预置供应商/物料下拉选项
    if (form.value.vendorId) {
      vendorOptions.value = [{ vendorId: form.value.vendorId, vendorCode: form.value.vendorCode, vendorName: form.value.vendorName }];
    }
    if (form.value.materialId) {
      materialOptions.value = [
        {
          materialId: form.value.materialId,
          materialCode: form.value.materialCode,
          materialName: form.value.materialName,
          materialSpec: form.value.materialSpec,
          baseUomName: form.value.baseUomName
        }
      ];
      loadBomOptions(Number(form.value.materialId), form.value.bomId);
    }
  });
}

/** 远程搜索供应商 */
const searchVendor = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listVendor({ vendorName: query || undefined, pageNum: 1, pageSize: 20 });
    vendorOptions.value = res.rows || [];
  } catch {
    vendorOptions.value = [];
  }
};

/** 远程搜索物料 */
const searchMaterial = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listMaterial({ materialName: query || undefined, pageNum: 1, pageSize: 20 });
    materialOptions.value = res.rows || [];
  } catch {
    materialOptions.value = [];
  }
};

/** 选中供应商后回填编码/名称 */
const onVendorChange = (vendorId: number) => {
  const v = vendorOptions.value.find((i: any) => i.vendorId === vendorId);
  if (v) {
    form.value.vendorCode = v.vendorCode;
    form.value.vendorName = v.vendorName;
  }
};

/** 选中物料后回填物料信息 + 带出当前生效 BOM */
const onMaterialChange = (materialId: number) => {
  const m = materialOptions.value.find((i: any) => i.materialId === materialId);
  if (m) {
    form.value.materialCode = m.materialCode;
    form.value.materialName = m.materialName;
    form.value.materialSpec = m.materialSpec;
    form.value.baseUomName = m.baseUomName;
  }
  loadBomOptions(materialId, null);
};

function loadBomOptions(materialId: number, preferBomId: number | null) {
  bomOptions.value = [];
  form.value.bomId = null;
  form.value.routingId = null;
  if (!materialId) return;
  getMaterialInfo(materialId)
    .then((res: any) => {
      const data = res.data || {};
      bomOptions.value = data.bom ? [data.bom] : [];
      if (preferBomId) {
        form.value.bomId = preferBomId;
      } else if (data.bom) {
        form.value.bomId = data.bom.bomId;
        form.value.routingId = data.bom.routingId || null;
      }
    })
    .catch(() => {});
}

/** 选中 BOM 后带出工艺路线 */
const onBomChange = (bomId: number) => {
  const b = bomOptions.value.find((i: any) => i.bomId === bomId);
  form.value.routingId = b?.routingId || null;
};

/** 选中仓库后回填名称 */
const onWarehouseChange = (warehouseId: number) => {
  const w = warehouseOptions.value.find((i: any) => i.warehouseId === warehouseId);
  form.value.targetWarehouseName = w?.warehouseName || null;
};

/** 保存 */
function submitForm() {
  subcontractRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    // 收货仓库名称回填
    if (form.value.targetWarehouseId) {
      onWarehouseChange(form.value.targetWarehouseId);
    }
    const req = form.value.subcontractId ? updateSubcontract(form.value) : addSubcontract(form.value);
    req
      .then(() => {
        proxy.$modal.msgSuccess(form.value.subcontractId ? '修改成功' : '新增成功');
        router.push('/erp/prdSub/subcontract');
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
