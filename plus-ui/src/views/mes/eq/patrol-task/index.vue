<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in mes_patrol_status" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
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
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:eq:patrol:task:add']">新增任务</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="任务ID" prop="taskId" width="120" />
      <el-table-column label="路线" prop="routeName" min-width="120" />
      <el-table-column label="计划日期" prop="planDate" align="center" width="120" />
      <el-table-column label="指派人" prop="assignedToName" width="100" />
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="s"><dict-tag :options="mes_patrol_status" :value="s.row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="240" :show-overflow-tooltip="false">
        <template #default="s">
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleExecute(s.row)"
            v-if="s.row.status === 'PENDING' || s.row.status === 'IN_PROGRESS'"
            v-hasPermi="['mes:eq:patrol:task:execute']"
            >执行</el-button
          >
          <el-button link type="primary" icon="Edit" @click="handleUpdate(s.row)" v-hasPermi="['mes:eq:patrol:task:add']">编辑</el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(s.row)"
            v-if="s.row.status !== 'COMPLETED'"
            v-hasPermi="['mes:eq:patrol:task:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改巡检任务对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="路线" prop="routeId">
          <el-select v-model="form.routeId" filterable placeholder="选择路线" style="width: 100%">
            <el-option v-for="r in routeOptions" :key="r.routeId" :label="r.routeName" :value="r.routeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期" prop="planDate">
          <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="指派人">
          <el-input v-model="form.assignedToName" placeholder="指派人姓名" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 执行巡检 -->
    <el-dialog v-model="execDialog.visible" :title="`执行巡检 - ${execDialog.routeName}`" width="640px" append-to-body>
      <el-form label-width="90px">
        <el-table :data="execRecords" border size="small">
          <el-table-column label="设备" min-width="180">
            <template #default="s"><equipment-select v-model="s.row.equipmentId" /></template>
          </el-table-column>
          <el-table-column label="检查项" width="150">
            <template #default="s"><el-input v-model="s.row.checkItem" size="small" /></template>
          </el-table-column>
          <el-table-column label="实际值" width="120">
            <template #default="s"><el-input v-model="s.row.actualValue" size="small" /></template>
          </el-table-column>
          <el-table-column label="结果" width="100">
            <template #default="s">
              <el-select v-model="s.row.result" size="small">
                <el-option label="正常" value="NORMAL" />
                <el-option label="异常" value="ABNORMAL" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="备注" width="150">
            <template #default="s"><el-input v-model="s.row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="照片" width="120">
            <template #default="s">
              <div class="patrol-photo-cell">
                <ImageUpload v-model="s.row.photoOssId" :limit="1" :file-size="5" :is-show-tip="false" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="s"><el-button link type="danger" icon="Delete" size="small" @click="execRecords.splice(s.$index, 1)" /></template>
          </el-table-column>
        </el-table>
        <el-button
          size="small"
          icon="Plus"
          style="margin-top: 8px"
          @click="
            execRecords.push({
              equipmentId: undefined as unknown as number,
              checkItem: '',
              actualValue: '',
              result: 'NORMAL',
              remark: '',
              photoOssId: ''
            })
          "
          >添加记录</el-button
        >
      </el-form>
      <template #footer>
        <el-button @click="execDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="execSubmitting" :disabled="execSubmitting" @click="submitExecute(false)">保存记录</el-button>
        <el-button type="success" :loading="execSubmitting" :disabled="execSubmitting" @click="submitExecute(true)">保存并完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolTask">
import {
  listPatrolTask,
  getPatrolTask,
  addPatrolTask,
  updatePatrolTask,
  delPatrolTask,
  completePatrolTask,
  batchSubmitPatrolRecord,
  listPatrolRoute
} from '@/api/mes/eq/patrol';
import type { EqPatrolTaskVO, EqPatrolTaskQuery, EqPatrolRouteVO, EqPatrolRecordForm } from '@/api/mes/eq/patrol';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';
import ImageUpload from '@/components/ImageUpload/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_patrol_status } = toRefs<any>(proxy?.useDict('mes_patrol_status'));

const formRef = ref<FormInstance>();
const formRules = {
  routeId: [{ required: true, message: '请选择路线', trigger: 'change' }],
  planDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
};

const queryParams = ref<EqPatrolTaskQuery>({ pageNum: 1, pageSize: 10, status: undefined });
const routeOptions = ref<EqPatrolRouteVO[]>([]);

const {
  loading,
  list: dataList,
  total,
  open,
  title,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleDelete,
  submitForm,
  cancel
} = useCrud<EqPatrolTaskVO, EqPatrolTaskQuery>({
  apis: { list: listPatrolTask, get: getPatrolTask, add: addPatrolTask, update: updatePatrolTask, del: delPatrolTask },
  idField: 'taskId',
  entityName: '巡检任务',
  formRef,
  queryParams,
  initForm: () => ({ routeId: undefined as unknown as number, planDate: '', assignedToName: '', remark: '' })
});

/** 执行巡检弹窗(本地保留: 行内明细编辑 + 照片上传 + 保存/完成两段提交) */
const execDialog = reactive({ visible: false, taskId: 0, routeId: 0, routeName: '' });
const execRecords = ref<Array<{ equipmentId?: number; checkItem: string; actualValue: string; result: string; remark: string; photoOssId?: string }>>(
  []
);
const execSubmitting = ref(false);

const loadRoutes = async () => {
  const r = await listPatrolRoute({ status: 'ACTIVE' } as any);
  routeOptions.value = r.rows || [];
};

const handleExecute = (row: EqPatrolTaskVO) => {
  execDialog.taskId = row.taskId;
  execDialog.routeId = row.routeId;
  execDialog.routeName = row.routeName || '';
  execRecords.value = [{ equipmentId: undefined as unknown as number, checkItem: '', actualValue: '', result: 'NORMAL', remark: '', photoOssId: '' }];
  execDialog.visible = true;
};

const submitExecute = async (complete: boolean) => {
  const valid = execRecords.value.filter((r) => r.equipmentId);
  if (valid.length === 0) {
    proxy?.$modal.msgWarning('请至少填写一条带设备的记录');
    return;
  }
  // 部分行填了设备但未填巡检项: 提示但不阻断(允许只记录设备到访)
  const noCheckItem = valid.filter((r) => !r.checkItem || !r.checkItem.trim());
  if (noCheckItem.length > 0) {
    proxy?.$modal.msgWarning('存在未填写巡检项的记录, 请确认');
  }
  const bos: EqPatrolRecordForm[] = valid.map((r) => ({ ...r, taskId: execDialog.taskId, routeId: execDialog.routeId }) as any);
  execSubmitting.value = true;
  try {
    await batchSubmitPatrolRecord(bos);
    if (complete) {
      await completePatrolTask(execDialog.taskId);
    }
    proxy?.$modal.msgSuccess(complete ? '巡检完成' : '记录已保存');
    execDialog.visible = false;
    getList();
  } catch {
    // 记录已保存但完成失败时, 保留弹窗让用户重试完成(避免重复提交导致重复记录)
    proxy?.$modal.msgError(complete ? '记录已保存, 但完成任务失败, 请重试完成或检查任务状态' : '保存记录失败');
  } finally {
    execSubmitting.value = false;
  }
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
loadRoutes();
</script>
<style scoped lang="scss">
/* 巡检照片上传: 缩小 picture-card 以适应表格行 */
.patrol-photo-cell :deep(.el-upload--picture-card) {
  width: 80px;
  height: 80px;
}
.patrol-photo-cell :deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 80px;
  height: 80px;
}
</style>
