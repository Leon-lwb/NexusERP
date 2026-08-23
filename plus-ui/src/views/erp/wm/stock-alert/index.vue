<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="预警类型" prop="alertType">
            <el-select v-model="queryParams.alertType" placeholder="请选择预警类型" clearable style="width: 100%">
              <el-option v-for="dict in wm_stock_alert_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <!-- 技术债：handledFlag 无对应字典，暂用硬编码 el-option，待补字典后改 useDict -->
          <el-form-item label="处理状态" prop="handledFlag">
            <el-select v-model="queryParams.handledFlag" placeholder="请选择处理状态" clearable style="width: 100%">
              <el-option label="未处理" :value="0" />
              <el-option label="已处理" :value="1" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" @click="handleCheck()" v-hasPermi="['wm:stockAlert:edit']">执行检查</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:stockAlert:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:stockAlert:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="stockAlertList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" />
      <el-table-column label="当前库存" align="center" prop="currentQty" />
      <el-table-column label="安全库存" align="center" prop="safetyStock" />
      <el-table-column label="最低库存" align="center" prop="minStock" />
      <el-table-column label="最高库存" align="center" prop="maxStock" />
      <el-table-column label="预警类型" align="center" prop="alertType">
        <template #default="scope">
          <dict-tag :options="wm_stock_alert_type" :value="scope.row.alertType" />
        </template>
      </el-table-column>
      <el-table-column label="预警级别" align="center" prop="alertLevel">
        <template #default="scope">
          <dict-tag :options="wm_alert_level" :value="scope.row.alertLevel" />
        </template>
      </el-table-column>
      <!-- 技术债：handledFlag 无对应字典，暂用硬编码 el-tag，待补字典后改 dict-tag -->
      <el-table-column label="处理状态" align="center" prop="handledFlag">
        <template #default="scope">
          <el-tag :type="scope.row.handledFlag === 1 ? 'success' : 'danger'">
            {{ scope.row.handledFlag === 1 ? '已处理' : '未处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="处理" placement="top" v-if="scope.row.handledFlag === 0">
            <el-button link type="success" icon="Select" @click="handleHandle(scope.row)" v-hasPermi="['wm:stockAlert:edit']" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:stockAlert:remove']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="'处理预警'" v-model="handleVisible" width="500px">
      <el-form ref="handleFormRef" :model="handleForm" label-width="100px">
        <el-form-item label="处理说明" prop="handledRemark">
          <el-input v-model="handleForm.handledRemark" type="textarea" placeholder="请输入处理说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitHandle">确 定</el-button>
        <el-button @click="handleVisible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WmStockAlert" lang="ts">
import { listStockAlert, getStockAlert, updateStockAlert, delStockAlert, checkStockAlert, handleStockAlert } from '@/api/erp/wm/stock-alert';
import { WmStockAlertVO, WmStockAlertQuery } from '@/api/erp/wm/stock-alert/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_stock_alert_type, wm_alert_level } = toRefs<any>(proxy?.useDict('wm_stock_alert_type', 'wm_alert_level'));

// 库存预警由系统检查生成，无新增接口（页面亦无新增入口），占位仅为满足 useCrud 的 API 签名
const readonlyApi = () => Promise.resolve({ data: {} as any });

const handleVisible = ref(false);
const handleForm = ref({ alertId: undefined as number | undefined, handledRemark: '' });

const handleFormRef = ref<ElFormInstance>();

const queryParams = ref<WmStockAlertQuery>({ pageNum: 1, pageSize: 10 });

const {
  loading,
  list: stockAlertList,
  total,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listStockAlert, get: getStockAlert, add: readonlyApi, update: updateStockAlert, del: delStockAlert },
  idField: 'alertId',
  entityName: '库存预警',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/wm/stock-alert/export',
  exportFileName: '库存预警'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 执行库存预警检查 */
async function handleCheck() {
  loading.value = true;
  await checkStockAlert();
  proxy?.$modal.msgSuccess('检查完成');
  getList();
}

/** 打开处理预警弹窗 */
function handleHandle(row: WmStockAlertVO) {
  handleForm.value.alertId = row.alertId;
  handleForm.value.handledRemark = '';
  handleVisible.value = true;
}

/** 提交预警处理 */
async function submitHandle() {
  await handleStockAlert(handleForm.value.alertId!, handleForm.value.handledRemark);
  proxy?.$modal.msgSuccess('处理成功');
  handleVisible.value = false;
  getList();
}

onMounted(() => {
  getList();
});
</script>
