<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="来源" prop="source">
            <el-select v-model="queryParams.source" placeholder="请选择来源" clearable style="width: 100%">
              <el-option v-for="dict in mes_data_source" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleOpenAdd()" v-hasPermi="['mes:dc:record:add']">录入数据</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="采集点编码" prop="pointCode" min-width="140" />
      <el-table-column label="数值" prop="numericValue" align="right" min-width="90" />
      <el-table-column label="文本值" prop="textValue" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.textValue || '-' }}</template>
      </el-table-column>
      <el-table-column label="采集时间" prop="collectTime" width="160" />
      <el-table-column label="来源" prop="source" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_data_source" :value="scope.row.source" />
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 录入数据对话框(采集点/工单/流转卡三级选择, 特殊功能本地保留) -->
    <el-dialog v-model="addDialog.visible" title="录入采集数据" width="500px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="采集点" required>
          <el-input v-model="addPointCode" placeholder="请选择采集点" readonly>
            <template #append>
              <el-button icon="Search" @click="openPointSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="数值">
          <el-input-number v-model="addForm.numericValue" :precision="4" controls-position="right" style="width: 100%" placeholder="数值(用于SPC)" />
        </el-form-item>
        <el-form-item label="文本值">
          <el-input v-model="addForm.textValue" type="textarea" :rows="2" placeholder="文本值(可选)" />
        </el-form-item>
        <el-form-item label="关联工单">
          <el-input v-model="addWoNo" placeholder="可选, 选择工单" readonly clearable @clear="addForm.mesWoId = undefined">
            <template #append>
              <el-button icon="Search" @click="openWoSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="关联流转卡">
          <el-input v-model="addCardNo" placeholder="可选, 选择流转卡" readonly clearable @clear="addForm.cardId = undefined">
            <template #append>
              <el-button icon="Search" @click="openCardSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="采集时间">
          <el-date-picker
            v-model="addForm.collectTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空默认当前时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd()">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 采集点选择对话框 -->
    <el-dialog v-model="pointDialog.visible" title="选择采集点" width="800px" append-to-body>
      <el-form :model="pointDialog.query" :inline="true">
        <el-form-item label="采集点编码">
          <el-input v-model="pointDialog.query.pointCode" clearable @keyup.enter="queryPoints()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryPoints()">搜索</el-button>
          <el-button icon="Refresh" @click="resetPointQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pointDialog.list" height="400" @row-dblclick="selectPoint">
        <el-table-column label="采集点编码" prop="pointCode" min-width="140" />
        <el-table-column label="采集点名称" prop="pointName" min-width="140" />
        <el-table-column label="类型" prop="pointType" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="dc_collection_point_type" :value="scope.row.pointType" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectPoint(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="pointDialog.total > 0"
        :total="pointDialog.total"
        v-model:page="pointDialog.query.pageNum"
        v-model:limit="pointDialog.query.pageSize"
        @pagination="queryPoints"
      />
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="960px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号">
          <el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWorkOrders()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryWorkOrders()">搜索</el-button>
          <el-button icon="Refresh" @click="resetWoQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="400" @row-dblclick="selectWorkOrder">
        <el-table-column label="工单编号" prop="woNo" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="woStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWorkOrders"
      />
    </el-dialog>

    <!-- 流转卡选择对话框 -->
    <el-dialog v-model="cardDialog.visible" title="选择流转卡" width="960px" append-to-body>
      <el-form :model="cardDialog.query" :inline="true">
        <el-form-item label="流转卡号">
          <el-input v-model="cardDialog.query.cardNo" clearable @keyup.enter="queryFlowCards()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryFlowCards()">搜索</el-button>
          <el-button icon="Refresh" @click="resetCardQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="cardDialog.list" height="400" @row-dblclick="selectFlowCard">
        <el-table-column label="流转卡号" prop="cardNo" min-width="140" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="cardStatus" width="100" align="center" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectFlowCard(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="cardDialog.total > 0"
        :total="cardDialog.total"
        v-model:page="cardDialog.query.pageNum"
        v-model:limit="cardDialog.query.pageSize"
        @pagination="queryFlowCards"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesDcDataRecord">
import { listDataRecord, addDataRecord } from '@/api/mes/dc/data-record';
import type { DcDataRecordVO, DcDataRecordQuery, DcDataRecordForm } from '@/api/mes/dc/data-record';
import { listCollectionPoint } from '@/api/mes/dc/collection-point';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import { listFlowCard } from '@/api/mes/pro/flowcardApi';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_data_source, dc_collection_point_type, prd_wo_status } = toRefs<any>(
  proxy?.useDict('mes_data_source', 'dc_collection_point_type', 'prd_wo_status')
);

const queryParams = ref<DcDataRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  source: undefined
});

// 数据记录为记录类只读列表(录入数据为带三级选择器的特殊功能, 本地保留；
// get/update/del 后端无对应标准接口，占位仅为满足 useCrud 的 API 签名，不会被调用)
const readonlyApi = () => Promise.resolve({ data: {} as any });

const { loading, list, total, showSearch, getList, handleQuery } = useCrud({
  apis: { list: listDataRecord, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'recordId',
  entityName: '采集数据记录',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

const addDialog = reactive({ visible: false });
const addForm = reactive<DcDataRecordForm>({
  pointId: undefined as unknown as number,
  numericValue: undefined,
  textValue: '',
  mesWoId: undefined,
  cardId: undefined,
  collectTime: undefined,
  remark: ''
});
// 选择器展示字段(后端 BO 忽略)
const addPointCode = ref('');
const addWoNo = ref('');
const addCardNo = ref('');

const pointDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, pointCode: '' as string }
});
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' as string }
});
const cardDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, cardNo: '' as string }
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

const handleOpenAdd = () => {
  Object.assign(addForm, {
    pointId: undefined as unknown as number,
    numericValue: undefined,
    textValue: '',
    mesWoId: undefined,
    cardId: undefined,
    collectTime: undefined,
    remark: ''
  });
  addPointCode.value = '';
  addWoNo.value = '';
  addCardNo.value = '';
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.pointId) {
    proxy?.$modal.msgWarning('请选择采集点');
    return;
  }
  await addDataRecord(addForm);
  proxy?.$modal.msgSuccess('录入成功');
  addDialog.visible = false;
  getList();
};

// ============ 采集点选择 ============
const openPointSelect = () => {
  pointDialog.visible = true;
  queryPoints();
};
const queryPoints = () => {
  listCollectionPoint(pointDialog.query as any).then((res: any) => {
    pointDialog.list = res.rows || [];
    pointDialog.total = res.total || 0;
  });
};
const resetPointQuery = () => {
  pointDialog.query = { pageNum: 1, pageSize: 10, pointCode: '' };
  queryPoints();
};
const selectPoint = (row: any) => {
  addForm.pointId = row.pointId;
  addPointCode.value = row.pointCode;
  pointDialog.visible = false;
};

// ============ 工单选择 ============
const openWoSelect = () => {
  woDialog.visible = true;
  queryWorkOrders();
};
const queryWorkOrders = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const resetWoQuery = () => {
  woDialog.query = { pageNum: 1, pageSize: 10, woNo: '' };
  queryWorkOrders();
};
const selectWorkOrder = (row: any) => {
  addForm.mesWoId = row.mesWoId;
  addWoNo.value = row.woNo;
  woDialog.visible = false;
};

// ============ 流转卡选择 ============
const openCardSelect = () => {
  cardDialog.visible = true;
  queryFlowCards();
};
const queryFlowCards = () => {
  listFlowCard(cardDialog.query as any).then((res: any) => {
    cardDialog.list = res.rows || [];
    cardDialog.total = res.total || 0;
  });
};
const resetCardQuery = () => {
  cardDialog.query = { pageNum: 1, pageSize: 10, cardNo: '' };
  queryFlowCards();
};
const selectFlowCard = (row: any) => {
  addForm.cardId = row.cardId;
  addCardNo.value = row.cardNo;
  cardDialog.visible = false;
};

getList();
</script>
