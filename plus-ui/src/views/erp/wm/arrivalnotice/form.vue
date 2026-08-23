<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && !props.disabled && form.status === 'DRAFT'">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="arrivalnoticeRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="通知单编号" prop="noticeCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.noticeCode" placeholder="请输入通知单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode || props.disabled"
                >
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="通知单名称" prop="noticeName">
              <el-input v-model="form.noticeName" placeholder="请输入通知单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购订单编号" prop="purOrderCode">
              <el-input v-model="form.purOrderCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">供应商信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商ID" prop="vendorId">
              <el-input v-model="form.vendorId" placeholder="请输入供应商ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商简称" prop="vendorNick">
              <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
          <el-col :span="8"></el-col>
          <el-col :span="8"></el-col>
        </el-row>
        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="到货日期" prop="arrivalDate">
              <el-date-picker clearable v-model="form.arrivalDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择到货日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系方式" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode || props.disabled || form.status !== 'DRAFT'"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单据状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in wm_notice_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 流程选择警告 -->
        <el-alert
          v-if="!isViewMode && !props.disabled && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
          type="warning"
          show-icon
          :closable="false"
          title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 wmArrivalNoticeService。"
          style="margin-bottom: 12px"
        />

        <!-- 子表部分开始 -->
        <el-divider content-position="center">到货通知单行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddWmArrivalNoticeLine" v-if="!isViewMode && !props.disabled">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteWmArrivalNoticeLine" v-if="!isViewMode && !props.disabled">删除</el-button>
          </el-col>
        </el-row>
        <el-table
          :data="wmArrivalNoticeLineList"
          :row-class-name="rowWmArrivalNoticeLineIndex"
          @selection-change="handleWmArrivalNoticeLineSelectionChange"
          ref="wmArrivalNoticeLine"
        >
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && !props.disabled" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="产品物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入产品物料编码" v-if="!isViewMode && !props.disabled" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入产品物料名称" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="baseUomName" width="150">
            <template #default="scope">
              <el-select
                v-model="scope.row.baseUomId"
                placeholder="请选择单位"
                v-if="!isViewMode && !props.disabled"
                filterable
                style="width: 100%"
                @change="(val) => onUomChange(val, scope.row)"
              >
                <el-option v-for="u in uomOptions" :key="u.uomId" :label="u.uomName" :value="String(u.uomId)" />
              </el-select>
              <span v-else>{{ scope.row.baseUomName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="到货数量" prop="quantityArrival" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.quantityArrival" placeholder="请输入到货数量" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.quantityArrival }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合格数量" prop="quantityQuanlified" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.quantityQuanlified" placeholder="请输入合格数量" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.quantityQuanlified }}</span>
            </template>
          </el-table-column>
          <el-table-column label="是否来料检验" prop="iqcCheck" width="150">
            <template #default="scope">
              <el-select v-model="scope.row.iqcCheck" placeholder="请选择是否来料检验" v-if="!isViewMode && !props.disabled">
                <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
              <span v-else>{{ getDictLabel(sys_true_or_false, scope.row.iqcCheck) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来料检验单编号" prop="iqcCode" width="180">
            <template #default="scope">
              <div style="display: flex; align-items: center; gap: 8px" v-if="!isViewMode && !props.disabled">
                <el-input v-model="scope.row.iqcCode" placeholder="请选择来料检验单" readonly style="flex: 1" />
                <el-button type="primary" icon="Search" @click="openIqcSelect(scope.$index)" />
              </div>
              <span v-else>{{ scope.row.iqcCode }}</span>
            </template>
          </el-table-column>
        </el-table>
        <!-- 子表部分结束 -->
      </el-form>
    </el-card>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />

    <!-- IQC选择对话框 -->
    <el-dialog :title="'选择来料检验单'" v-model="iqcSelectOpen" width="1000px" append-to-body>
      <el-form :model="iqcQueryParams" ref="iqcQueryRef" :inline="true" label-width="100px">
        <el-form-item label="物料名称" prop="materialId">
          <span style="color: #606266">{{ iqcCurrentMaterialName || '-' }}</span>
        </el-form-item>
        <el-form-item label="IQC编号">
          <el-input v-model="iqcQueryParams.iqcNo" placeholder="请输入IQC编号" clearable @keyup.enter="handleIqcQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleIqcQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetIqcQuery">重置</el-button>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon style="margin-top: 8px"> 仅显示状态为已完成且检验结果为合格的IQC记录 </el-alert>
      </el-form>

      <el-table v-loading="iqcLoading" :data="iqcList" @row-dblclick="selectIqc" highlight-current-row style="width: 100%" height="400">
        <el-table-column label="IQC编号" prop="iqcNo" width="180" />
        <el-table-column label="物料编码" prop="materialCode" width="140">
          <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料名称" prop="materialName" width="150">
          <template #default="scope">{{ scope.row.materialName || '-' }}</template>
        </el-table-column>
        <el-table-column label="供应商" prop="vendorName" width="150">
          <template #default="scope">{{ scope.row.vendorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验数量" prop="quantity" width="100" align="right" />
        <el-table-column label="检验结果" prop="inspectResult" width="100" align="center">
          <template #default="scope">
            <el-tag type="success" v-if="scope.row.inspectResult === 'QUALIFIED'">合格</el-tag>
            <el-tag type="danger" v-else-if="scope.row.inspectResult === 'UNQUALIFIED'">不合格</el-tag>
            <el-tag v-else>待定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检验员" prop="inspectorName" width="100">
          <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验时间" prop="inspectTime" width="160">
          <template #default="scope">{{ scope.row.inspectTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectIqc(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="iqcTotal > 0"
        :total="iqcTotal"
        v-model:page="iqcQueryParams.pageNum"
        v-model:limit="iqcQueryParams.pageSize"
        @pagination="getIqcList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="iqcSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ArrivalnoticeForm">
import { getArrivalnotice, addArrivalnotice, updateArrivalnotice, submitArrivalnotice, getArrivalnoticeProcId } from '@/api/erp/wm/arrivalnotice';
import { getProcesss } from '@/api/workflow/process/index';
import { getMaterial } from '@/api/erp/bd/material';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { getMeasure, listMeasureAll } from '@/api/erp/bd/measure';
import { listIqc } from '@/api/mes/qc/iqc';

import { useRoute, useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import type { ArrivalnoticeForm } from '@/api/erp/wm/arrivalnotice/types';

const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false, wm_notice_status, bd_material_type } = toRefs<any>(
  proxy.useDict('sys_true_or_false', 'wm_notice_status', 'bd_material_type')
);
const route = useRoute();
const router = useRouter();

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

// 添加物料查询引用
const materialQueryRef = ref();

const form = ref<ArrivalnoticeForm>({});
const autoGenFlag = ref(false);
const isViewMode = ref(false);
const title = ref('');

// 表单禁用状态（考虑 workflow 嵌入和查看模式）
const formDisabled = computed(() => isViewMode.value || props.disabled);

// 添加缺失的响应式变量定义
const wmArrivalNoticeLineList = ref([]);
const checkedWmArrivalNoticeLine = ref([]);

// 物料选择对话框
const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
const currentMaterialRowIndex = ref(-1);

// IQC选择对话框相关
const iqcSelectOpen = ref(false);
const iqcList = ref([]);
const iqcLoading = ref(false);
const iqcTotal = ref(0);
const currentIqcRowIndex = ref(-1);
const iqcCurrentMaterialName = ref('');
const iqcQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  iqcNo: '',
  materialId: undefined,
  status: 'COMPLETED',
  inspectResult: 'QUALIFIED'
});

// 流程相关状态
const loadingFlow = ref(false);
const flowOptions = ref([]);

// 计量单位下拉选项
const uomOptions = ref([]);

/**
 * 流程详情嵌入时：父组件注入 customFormData（含 noticeId）。
 * getBusinessDataById 仅返回主表，这里按 noticeId 拉全量（含子表行）。
 */
watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).noticeId != null) {
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '到货通知（流程）' : '到货通知';
      getInfo(String((val as any).noticeId));
    }
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action;
  // 设置查看模式
  isViewMode.value = action === 'view';

  // 加载计量单位列表
  loadUomList();

  if (id) {
    initPage(id);
  }
});

/** 加载计量单位列表 */
function loadUomList() {
  listMeasureAll()
    .then((response: any) => {
      uomOptions.value = response.data || response.rows || [];
    })
    .catch((error: any) => {
      console.error('获取计量单位列表失败:', error);
      uomOptions.value = [];
    });
}

/** 单位下拉选择变化时同步单位名称 */
function onUomChange(uomId: string, row: any) {
  const found = uomOptions.value.find((u: any) => '' + u.uomId === uomId);
  if (found) {
    row.baseUomName = found.uomName || '';
  } else {
    row.baseUomName = '';
  }
}

function initPage(id: any) {
  const action = route.query.action;

  if (action === 'view') {
    // 查看模式
    isViewMode.value = true;
    title.value = '查看到货通知';
    if (id) {
      getInfo(id);
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false;
    title.value = '修改到货通知';
    if (id) {
      getInfo(id);
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false;
    title.value = '新增到货通知';
    reset();
    loadFlows();
  }
}

// 获取详细信息
function getInfo(id: any) {
  getArrivalnotice(id).then((response: any) => {
    form.value = response.data;
    wmArrivalNoticeLineList.value = response.data.wmArrivalNoticeLineList;
    loadFlows();
  });
}

// 表单重置
function reset() {
  autoGenFlag.value = false;
  form.value = {
    noticeId: null,
    noticeCode: null,
    noticeName: null,
    purOrderCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    arrivalDate: null,
    contact: null,
    phone: null,
    status: null,
    selectFlowId: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  wmArrivalNoticeLineList.value = [];
  proxy.resetForm('arrivalnoticeRef');
}

// 表单校验规则
const rules = {};

/** 提交按钮 */
function submitForm() {
  proxy.$refs['arrivalnoticeRef'].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.noticeCode = undefined;
      }
      form.value.wmArrivalNoticeLineList = wmArrivalNoticeLineList.value;
      if (form.value.noticeId != null) {
        updateArrivalnotice(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/inbound/arrivalnotice');
        });
      } else {
        addArrivalnotice(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/inbound/arrivalnotice');
        });
      }
    }
  });
}

// 取消按钮
//自动生成编号
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_ARRIVAL_NOTICE_CODE').then((response: any) => {
      form.value.noticeCode = response;
    });
  } else {
    form.value.noticeCode = null;
  }
}

function cancel() {
  const obj = { path: '/erp/inbound/arrivalnotice' };
  proxy.$tab.closeOpenPage(obj);
}

// ========== 流程相关方法 ==========

/** 加载可用审批流程列表 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'wmArrivalNoticeService' })
    .then((res: any) => {
      flowOptions.value = res?.data || [];
      tryAutoSelect();
    })
    .catch(() => {
      flowOptions.value = [];
    })
    .finally(() => {
      loadingFlow.value = false;
    });
}

/** 如果只有一个可用流程，自动选中 */
function tryAutoSelect() {
  if (flowOptions.value.length === 1 && !form.value.selectFlowId) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

/** 提交审批 */
function handleSubmitFlow() {
  if (!form.value.noticeId) {
    proxy.$modal.msgWarning('请先保存单据');
    return;
  }
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning('请先选择审批流程并保存');
    return;
  }
  proxy.$modal.confirm('确认提交到货通知审批？').then(() => {
    submitArrivalnotice(String(form.value.noticeId), String(form.value.selectFlowId)).then(() => {
      proxy.$modal.msgSuccess('提交审批成功');
      getInfo(form.value.noticeId);
    });
  });
}

/** 查看流程 */
function onViewFlow() {
  if (!form.value.noticeId) return;
  getArrivalnoticeProcId(String(form.value.noticeId)).then((res: any) => {
    const procInsId = res.data;
    if (!procInsId) {
      proxy.$modal.msgWarning('暂无流程信息');
      return;
    }
    router.push({
      path: '/workflow/process/detail/' + procInsId,
      query: { processed: 'false' }
    });
  });
}

// 物料选择相关方法
/** 打开物料选择对话框 */
function openMaterialSelect(rowIndex: number) {
  currentMaterialRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调 */
function handleMaterialSelected(row: any) {
  const idx = currentMaterialRowIndex.value;
  if (idx >= 0 && wmArrivalNoticeLineList.value[idx]) {
    wmArrivalNoticeLineList.value[idx].materialId = row.materialId;
    wmArrivalNoticeLineList.value[idx].materialCode = row.materialCode;
    wmArrivalNoticeLineList.value[idx].materialName = row.materialName;
    wmArrivalNoticeLineList.value[idx].materialSpec = row.materialSpec;
    if (row.baseUomId) {
      wmArrivalNoticeLineList.value[idx].baseUomId = row.baseUomId;
      getMeasure(row.baseUomId)
        .then((response: any) => {
          if (idx >= 0 && wmArrivalNoticeLineList.value[idx]) {
            wmArrivalNoticeLineList.value[idx].baseUomName = response.data.uomName;
          }
        })
        .catch(() => {
          if (idx >= 0 && wmArrivalNoticeLineList.value[idx]) {
            wmArrivalNoticeLineList.value[idx].baseUomName = '';
          }
        });
    } else {
      wmArrivalNoticeLineList.value[idx].baseUomId = '';
      wmArrivalNoticeLineList.value[idx].baseUomName = '';
    }
  }
  currentMaterialRowIndex.value = -1;
}

// ========== IQC选择相关方法 ==========

/** 打开IQC选择对话框 */
function openIqcSelect(rowIndex: number) {
  currentIqcRowIndex.value = rowIndex;
  if (!wmArrivalNoticeLineList.value || !wmArrivalNoticeLineList.value[rowIndex]) {
    proxy.$modal.msgWarning('当前行数据不存在，请重试');
    return;
  }
  const row = wmArrivalNoticeLineList.value[rowIndex];
  iqcCurrentMaterialName.value = row.materialName || '';
  // 根据当前行的物料编码预填查询条件
  iqcQueryParams.value.materialId = row.materialId || undefined;
  iqcQueryParams.value.status = 'COMPLETED';
  iqcQueryParams.value.inspectResult = 'QUALIFIED';
  iqcQueryParams.value.pageNum = 1;
  iqcSelectOpen.value = true;
  getIqcList();
}

/** 查询IQC列表 */
function getIqcList() {
  iqcLoading.value = true;
  listIqc(iqcQueryParams.value)
    .then((response: any) => {
      iqcList.value = response.rows || [];
      iqcTotal.value = response.total || 0;
    })
    .catch((error: any) => {
      console.error('查询IQC列表失败:', error);
      iqcList.value = [];
      iqcTotal.value = 0;
    })
    .finally(() => {
      iqcLoading.value = false;
    });
}

/** 搜索IQC */
function handleIqcQuery() {
  iqcQueryParams.value.pageNum = 1;
  getIqcList();
}

/** 重置IQC查询 */
function resetIqcQuery() {
  proxy.resetForm('iqcQueryRef');
  iqcQueryParams.value.materialId = undefined;
  iqcQueryParams.value.iqcNo = '';
  iqcQueryParams.value.pageNum = 1;
  iqcQueryParams.value.status = 'COMPLETED';
  iqcQueryParams.value.inspectResult = 'QUALIFIED';
  getIqcList();
}

/** 选择IQC */
function selectIqc(row: any) {
  if (currentIqcRowIndex.value >= 0 && wmArrivalNoticeLineList.value[currentIqcRowIndex.value]) {
    const line = wmArrivalNoticeLineList.value[currentIqcRowIndex.value];
    line.iqcId = row.iqcId || '';
    line.iqcCode = row.iqcNo || '';
  }
  iqcSelectOpen.value = false;
  currentIqcRowIndex.value = -1;
}

/** 到货通知单行序号 */
function rowWmArrivalNoticeLineIndex({ row, rowIndex }: any): string {
  row.index = rowIndex + 1;
  return '';
}

/** 到货通知单行添加按钮操作 */
function handleAddWmArrivalNoticeLine() {
  const obj: any = {};
  obj.materialId = '';
  obj.materialCode = '';
  obj.materialName = '';
  obj.materialSpec = '';
  obj.baseUomId = '';
  obj.baseUomName = '';
  obj.quantityArrival = '';
  obj.quantityQuanlified = '';
  obj.iqcCheck = '';
  obj.iqcId = '';
  obj.iqcCode = '';
  obj.remark = '';
  wmArrivalNoticeLineList.value.push(obj);
}

/** 到货通知单行删除按钮操作 */
function handleDeleteWmArrivalNoticeLine() {
  if (checkedWmArrivalNoticeLine.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的到货通知单行数据');
  } else {
    const wmArrivalNoticeLines = wmArrivalNoticeLineList.value;
    const checkedWmArrivalNoticeLines = checkedWmArrivalNoticeLine.value;
    wmArrivalNoticeLineList.value = wmArrivalNoticeLines.filter(function (item: any) {
      return checkedWmArrivalNoticeLines.indexOf(item.index) == -1;
    });
  }
}

/** 复选框选中数据 */
function handleWmArrivalNoticeLineSelectionChange(selection: any[]) {
  checkedWmArrivalNoticeLine.value = selection.map((item: any) => item.index);
}

/** 获取字典标签 */
function getDictLabel(dictOptions: any, value: any) {
  const dict = dictOptions.find((item: any) => item.value === value);
  return dict ? dict.label : value;
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

.category-tree-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
  height: 500px;
  overflow-y: auto;
}

.category-tree {
  height: 100%;
}

.category-search-input {
  margin-bottom: 10px;
}
</style>
