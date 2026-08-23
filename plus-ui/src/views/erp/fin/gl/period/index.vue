<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="会计年度" prop="fiscalYear">
            <el-input v-model="queryParams.fiscalYear" placeholder="如 2026" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in gl_period_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Calendar" @click="handleGenerate()" v-hasPermi="['fin:gl:period:edit']">生成年度期间</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="会计年度" align="center" prop="fiscalYear" width="100" />
      <el-table-column label="期间号" align="center" prop="periodNo" width="80" />
      <el-table-column label="期间名称" align="center" prop="periodName" />
      <el-table-column label="开始日期" align="center" prop="startDate" width="120" />
      <el-table-column label="结束日期" align="center" prop="endDate" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="gl_period_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="warning"
            icon="Lock"
            @click="handleAction(scope.row, 'close')"
            v-if="scope.row.status === 'OPEN'"
            v-hasPermi="['fin:gl:period:edit']"
            >关账</el-button
          >
          <el-button
            link
            type="danger"
            icon="Lock"
            @click="handleAction(scope.row, 'lock')"
            v-if="scope.row.status === 'CLOSED'"
            v-hasPermi="['fin:gl:period:edit']"
            >锁定</el-button
          >
          <el-button
            link
            type="success"
            icon="Unlock"
            @click="handleAction(scope.row, 'reopen')"
            v-if="scope.row.status === 'CLOSED'"
            v-hasPermi="['fin:gl:period:edit']"
            >反结账</el-button
          >
          <span v-if="scope.row.status === 'LOCKED'" style="color: #909399">已锁定</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="生成年度期间" v-model="genOpen" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="会计年度">
          <el-input-number v-model="genYear" :min="2020" :max="2099" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitGenerate">确 定</el-button>
          <el-button @click="genOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="GlPeriod">
import { listPeriod, closePeriod, lockPeriod, reopenPeriod, generateYearPeriods } from '@/api/erp/fin/gl/period';
import { PeriodVO, PeriodQuery } from '@/api/erp/fin/gl/period/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { gl_period_status } = toRefs<any>(proxy.useDict('gl_period_status'));

const genOpen = ref(false);
const genYear = ref(new Date().getFullYear());

// 会计期间由"生成年度期间"批量创建，无行级增删改，以下占位仅为满足 useCrud 的 API 签名，不会被调用
const formRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<PeriodQuery>({ pageNum: 1, pageSize: 20, fiscalYear: undefined, status: undefined });

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud<PeriodVO, PeriodQuery>({
  apis: { list: listPeriod, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'periodId',
  entityName: '会计期间',
  formRef,
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleGenerate() {
  genYear.value = new Date().getFullYear();
  genOpen.value = true;
}

async function submitGenerate() {
  await generateYearPeriods(genYear.value);
  proxy.$modal.msgSuccess('生成成功');
  genOpen.value = false;
  getList();
}

function handleAction(row: PeriodVO, action: 'close' | 'lock' | 'reopen') {
  const labels = { close: '关账', lock: '锁定', reopen: '反结账' } as const;
  proxy.$modal.confirm('确认对期间【' + row.periodName + '】执行' + labels[action] + '操作?').then(async () => {
    if (action === 'close') await closePeriod(row.periodId);
    if (action === 'lock') await lockPeriod(row.periodId);
    if (action === 'reopen') await reopenPeriod(row.periodId);
    proxy.$modal.msgSuccess(labels[action] + '成功');
    getList();
  });
}

getList();
</script>
