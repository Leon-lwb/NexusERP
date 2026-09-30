<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <el-button
              type="success"
              @click="handleSubmitFlow"
              v-if="!isViewMode && !props.disabled && (form.status === 'DRAFT' || form.status === 'REJECTED')"
              >提交审批</el-button
            >
            <el-button type="warning" @click="handleConfirmShelf" v-if="pageType === 'shelf' && form.status === 'APPROVED' && !isViewMode"
              >确认上架</el-button
            >
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="receiveRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="入库单编号" prop="receiveCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.receiveCode" placeholder="请输入入库单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
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
            <el-form-item label="入库单名称" prop="receiveName">
              <el-input v-model="form.receiveName" placeholder="请输入入库单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来料检验单编号" prop="iqcCode">
              <el-input v-model="form.iqcCode" placeholder="请输入来料检验单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="到货通知单编号" prop="noticeCode">
              <el-input v-model="form.noticeCode" placeholder="请输入到货通知单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购订单编号" prop="purCode">
              <el-input v-model="form.purCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商简称" prop="vendorNick">
              <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <!-- ERP-PU-06：自动生成的收货草稿 warehouseId 为空，此处强制选择入库仓库 -->
            <el-form-item label="仓库" prop="warehouseId">
              <el-select
                v-model="form.warehouseId"
                placeholder="请选择入库仓库"
                filterable
                clearable
                :disabled="formDisabled"
                style="width: 100%"
                @change="handleReceiveWarehouseChange"
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
          <el-col :span="8">
            <el-form-item label="入库日期" prop="receiveDate">
              <el-date-picker
                clearable
                v-model="form.receiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择入库日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">状态信息</el-divider>
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
              <el-radio-group v-model="form.status" :disabled="formDisabled || form.receiveId != null">
                <el-radio v-for="dict in wm_material_receive_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20" v-if="form.status === 'SHELVED' || form.status === 'FINISHED'">
          <el-col :span="8">
            <el-form-item label="上架人">
              <el-input v-model="form.shelfByName" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上架时间">
              <el-input :value="parseTime(form.shelfTime, '{y}-{m}-{d} {h}:{i}:{s}')" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 流程选择警告 -->
        <el-alert
          v-if="!isViewMode && !props.disabled && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
          type="warning"
          show-icon
          :closable="false"
          title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 wmMaterialReceiveService。"
          style="margin-bottom: 12px"
        />

        <!-- 子表部分开始 -->
        <el-divider content-position="center">物料入库单信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddWmMaterialReceiveLine" v-if="!isViewMode && !props.disabled">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteWmMaterialReceiveLine" v-if="!isViewMode && !props.disabled">删除</el-button>
          </el-col>
        </el-row>
        <el-table
          :data="wmMaterialReceiveLineList"
          :row-class-name="rowWmMaterialReceiveLineIndex"
          @selection-change="handleWmMaterialReceiveLineSelectionChange"
          ref="wmMaterialReceiveLine"
          style="width: 100%"
        >
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && !props.disabled" />
          <el-table-column type="expand" v-if="form.status !== 'DRAFT'">
            <template #default="props">
              <el-table :data="props.row.detailList" border style="width: 90%; margin-left: 50px">
                <el-table-column label="明细ID" prop="detailId" />
                <el-table-column label="产品物料编码" prop="materialCode" />
                <el-table-column label="产品物料名称" prop="materialName" />
                <el-table-column label="规格型号" prop="materialSpec" />
                <el-table-column label="单位" prop="baseUomName" />
                <el-table-column label="入库数量" prop="quantity" />
                <el-table-column label="批次号" prop="batchCode" />
                <el-table-column label="仓库名称" prop="warehouseName" />
                <el-table-column label="库区名称" prop="areaName" />
                <el-table-column label="库位名称" prop="locationName" />
                <el-table-column label="操作" align="center" width="150" v-if="!isViewMode && !props.disabled">
                  <template #default="scope">
                    <el-button link type="primary" @click="handleEditDetail(scope.row, props.row)">修改</el-button>
                    <el-button link type="danger" @click="handleDeleteDetail(scope.row, props.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-table-column>
          <el-table-column label="序号" align="center" prop="index" width="60" />
          <el-table-column label="产品物料编码" prop="materialCode" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入产品物料编码" v-if="!isViewMode && !props.disabled" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品物料名称" prop="materialName" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入产品物料名称" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位名称" prop="baseUomName" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.baseUomName" placeholder="请输入单位名称" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.baseUomName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库数量" prop="quantityReceived" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.quantityReceived" placeholder="请输入入库数量" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.quantityReceived }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" prop="batchCode" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.batchCode" placeholder="请输入批次号" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.batchCode }}</span>
            </template>
          </el-table-column>
          <!-- 上架操作列，只在通过上架按钮进入页面时显示 -->
          <el-table-column label="操作" align="center" min-width="120" v-if="pageType === 'shelf' && !isViewMode">
            <template #default="scope">
              <el-button link type="primary" @click="handleShelf(scope.row)">上架</el-button>
            </template>
          </el-table-column>
        </el-table>
        <!-- 子表部分结束 -->
      </el-form>
    </el-card>

    <!-- 上架弹窗 -->
    <el-dialog :title="shelfTitle" v-model="shelfOpen" width="960px" append-to-body>
      <el-form ref="shelfRef" :model="shelfForm" :rules="shelfRules" label-width="100px" :disabled="isShelfViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料编码" prop="materialCode">
              <div style="display: flex; align-items: center; gap: 6px">
                <el-input v-model="shelfForm.materialCode" placeholder="请输入物料编码" />
                <el-button icon="Camera" title="扫描标签识别料号/批次" @click="labelCaptureRef?.open()" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="shelfForm.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位名称" prop="baseUomName">
              <el-input v-model="shelfForm.baseUomName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="入库数量" prop="quantity">
              <el-input v-model="shelfForm.quantity" placeholder="请输入入库数量" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次ID" prop="batchId">
              <el-input v-model="shelfForm.batchId" placeholder="自动生成" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="批次号" prop="batchCode">
              <el-input v-model="shelfForm.batchCode" placeholder="自动生成，可手动修改" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库选择" prop="warehouseId">
              <el-cascader
                v-model="selectedWarehouse"
                :options="normalizedWarehouseTree"
                :props="cascaderProps"
                placeholder="请选择仓库/库区/库位"
                style="width: 100%"
                @change="handleWarehouseChange"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 隐藏原有的仓库、库区、库位相关字段 -->
        <div style="display: none">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="仓库ID" prop="warehouseId">
                <el-input v-model="shelfForm.warehouseId" placeholder="请输入仓库ID" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="仓库编码" prop="warehouseCode">
                <el-input v-model="shelfForm.warehouseCode" placeholder="请输入仓库编码" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="仓库名称" prop="warehouseName">
                <el-input v-model="shelfForm.warehouseName" placeholder="请输入仓库名称" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="库区ID" prop="areaId">
                <el-input v-model="shelfForm.areaId" placeholder="请输入库区ID" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="库区编码" prop="areaCode">
                <el-input v-model="shelfForm.areaCode" placeholder="请输入库区编码" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="库区名称" prop="areaName">
                <el-input v-model="shelfForm.areaName" placeholder="请输入库区名称" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="库位ID" prop="locationId">
                <el-input v-model="shelfForm.locationId" placeholder="请输入库位ID" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="库位编码" prop="locationCode">
                <el-input v-model="shelfForm.locationCode" placeholder="请输入库位编码" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="库位名称" prop="locationName">
                <el-input v-model="shelfForm.locationName" placeholder="请输入库位名称" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="shelfForm.materialSpec" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="shelfForm.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitShelfForm" v-if="!isShelfViewMode">确 定</el-button>
          <el-button @click="cancelShelf">{{ isShelfViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 到货通知单选择对话框 -->
    <el-dialog :title="arrivalNoticeDialogTitle" v-model="arrivalNoticeSelectOpen" width="1200px" append-to-body>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-table
            v-loading="arrivalNoticeLoading"
            :data="arrivalNoticeLineList"
            @row-dblclick="selectArrivalNoticeLine"
            highlight-current-row
            style="width: 100%"
            height="400"
          >
            <el-table-column type="index" width="50" align="center" />
            <el-table-column label="产品物料编码" align="center" prop="materialCode" />
            <el-table-column label="产品物料名称" align="center" prop="materialName" />
            <el-table-column label="规格型号" align="center" prop="materialSpec" />
            <el-table-column label="单位" align="center" prop="baseUomName" />
            <el-table-column label="到货数量" align="center" prop="quantityArrival" />
            <el-table-column label="合格数量" align="center" prop="quantityQuanlified" />
            <el-table-column label="操作" align="center" width="100">
              <template #default="scope">
                <el-button link type="primary" @click="selectArrivalNoticeLine(scope.row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="arrivalNoticeSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 标签拍照识别(OCR 扫标签,回填料号/批次;Web 仅测试用,最终落地 App/PAD) -->
    <LabelCapture ref="labelCaptureRef" @recognized="onLabelRecognized" />
  </div>
</template>

<script setup lang="ts" name="ReceiveForm">
import { getReceive, addReceive, updateReceive, submitReceive, confirmShelf, getReceiveProcId } from '@/api/erp/wm/receive';
import type { ReceiveForm, ReceiveLineVO, ReceiveDetailVO } from '@/api/erp/wm/receive/types';
import { getArrivalnotice } from '@/api/erp/wm/arrivalnotice';
import { listDetail } from '@/api/erp/wm/detail';
import { createBatch } from '@/api/erp/wm/batch';
import { getWareHouseTree, listWarehouse } from '@/api/erp/wm/warehouse';
import { getProcesss } from '@/api/workflow/process/index';
import { useRoute, useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import { listMaterial } from '@/api/erp/bd/material';
import LabelCapture from '@/components/cv/LabelCapture.vue';

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_material_receive_status } = toRefs<any>(proxy.useDict('wm_material_receive_status'));
const route = useRoute();
const router = useRouter();

const wmMaterialReceiveLineList = ref<ReceiveLineVO[]>([]);
const checkedWmMaterialReceiveLine = ref<any[]>([]);

/** 流程相关状态 */
const loadingFlow = ref(false);
const flowOptions = ref<any[]>([]);

/** 上架弹窗相关变量 */
const shelfOpen = ref(false);
/** 上架表单（物料入库单明细的前端可编辑形态） */
type ShelfForm = Partial<ReceiveDetailVO> & { snCode?: string };
const shelfForm = ref<ShelfForm>({});
const shelfTitle = ref('');
const isShelfViewMode = ref(false);
const labelCaptureRef = ref<InstanceType<typeof LabelCapture> | null>(null);

/**
 * OCR 扫标签回填上架表单(Web 仅测试用,最终落地 App/PAD)
 * 料号 → 主数据校验 → 回填 名称/批次;识别结果可继续手改
 */
function onLabelRecognized(payload: any) {
  if (payload.materialCode) {
    shelfForm.value.materialCode = payload.materialCode;
    if (payload.materialName) {
      shelfForm.value.materialName = payload.materialName;
    } else {
      /** 主数据未命中时尝试按料号查物料(兜底) */
      listMaterial({ materialCode: payload.materialCode, pageNum: 1, pageSize: 5 } as any)
        .then((res: any) => {
          const rows = res?.rows || [];
          if (rows.length) {
            shelfForm.value.materialCode = rows[0].materialCode;
            shelfForm.value.materialName = rows[0].materialName;
          }
        })
        .catch(() => {});
    }
  }
  if (payload.batchCode) {
    shelfForm.value.batchCode = payload.batchCode;
  }
  if (payload.snCode) {
    shelfForm.value.snCode = payload.snCode;
  }
}

/** 仓库树相关数据 */
const warehouseTree = ref([]);
const warehouseTreeLoading = ref(false);
const selectedWarehouse = ref('');
const selectedArea = ref('');
const selectedLocation = ref('');

/** 级联选择器配置 */
const cascaderProps = computed(() => ({
  label: 'label',
  value: 'value',
  children: 'children',
  emitPath: false,
  expandTrigger: 'hover' as const,
  checkStrictly: true
}));

/** 标准化仓库树数据，添加统一的 label 和 value 字段 */
const normalizedWarehouseTree = computed(() => {
  const convert = (nodes: any[]) => {
    if (!nodes || !Array.isArray(nodes)) return [];
    return nodes.map((node: any) => {
      const newNode: any = { ...node };
      /** 确定标签 */
      if (node.warehouseName !== undefined) {
        newNode.label = node.warehouseName;
        newNode.value = node.warehouseId;
      } else if (node.areaName !== undefined) {
        newNode.label = node.areaName;
        newNode.value = node.areaId;
      } else if (node.locationName !== undefined) {
        newNode.label = node.locationName;
        newNode.value = node.locationId;
      } else {
        newNode.label = node.name || node.label || node.warehouseCode || node.areaCode || node.locationCode || '未知节点';
        newNode.value = node.id || null;
      }
      /** 递归处理子节点 */
      if (node.children && node.children.length > 0) {
        newNode.children = convert(node.children);
      }
      return newNode;
    });
  };
  return convert(warehouseTree.value);
});

/** 明细数据显示相关 */
const expandedRows = ref([]);

/** 到货通知单选择相关变量 */
const arrivalNoticeSelectOpen = ref(false);
const arrivalNoticeLineList = ref([]);
const arrivalNoticeLoading = ref(false);
const currentMaterialRowIndex = ref(-1);
const arrivalNoticeDialogTitle = ref('选择到货通知单物料信息');

const data = reactive({
  form: {} as ReceiveForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
    status: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  },
  shelfRules: {
    materialId: [{ required: true, message: '产品物料ID不能为空', trigger: 'blur' }],
    quantity: [{ required: true, message: '入库数量不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, shelfRules } = toRefs(data);
const autoGenFlag = ref(false);
/** 仓库必选：ERP-PU-06 —— 采购收货联动自动生成的收货草稿 warehouseId 为空， */
/** 若不强制指定仓库，后续上架/库存归集会落到空仓库。故主表单加必填校验。 */
const warehouseOptions = ref<any[]>([]);
const rules = {
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
};

/** 加载仓库下拉（供收货单指定入库仓库） */
function loadWarehouseOptions() {
  listWarehouse({ pageNum: 1, pageSize: 500 })
    .then((res: any) => {
      warehouseOptions.value = res.rows || [];
    })
    .catch((e: any) => {
      console.error('加载仓库列表失败', e);
      warehouseOptions.value = [];
    });
}

/** 收货单主表仓库变更时同步编码/名称，避免只落 ID 导致列表列空白（与上架表单的 handleWarehouseChange 区分） */
function handleReceiveWarehouseChange(warehouseId: any) {
  const w = warehouseOptions.value.find((i: any) => i.warehouseId === warehouseId);
  form.value.warehouseCode = w?.warehouseCode ?? null;
  form.value.warehouseName = w?.warehouseName ?? null;
}

loadWarehouseOptions();

const isViewMode = ref(false);
const title = ref('');
const pageType = ref('');

const formDisabled = computed(() => isViewMode.value || props.disabled);

function normalizeReceiveDate(raw: any) {
  if (raw == null || raw === '') return raw;
  const s = String(raw);
  return s.length >= 10 ? s.substring(0, 10) : s;
}

/** 将流程下发的业务主表 + 行表写入本地状态（与 getReceive 结构一致） */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  if (merged.receiveDate) {
    merged.receiveDate = normalizeReceiveDate(merged.receiveDate);
  }
  form.value = merged;
  wmMaterialReceiveLineList.value = Array.isArray(merged.wmMaterialReceiveLineList)
    ? merged.wmMaterialReceiveLineList.map((line: any) => ({
        ...line,
        /** 优先使用后端返回的 wmMaterialReceiveLineDetailList，否则使用前端维护的 detailList */
        detailList: line.wmMaterialReceiveLineDetailList || line.detailList || []
      }))
    : [];
  wmMaterialReceiveLineList.value.forEach((line: any, index: number) => {
    if (line.lineId) {
      loadDetailList(line.lineId, index);
    }
  });
  tryAutoSelect();
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).receiveId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '物料入库单（流程）' : '物料入库单';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  const id = (route.query.id || route.query.dataId) as string | undefined;
  const action = route.query.action;
  pageType.value = (route.query.pageType as string) || '';
  isViewMode.value = action === 'view';

  if (id) {
    initPage(id);
  }
});

function initPage(id: any) {
  const action = route.query.action;

  if (action === 'view') {
    isViewMode.value = true;
    title.value = '查看物料入库单';
    if (id) {
      getInfo(id);
    }
  } else if (action === 'edit') {
    isViewMode.value = false;
    const pageType = route.query.pageType;
    title.value = pageType === 'shelf' ? '采购单上架' : '修改物料入库单';
    if (id) {
      getInfo(id);
    }
    loadFlows();
  } else if (action === 'add') {
    isViewMode.value = false;
    title.value = '新增物料入库单';
    reset();
    loadFlows();
  } else if (id) {
    /** 已由流程父组件注入 customFormData 时不再重复请求 */
    const injectedId = (props.customFormData as any)?.receiveId;
    if (injectedId != null && String(injectedId) === String(id)) {
      return;
    }
    /** 仅有 id/dataId、无 action 时从接口拉全量（含子表明细） */
    const inWorkflowDetail = (route.path || '').includes('/workflow/process/detail');
    isViewMode.value = true;
    title.value = inWorkflowDetail ? '物料入库单（流程）' : '查看物料入库单';
    getInfo(id);
    loadFlows();
  }
}

/** 获取详细信息 */
function getInfo(id: any) {
  getReceive(id).then((response: any) => {
    form.value = response.data;
    wmMaterialReceiveLineList.value = response.data.wmMaterialReceiveLineList;

    /** 获取每个行项目的明细数据 */
    wmMaterialReceiveLineList.value.forEach((line: any, index: number) => {
      /** 优先使用后端返回的 wmMaterialReceiveLineDetailList，否则初始化为空数组 */
      /** 后端 selectById 会查询明细数据并填充到此字段 */
      line.detailList = line.wmMaterialReceiveLineDetailList || [];

      /** 如果后端未返回明细数据但行ID存在，则通过独立接口加载 */
      if (line.detailList.length === 0 && line.lineId) {
        loadDetailList(line.lineId, index);
      }
    });

    /** 状态校验：审批中状态强制设为只读 */
    if (form.value.status === 'APPROVING') {
      isViewMode.value = true;
      proxy.$modal.msgWarning('当前单据正在审批中，不可修改');
    }

    /** 表单数据加载完成后，重新尝试自动选择流程（解决与 loadFlows 的竞态问题） */
    tryAutoSelect();
  });
}

/** 加载明细数据 */
function loadDetailList(lineId: any, index: number): Promise<any> {
  const queryParams = { pageNum: 1, pageSize: 9999, lineId: lineId };
  return listDetail(queryParams)
    .then((response: any) => {
      /** 将获取到的明细数据赋值给对应的行 */
      /** 兼容两种响应结构：TableDataInfo 直接返回（response.rows）或被 R 包装（response.data.rows） */
      wmMaterialReceiveLineList.value[index].detailList = response.rows || response.data?.rows || [];
    })
    .catch((error: any) => {
      console.error('加载明细数据失败:', error);
      wmMaterialReceiveLineList.value[index].detailList = [];
    });
}

/** 表单重置 */
function reset() {
  autoGenFlag.value = false;
  form.value = {
    receiveId: null,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
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
  wmMaterialReceiveLineList.value = [];
  proxy.resetForm('receiveRef');
}

/** 加载流程列表 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'wmMaterialReceiveService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

/** 尝试自动选择流程（仅当未选择且只有一个可用流程时） */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

/** 提交审批 */
function handleSubmitFlow() {
  (proxy.$refs['receiveRef'] as any).validate((valid: boolean) => {
    if (valid) {
      if (!form.value.selectFlowId) {
        proxy.$modal.msgWarning('请先选择审批流程');
        return;
      }
      form.value.wmMaterialReceiveLineList = normalizeReceiveLineDetails();
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(() => {
          submitReceive(String(form.value.receiveId), String(form.value.selectFlowId))
            .then((res: any) => {
              if (res.code === 200) {
                proxy.$modal.msgSuccess('提交审批成功');
                router.push('/erp/inbound/receive');
              } else {
                proxy.$modal.msgError(res.msg || '提交审批失败');
              }
            })
            .catch((error: any) => {
              proxy.$modal.msgError('提交审批失败：' + error);
            });
        });
      } else {
        proxy.$modal.msgWarning('请先保存单据后再提交审批');
      }
    }
  });
}

/** 确认上架 */
function handleConfirmShelf() {
  if (form.value.status !== 'APPROVED') {
    proxy.$modal.msgWarning('当前状态不允许确认上架，只有待上架状态才能执行确认上架操作！');
    return;
  }
  /** 校验是否已有上架明细 */
  let hasDetail = false;
  for (const line of wmMaterialReceiveLineList.value) {
    if (line.detailList && line.detailList.length > 0) {
      hasDetail = true;
      break;
    }
  }
  if (!hasDetail) {
    proxy.$modal.msgWarning('请先添加上架明细并分配库位后再确认上架');
    return;
  }
  proxy.$modal
    .confirm('是否确认上架？确认后单据状态将变为"待入库"。')
    .then(function () {
      /** 上架弹窗只修改内存中的明细；先通过业务 PUT 持久化，再让 confirmShelf 从数据库校验。 */
      form.value.wmMaterialReceiveLineList = normalizeReceiveLineDetails();
      return updateReceive(form.value).then(() => {
        return confirmShelf(form.value.receiveId);
      });
    })
    .then(() => {
      proxy.$modal.msgSuccess('确认上架成功');
      /** 刷新页面数据 */
      getInfo(form.value.receiveId);
    })
    .catch(() => {});
}

/** 查看流程 */
function onViewFlow() {
  getReceiveProcId(String(form.value.receiveId))
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

/** 同步入库行明细字段 */
function normalizeReceiveLineDetails() {
  return wmMaterialReceiveLineList.value.map((line: any) => {
    /** 优先使用前端维护的 detailList（包含用户新增/编辑的明细） */
    /** 后端返回的 wmMaterialReceiveLineDetailList 可能是空数组 []（仍为真值），不能用 || 短路。 */
    const details =
      line.detailList && line.detailList.length > 0
        ? line.detailList
        : line.wmMaterialReceiveLineDetailList && line.wmMaterialReceiveLineDetailList.length > 0
          ? line.wmMaterialReceiveLineDetailList
          : [];
    return {
      ...line,
      wmMaterialReceiveLineDetailList: details
    };
  });
}

/** 提交按钮 */
function submitForm() {
  (proxy.$refs['receiveRef'] as any).validate((valid: boolean) => {
    if (valid) {
      /** 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号 */
      if (autoGenFlag.value) {
        form.value.receiveCode = undefined;
      }
      form.value.wmMaterialReceiveLineList = normalizeReceiveLineDetails();
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/inbound/receive');
        });
      } else {
        addReceive(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/inbound/receive');
        });
      }
    }
  });
}

/** 审核按钮提交表单 */
function submitApprovingForm() {
  (proxy.$refs['receiveRef'] as any).validate((valid: boolean) => {
    if (valid) {
      /** 设置状态为审核中 */
      form.value.status = 'APPROVING';
      form.value.wmMaterialReceiveLineList = normalizeReceiveLineDetails();
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(() => {
          proxy.$modal.msgSuccess('审核提交成功');
          router.push('/erp/inbound/receive');
        });
      } else {
        addReceive(form.value).then(() => {
          proxy.$modal.msgSuccess('审核提交成功');
          router.push('/erp/inbound/receive');
        });
      }
    }
  });
}

/** 取消按钮 */
function cancel() {
  const obj = { path: '/erp/inbound/receive' };
  proxy.$tab.closeOpenPage(obj);
}

/** 物料入库单行序号 */
function rowWmMaterialReceiveLineIndex({ row, rowIndex }: any): string {
  row.index = rowIndex + 1;
  return '';
}

/** 物料入库单行添加按钮操作 */
function handleAddWmMaterialReceiveLine() {
  const obj: any = {};
  obj.sourceDocLineId = '';
  obj.materialId = '';
  obj.materialCode = '';
  obj.materialName = '';
  obj.materialSpec = '';
  obj.baseUomId = '';
  obj.baseUomName = '';
  obj.quantityReceived = '';
  obj.batchId = '';
  obj.batchCode = '';
  obj.warehouseId = '';
  obj.warehouseCode = '';
  obj.warehouseName = '';
  obj.areaId = '';
  obj.areaCode = '';
  obj.areaName = '';
  obj.locationId = '';
  obj.locationCode = '';
  obj.locationName = '';
  obj.detailList = []; /** 初始化明细列表 */
  wmMaterialReceiveLineList.value.push(obj);
}

/** 物料入库单行删除按钮操作 */
function handleDeleteWmMaterialReceiveLine() {
  if (checkedWmMaterialReceiveLine.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的物料入库单行数据');
  } else {
    const wmMaterialReceiveLines = wmMaterialReceiveLineList.value;
    const checkedWmMaterialReceiveLines = checkedWmMaterialReceiveLine.value;
    wmMaterialReceiveLineList.value = wmMaterialReceiveLines.filter(function (item: any) {
      return checkedWmMaterialReceiveLines.indexOf(item.index) == -1;
    });
  }
}

/** 复选框选中数据 */
function handleWmMaterialReceiveLineSelectionChange(selection: any[]) {
  checkedWmMaterialReceiveLine.value = selection.map((item: any) => item.index);
}

/** 获取字典标签 */
function getDictLabel(dictOptions: any, value: any) {
  const dict = dictOptions.find((item: any) => item.value === value);
  return dict ? dict.label : value;
}

/**
 * 打开到货通知单物料选择对话框
 */
function openMaterialSelect(rowIndex: number) {
  /** 记录当前行索引 */
  currentMaterialRowIndex.value = rowIndex;

  /** 检查是否有到货通知单ID */
  if (!form.value.noticeId) {
    proxy.$modal.msgWarning('请先选择到货通知单');
    return;
  }

  /** 加载到货通知单的子表信息 */
  loadArrivalNoticeLines(form.value.noticeId);

  /** 打开对话框 */
  arrivalNoticeSelectOpen.value = true;
}

/**
 * 加载到货通知单子表信息
 */
function loadArrivalNoticeLines(noticeId: any) {
  arrivalNoticeLoading.value = true;
  getArrivalnotice(noticeId)
    .then((response: any) => {
      arrivalNoticeLineList.value = response.data.wmArrivalNoticeLineList || [];
      arrivalNoticeLoading.value = false;
    })
    .catch((error: any) => {
      arrivalNoticeLoading.value = false;
      proxy.$modal.msgError('加载到货通知单信息失败');
      console.error(error);
    });
}

/**
 * 选择到货通知单行信息
 */
function selectArrivalNoticeLine(row: any) {
  /** 检查行索引是否有效 */
  if (currentMaterialRowIndex.value < 0 || currentMaterialRowIndex.value >= wmMaterialReceiveLineList.value.length) {
    proxy.$modal.msgError('选择行索引无效');
    return;
  }

  /** 将选中的到货通知单行信息赋值给当前入库单行 */
  const currentRow = wmMaterialReceiveLineList.value[currentMaterialRowIndex.value];
  currentRow.materialId = row.materialId;
  currentRow.materialCode = row.materialCode;
  currentRow.materialName = row.materialName;
  currentRow.materialSpec = row.materialSpec;
  currentRow.baseUomId = row.baseUomId;
  currentRow.baseUomName = row.baseUomName;
  currentRow.quantityReceived = row.quantityArrival;
  currentRow.sourceDocLineId = row.lineId;

  /** 关闭对话框 */
  arrivalNoticeSelectOpen.value = false;
  currentMaterialRowIndex.value = -1;
}

/** 上架按钮操作 */
function handleShelf(row: any) {
  /** 校验行ID：若行未保存到数据库（lineId为空），不允许上架 */
  /** 否则 addDetail 保存的明细 line_id 为 null，会成为孤儿记录，重新打开后无法查询 */
  if (!row.lineId) {
    proxy.$modal.msgWarning('当前入库单行未保存，请先保存入库单主表后再进行上架操作');
    return;
  }

  /** 打开上架弹窗 */
  resetShelfForm();
  isShelfViewMode.value = false;

  /** 加载仓库树数据 */
  loadWarehouseTree();

  /** 填充从主表传递过来的数据 */
  shelfForm.value.lineId = row.lineId;
  shelfForm.value.materialId = row.materialId;
  shelfForm.value.materialCode = row.materialCode;
  shelfForm.value.materialName = row.materialName;
  shelfForm.value.baseUomId = row.baseUomId;
  shelfForm.value.baseUomName = row.baseUomName;
  shelfForm.value.quantity = row.quantityReceived;
  shelfForm.value.receiveId = form.value.receiveId;

  /** 自动生成批次号：调用后端 createBatch 接口，自动生成批次编码并回填 */
  autoGenerateBatch(row);

  shelfOpen.value = true;
  shelfTitle.value = '添加物料入库单明细';
}

/**
 * 自动生成批次号
 * 调用后端 /erp/wm/batch/createBatch 接口，自动生成批次编码并回填到上架表单
 * 如果生成失败（如物料未启用批次管理），不阻塞上架流程
 */
function autoGenerateBatch(row: any) {
  if (!row.materialId) return;

  const batchData = {
    materialId: row.materialId,
    materialCode: row.materialCode,
    materialName: row.materialName,
    sourceDocType: 'PURCHASE_RECEIVE',
    sourceDocId: form.value.receiveId,
    sourceDocNo: form.value.receiveCode,
    quantity: row.quantityReceived
  };

  createBatch(batchData)
    .then((response: any) => {
      if (response.code === 200 && response.data) {
        /** 自动回填批次ID和批次号到上架表单 */
        shelfForm.value.batchId = response.data.batchId;
        shelfForm.value.batchCode = response.data.batchCode;
      }
    })
    .catch((error: any) => {
      /** 批次号生成失败不阻塞上架流程，用户可手动输入 */
      console.warn('自动生成批次号失败，用户可手动输入:', error);
    });
}

/** 加载仓库树数据 */
function loadWarehouseTree() {
  warehouseTreeLoading.value = true;
  getWareHouseTree()
    .then((response: any) => {
      warehouseTree.value = response.data;
      warehouseTreeLoading.value = false;
    })
    .catch((error: any) => {
      warehouseTreeLoading.value = false;
      proxy.$modal.msgError('加载仓库数据失败');
      console.error(error);
    });
}

/** 仓库选择变更处理 */
function handleWarehouseChange(value: any) {
  if (!value) {
    /** 清空所有相关字段 */
    shelfForm.value.warehouseId = null;
    shelfForm.value.warehouseCode = '';
    shelfForm.value.warehouseName = '';
    shelfForm.value.areaId = null;
    shelfForm.value.areaCode = '';
    shelfForm.value.areaName = '';
    shelfForm.value.locationId = null;
    shelfForm.value.locationCode = '';
    shelfForm.value.locationName = '';
    return;
  }

  /** 解析选中的值 */
  const selectedPath = value;
  const pathNodes = findPathNodes(normalizedWarehouseTree.value, selectedPath);

  /** 根据选中路径提取相关信息 */
  if (pathNodes.length >= 1) {
    const warehouseNode = pathNodes[0];
    shelfForm.value.warehouseId = warehouseNode.warehouseId;
    shelfForm.value.warehouseCode = warehouseNode.warehouseCode;
    shelfForm.value.warehouseName = warehouseNode.warehouseName;
  }

  if (pathNodes.length >= 2) {
    const areaNode = pathNodes[1];
    shelfForm.value.areaId = areaNode.areaId;
    shelfForm.value.areaCode = areaNode.areaCode;
    shelfForm.value.areaName = areaNode.areaName;
  }

  if (pathNodes.length >= 3) {
    const locationNode = pathNodes[2];
    shelfForm.value.locationId = locationNode.locationId;
    shelfForm.value.locationCode = locationNode.locationCode;
    shelfForm.value.locationName = locationNode.locationName;
  }
}

/** 根据ID在树中查找节点 */
function findNodeById(nodes: any[], id: any, idField: string): any {
  if (!nodes || !Array.isArray(nodes)) return null;

  for (const node of nodes) {
    if (node[idField] === id) {
      return node;
    }
    if (node.children && node.children.length > 0) {
      const found = findNodeById(node.children, id, idField);
      if (found) return found;
    }
  }
  return null;
}

/** 根据选中的值查找完整路径节点 */
function findPathNodes(nodes: any[], selectedValue: any): any[] {
  if (!nodes || !selectedValue) return [];

  /** 使用 value 字段查找节点（标准化后的树中每个节点都有 value 字段） */
  let path: any[] = [];
  const currentNode = findNodeById(nodes, selectedValue, 'value');

  if (currentNode) {
    path.push(currentNode);
    /** 查找父节点 */
    const parentPath = findParentPath(nodes, currentNode);
    path = [...parentPath, ...path];
  }

  return path;
}

/** 查找父节点路径 */
function findParentPath(nodes: any[], node: any): any[] {
  if (!nodes || !node) return [];

  /** 深度优先搜索查找父节点 */
  function dfs(currentNodes: any[], targetNode: any, path: any[]): any[] {
    for (const n of currentNodes) {
      /** 如果当前节点是目标节点的父节点 */
      if (n.children && n.children.some((child: any) => child.value === targetNode.value)) {
        return [n, ...path];
      }
      /** 递归搜索子节点 */
      if (n.children && n.children.length > 0) {
        const found = dfs(n.children, targetNode, [n, ...path]);
        if (found.length > 0) {
          return found;
        }
      }
    }
    return [];
  }

  const result = dfs(nodes, node, []);
  /** 反转路径，使其从根节点到父节点 */
  return result.reverse();
}

/** 重置上架表单 */
function resetShelfForm() {
  shelfForm.value = {
    detailId: null,
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
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
  selectedWarehouse.value = '';
  selectedArea.value = '';
  selectedLocation.value = '';
  proxy.resetForm('shelfRef');
}

/** 取消上架 */
function cancelShelf() {
  shelfOpen.value = false;
  resetShelfForm();
}

/** 提交上架表单 */
function submitShelfForm() {
  (proxy.$refs['shelfRef'] as any).validate((valid: boolean) => {
    if (valid) {
      /** 校验 lineId：若为空则不允许保存，防止生成孤儿明细记录 */
      if (!shelfForm.value.lineId) {
        proxy.$modal.msgWarning('当前入库单行未保存，请先保存入库单主表后再进行上架操作');
        return;
      }

      const rowIndex = wmMaterialReceiveLineList.value.findIndex((line: any) => line.lineId === shelfForm.value.lineId);
      if (rowIndex < 0) {
        proxy.$modal.msgWarning('未找到对应的入库单行');
        return;
      }

      const detailList = wmMaterialReceiveLineList.value[rowIndex].detailList;

      if (shelfForm.value.detailId != null) {
        /** 编辑模式：只更新前端内存中的明细数据 */
        /** 不调用独立 updateDetail API，避免 lineId 变化导致数据不一致 */
        const detailIndex = detailList.findIndex((d: any) => d.detailId === shelfForm.value.detailId);
        if (detailIndex >= 0) {
          detailList[detailIndex] = { ...shelfForm.value } as ReceiveDetailVO;
        }
        proxy.$modal.msgSuccess('修改成功');
      } else {
        /** 新增模式：生成临时 detailId（负数，避免与数据库正数 ID 冲突），添加到前端内存 */
        /** 真正的数据库保存由主表单 save 时通过 updateByBo 一并完成 */
        shelfForm.value.detailId = -Date.now();
        detailList.push({ ...shelfForm.value } as ReceiveDetailVO);
        proxy.$modal.msgSuccess('新增成功');
      }

      shelfOpen.value = false;
    }
  });
}

/**
 * 修改明细数据
 */
function handleEditDetail(detailRow: any, parentRow: any) {
  /** 打开上架弹窗进行编辑 */
  resetShelfForm();
  isShelfViewMode.value = false;

  /** 加载仓库树数据 */
  loadWarehouseTree();

  /** 填充选中的明细数据到表单 */
  Object.keys(shelfForm.value).forEach((key) => {
    if (detailRow[key] !== undefined) {
      shelfForm.value[key] = detailRow[key];
    }
  });

  /** 设置级联选择器的值（emitPath: false 时取最深层级节点的值） */
  if (detailRow.warehouseId) {
    selectedWarehouse.value = detailRow.locationId ?? detailRow.areaId ?? detailRow.warehouseId;
  }

  shelfOpen.value = true;
  shelfTitle.value = '修改物料入库单明细';
}

/**
 * 删除明细数据
 */
function handleDeleteDetail(detailRow: any, parentRow: any) {
  proxy.$modal
    .confirm('确定要删除该条明细数据吗？')
    .then(() => {
      /** 只删除前端内存中的明细，不调用独立 delDetail API */
      /** 真正的数据库删除由主表单 save 时通过 updateByBo 全删全插完成 */
      const lineId = parentRow.lineId;
      const rowIndex = wmMaterialReceiveLineList.value.findIndex((line: any) => line.lineId === lineId);
      if (rowIndex >= 0) {
        const detailList = wmMaterialReceiveLineList.value[rowIndex].detailList;
        const detailIndex = detailList.findIndex((d: any) => d.detailId === detailRow.detailId);
        if (detailIndex >= 0) {
          detailList.splice(detailIndex, 1);
        }
      }
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch((error: any) => {
      if (error !== 'cancel') {
        console.error(error);
      }
    });
}

/** 监听仓库树数据变化，更新 cascader 选项 */
watch(warehouseTree, (newVal: any) => {
  if (newVal && newVal.length > 0) {
    /** 可以在这里处理数据转换 */
  }
});

/** 监听表单数据变化，如果有初始值则设置选中项 */
watch(
  shelfForm,
  (newVal: any) => {
    if (newVal.warehouseId && !selectedWarehouse.value) {
      /** 如果表单中有仓库ID但没有选中项，则尝试设置选中项 */
      const path = [];
      if (newVal.warehouseId) path.push(newVal.warehouseId.toString());
      if (newVal.areaId) path.push(newVal.areaId.toString());
      if (newVal.locationId) path.push(newVal.locationId.toString());
      if (path.length > 0) {
        selectedWarehouse.value = path.join('-');
      }
    }
  },
  { deep: true }
);

/** 自动生成编号 */
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_MATERIAL_RECEIVE_CODE').then((response: any) => {
      form.value.receiveCode = response;
    });
  } else {
    form.value.receiveCode = null;
  }
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
