<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备编码" prop="equipmentCode">
            <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="设备名称" prop="equipmentName">
            <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
      <el-table-column label="状态" prop="status" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_equipment_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="最近点检" prop="lastInspection" min-width="150">
        <template #default="scope">
          {{ parseLastInspectionTime(scope.row.lastInspection) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="120" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button type="primary" link @click="handleInspect(scope.row)">点检</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 点检对话框 -->
    <el-dialog v-model="inspectOpen" :title="`设备点检 - ${currentEquipment?.equipmentName}`" width="640px" append-to-body>
      <el-form ref="inspectFormRef" :model="inspectForm" label-width="120px">
        <div v-if="inspectForm.items.length === 0" class="empty-tip">
          <el-alert title="该设备未配置点检项目(inspection_items 为空), 可直接提交空点检" type="info" :closable="false" />
        </div>
        <div v-else>
          <el-row v-for="(item, idx) in inspectForm.items" :key="idx" :gutter="10" class="inspect-row">
            <el-col :span="6">
              <el-form-item :label="`项目${idx + 1}`">
                <span>{{ item.item }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="标准">
                <span>{{ item.standard }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实际值">
                <el-input v-model="item.actual" placeholder="请输入实际值" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="合格">
                <el-switch v-model="item.pass" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
        <el-form-item label="总体合格">
          <el-switch v-model="inspectForm.overallPass" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="点检备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitInspect()">确 定</el-button>
        <el-button @click="inspectOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EquipmentInspection">
import { listEquipmentForInspection, submitInspection, type InspectionItemResult, type EquipmentInspectionVO } from '@/api/mes/eq/inspection';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_equipment_status } = toRefs<any>(proxy?.useDict('mes_equipment_status'));

// 点检为列表+行内动作页（无新增/修改/删除表单），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const formRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  equipmentCode: '',
  equipmentName: ''
});

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud<EquipmentInspectionVO, any>({
  apis: { list: listEquipmentForInspection, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'equipmentId',
  entityName: '设备点检',
  formRef,
  queryParams,
  initForm: () => ({})
});

const parseLastInspectionTime = (json?: string): string => {
  if (!json) return '从未点检';
  try {
    const obj = JSON.parse(json);
    return obj.inspectTime || '未知时间';
  } catch {
    return '解析失败';
  }
};

/** 点检弹窗(本地保留: 解析 inspection_items JSON 逐项填报) */
const inspectOpen = ref(false);
const currentEquipment = ref<EquipmentInspectionVO>();
const inspectFormRef = ref<FormInstance>();
const inspectForm = reactive<{ items: InspectionItemResult[]; overallPass: boolean; remark: string }>({
  items: [],
  overallPass: true,
  remark: ''
});

const handleInspect = (row: EquipmentInspectionVO) => {
  currentEquipment.value = row;
  // 解析 inspection_items JSON
  inspectForm.items = [];
  if (row.inspectionItems) {
    try {
      const items = JSON.parse(row.inspectionItems);
      inspectForm.items = items.map((i: any) => ({
        item: i.item || '',
        standard: i.standard || '',
        actual: '',
        pass: true
      }));
    } catch {
      proxy?.$modal.msgError('点检项目 JSON 解析失败');
    }
  }
  inspectForm.overallPass = true;
  inspectForm.remark = '';
  inspectOpen.value = true;
};

const submitInspect = async () => {
  if (!currentEquipment.value) return;
  await proxy?.$modal.confirm('确认提交点检结果?');
  await submitInspection(currentEquipment.value.equipmentId, {
    results: inspectForm.items,
    overallPass: inspectForm.overallPass,
    remark: inspectForm.remark
  });
  proxy?.$modal.msgSuccess('点检提交成功');
  inspectOpen.value = false;
  getList();
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>

<style scoped lang="scss">
.inspect-row {
  margin-bottom: 10px;
}
.empty-tip {
  margin-bottom: 15px;
}
</style>
