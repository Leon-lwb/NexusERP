<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备编码" prop="equipmentCode">
            <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="设备名称" prop="equipmentName">
            <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-table v-loading="loading" :data="equipmentList" show-overflow-tooltip>
      <el-table-column label="设备编码" prop="equipmentCode" />
      <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
      <!-- 技术债:状态暂无对应字典,使用硬编码 el-tag + statusMap,待补字典后改 dict-tag -->
      <el-table-column label="状态" prop="status">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'NORMAL' ? 'success' : 'danger'">
            {{ statusMap[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近点检" prop="lastInspection">
        <template #default="scope">
          {{ parseLastInspectionTime(scope.row.lastInspection) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button type="primary" link @click="handleInspect(scope.row)">点检</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 点检对话框 -->
    <el-dialog v-model="inspectOpen" :title="`设备点检 - ${currentEquipment?.equipmentName}`" width="700px" append-to-body>
      <el-form ref="inspectForm" :model="inspectForm" label-width="120px">
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
        <el-button type="primary" @click="submitInspect">确 定</el-button>
        <el-button @click="inspectOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EquipmentInspection">
import { listEquipmentForInspection, submitInspection, type InspectionItemResult } from '@/api/mes/eq/inspection';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const loading = ref(false);
const showSearch = ref(true);
const equipmentList = ref<any[]>([]);
const inspectOpen = ref(false);
const currentEquipment = ref<any>(null);

const statusMap: Record<string, string> = { NORMAL: '正常', FAULT: '故障', MAINTENANCE: '维修中', SCRAPPED: '已报废' };

const queryParams = reactive({ equipmentCode: '', equipmentName: '' });

const inspectForm = reactive<{ items: InspectionItemResult[]; overallPass: boolean; remark: string }>({
  items: [],
  overallPass: true,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listEquipmentForInspection(queryParams);
    equipmentList.value = res.rows || [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => getList();
const resetQuery = () => {
  proxy?.resetForm('queryForm');
  handleQuery();
};

const parseLastInspectionTime = (json?: string): string => {
  if (!json) return '从未点检';
  try {
    const obj = JSON.parse(json);
    return obj.inspectTime || '未知时间';
  } catch {
    return '解析失败';
  }
};

const handleInspect = (row: any) => {
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

getList();
</script>

<style scoped lang="scss">
.inspect-row {
  margin-bottom: 10px;
}
.empty-tip {
  margin-bottom: 15px;
}
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
