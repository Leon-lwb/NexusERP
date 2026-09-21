<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="批记录编号" prop="dhrNo">
            <el-input v-model="queryParams.dhrNo" placeholder="请输入批记录编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单号" prop="workOrderNo">
            <el-input v-model="queryParams.workOrderNo" placeholder="请输入工单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次号" prop="lotNo">
            <el-input v-model="queryParams.lotNo" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="SN" prop="sn">
            <el-input v-model="queryParams.sn" placeholder="请输入SN" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="创建时间">
            <el-date-picker
              v-model="dateRange"
              value-format="YYYY-MM-DD"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="openBuild" v-hasPermi="['mes:dhr:record:add']">生成批记录</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['mes:dhr:record:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="批记录编号" prop="dhrNo" min-width="160" />
      <el-table-column label="工单号" prop="workOrderNo" width="150" />
      <el-table-column label="产出物料" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.materialCode || scope.row.materialName">{{ scope.row.materialCode }} {{ scope.row.materialName }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <!-- 批次号可为空（单件级批记录）、SN 可为空（批次级批记录），统一显示「—」 -->
      <el-table-column label="批次号" prop="lotNo" width="130">
        <template #default="scope">{{ scope.row.lotNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="SN" prop="sn" width="150">
        <template #default="scope">{{ scope.row.sn || '—' }}</template>
      </el-table-column>
      <el-table-column label="计划数量" prop="plannedQty" align="right" width="100" />
      <el-table-column label="完工数量" prop="completedQty" align="right" width="100" />
      <el-table-column label="报废数量" prop="scrapQty" align="right" width="100" />
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="scope">
          <dict-tag v-if="statusDictReady" :options="mes_dhr_status" :value="scope.row.status" />
          <el-tag v-else :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="冻结时间" prop="frozenAt" width="170" />
      <el-table-column label="放行时间" prop="releasedAt" width="170" />
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="380" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['mes:dhr:record:query']">详情</el-button>
          <el-button
            link
            type="primary"
            icon="Refresh"
            @click="handleRebuild(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:dhr:record:edit']"
            >重建</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="openSign(scope.row, 'SUBMIT')"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:dhr:record:submit']"
            >提交复核</el-button
          >
          <el-button
            link
            type="success"
            icon="Stamp"
            @click="openSign(scope.row, 'RELEASE')"
            v-if="scope.row.status === 'SUBMITTED'"
            v-hasPermi="['mes:dhr:record:release']"
            >放行</el-button
          >
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleReject(scope.row)"
            v-if="scope.row.status === 'SUBMITTED'"
            v-hasPermi="['mes:dhr:record:reject']"
            >驳回</el-button
          >
          <el-button
            link
            type="info"
            icon="Box"
            @click="handleArchive(scope.row)"
            v-if="scope.row.status === 'RELEASED'"
            v-hasPermi="['mes:dhr:record:archive']"
            >归档</el-button
          >
          <el-button link type="warning" icon="CircleCheck" @click="handleVerify(scope.row)" v-hasPermi="['mes:dhr:record:query']">校验</el-button>
          <el-button link type="primary" icon="Link" @click="handleSignChain(scope.row)" v-hasPermi="['mes:dhr:record:query']">签名链</el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:dhr:record:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 生成批记录：先选工单，再按需补批次号 / SN -->
    <el-dialog title="生成批记录" v-model="buildDialog.visible" width="620px" append-to-body>
      <el-form ref="buildFormRef" :model="buildForm" :rules="buildRules" label-width="100px">
        <el-form-item label="工单" prop="workOrderId">
          <el-input v-model="buildWoNo" placeholder="请选择工单" readonly>
            <template #append>
              <el-button icon="Search" @click="openWoDialog" />
            </template>
          </el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="批次号" prop="lotNo">
              <el-input v-model="buildForm.lotNo" placeholder="留空按工单聚合" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SN" prop="sn">
              <el-input v-model="buildForm.sn" placeholder="留空按工单聚合" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="buildForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="buildDialog.submitting" @click="submitBuild">确 定</el-button>
        <el-button @click="buildDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 工单选择 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="800px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单号">
          <el-input v-model="woDialog.query.woNo" placeholder="请输入工单号" clearable @keyup.enter="queryWos" />
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWos">搜索</el-button></el-form-item>
      </el-form>
      <el-table v-loading="woDialog.loading" :data="woDialog.list" height="380" @row-dblclick="selectWo">
        <el-table-column label="工单号" prop="woNo" width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="woStatus" width="110" align="center" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWo(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWos"
      />
    </el-dialog>

    <!-- 批记录详情：基本信息 + 分册折叠面板 -->
    <el-drawer v-model="detail.visible" title="批记录详情" size="960px" append-to-body>
      <div v-loading="detail.loading">
        <template v-if="detail.data">
          <el-descriptions :column="2" border title="基本信息">
            <el-descriptions-item label="批记录编号">{{ detail.data.dhrNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <dict-tag v-if="statusDictReady" :options="mes_dhr_status" :value="detail.data.status" />
              <el-tag v-else :type="statusTagType(detail.data.status)">{{ statusLabel(detail.data.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="工单号">{{ detail.data.workOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="产出物料">{{ detail.data.materialCode }} {{ detail.data.materialName }}</el-descriptions-item>
            <el-descriptions-item label="批次号">{{ detail.data.lotNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="SN">{{ detail.data.sn || '—' }}</el-descriptions-item>
            <el-descriptions-item label="计划 / 完工 / 报废">
              {{ detail.data.plannedQty ?? '—' }} / {{ detail.data.completedQty ?? '—' }} / {{ detail.data.scrapQty ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="冻结时间">{{ detail.data.frozenAt || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提交复核">{{ detail.data.submittedAt || '—' }}（{{ detail.data.submittedBy || '—' }}）</el-descriptions-item>
            <el-descriptions-item label="放行">{{ detail.data.releasedAt || '—' }}（{{ detail.data.releasedBy || '—' }}）</el-descriptions-item>
            <el-descriptions-item label="驳回">{{ detail.data.rejectedAt || '—' }}（{{ detail.data.rejectedBy || '—' }}）</el-descriptions-item>
            <el-descriptions-item label="驳回原因">{{ detail.data.rejectReason || '—' }}</el-descriptions-item>
            <el-descriptions-item label="归档时间">{{ detail.data.archivedAt || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.data.createTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="快照哈希" :span="2">
              <div v-if="detail.data.snapshotHash" class="hash-cell">
                <el-tooltip :content="detail.data.snapshotHash" placement="top">
                  <span class="hash-text">{{ shortHash(detail.data.snapshotHash) }}</span>
                </el-tooltip>
                <el-button link type="primary" icon="DocumentCopy" v-copyText="detail.data.snapshotHash" v-copyText:callback="onCopyHash" />
              </div>
              <span v-else>—</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">分册明细（{{ parsedSections.length }} 册）</div>
          <el-collapse v-model="activeSections">
            <el-collapse-item v-for="sec in parsedSections" :key="sec.sectionId" :name="sec.sectionId">
              <template #title>
                <span class="section-name">{{ sectionTitle(sec) }}</span>
                <el-tag class="ml8" size="small" type="info">{{ sec.itemCount ?? sec.rows.length }} 条</el-tag>
              </template>
              <div class="hash-cell mb8">
                <span class="text-gray-500">分册哈希：</span>
                <template v-if="sec.contentHash">
                  <el-tooltip :content="sec.contentHash" placement="top">
                    <span class="hash-text">{{ shortHash(sec.contentHash) }}</span>
                  </el-tooltip>
                  <el-button link type="primary" icon="DocumentCopy" v-copyText="sec.contentHash" v-copyText:callback="onCopyHash" />
                </template>
                <span v-else>—</span>
              </div>
              <el-table v-if="sec.rows.length" :data="sec.rows" size="small" border max-height="360">
                <el-table-column
                  v-for="col in sec.columns"
                  :key="col"
                  :label="columnLabel(col)"
                  :prop="col"
                  min-width="130"
                  show-overflow-tooltip
                  :formatter="cellFormatter"
                />
              </el-table>
              <el-empty v-else description="该分册无明细数据" :image-size="60" />
            </el-collapse-item>
          </el-collapse>
          <el-empty v-if="!parsedSections.length" description="暂无分册数据" :image-size="80" />
        </template>
        <el-empty v-else-if="!detail.loading" description="未获取到批记录详情" :image-size="80" />
      </div>
    </el-drawer>

    <!-- 校验结果 -->
    <el-dialog v-model="verifyDialog.visible" :title="`批记录校验 - ${verifyDialog.dhrNo || ''}`" width="680px" append-to-body>
      <div v-loading="verifyDialog.loading">
        <template v-if="verifyDialog.data">
          <el-alert
            v-if="verifyDialog.data.hashMatched === false"
            class="mb8"
            type="error"
            :closable="false"
            show-icon
            title="快照哈希不一致：批记录冻结后数据可能已被改动，请立即核查！"
          />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="冻结快照哈希">
              <div class="hash-cell">
                <span class="hash-text">{{ verifyDialog.data.snapshotHash || '—' }}</span>
                <el-button
                  v-if="verifyDialog.data.snapshotHash"
                  link
                  type="primary"
                  icon="DocumentCopy"
                  v-copyText="verifyDialog.data.snapshotHash"
                  v-copyText:callback="onCopyHash"
                />
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="重算哈希">
              <div class="hash-cell">
                <span class="hash-text">{{ verifyDialog.data.recomputedHash || '—' }}</span>
                <el-button
                  v-if="verifyDialog.data.recomputedHash"
                  link
                  type="primary"
                  icon="DocumentCopy"
                  v-copyText="verifyDialog.data.recomputedHash"
                  v-copyText:callback="onCopyHash"
                />
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="哈希一致">
              <el-tag v-if="verifyDialog.data.hashMatched" type="success">一致</el-tag>
              <el-tag v-else type="danger">不一致</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="签名链校验">{{ chainVerifyText(verifyDialog.data.chainVerify) }}</el-descriptions-item>
            <el-descriptions-item label="综合结论">{{ verifyDialog.data.verdict || '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else-if="!verifyDialog.loading" description="未获取到校验结果" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="verifyDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 签名链 -->
    <el-dialog v-model="signChainDialog.visible" :title="`签名链 - ${signChainDialog.dhrNo || ''}`" width="1000px" append-to-body>
      <el-table v-loading="signChainDialog.loading" :data="signChainDialog.list" max-height="460" empty-text="该批记录暂无签名记录">
        <el-table-column label="签署人" prop="signerName" width="120" show-overflow-tooltip />
        <el-table-column label="签署含义" prop="signatureMeaning" min-width="140" show-overflow-tooltip />
        <el-table-column label="认证方式" align="center" width="110">
          <template #default="scope">{{ authMethodText(scope.row.authMethod) }}</template>
        </el-table-column>
        <el-table-column label="签署时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.signedAt) }}</template>
        </el-table-column>
        <el-table-column label="IP" prop="signIp" width="140" show-overflow-tooltip />
        <el-table-column label="链哈希" align="center" min-width="200" :show-overflow-tooltip="false">
          <template #default="scope">
            <div v-if="scope.row.chainHash" class="hash-cell">
              <el-tooltip :content="scope.row.chainHash" placement="top">
                <span class="hash-text">{{ shortHash(scope.row.chainHash) }}</span>
              </el-tooltip>
              <el-button link type="primary" icon="DocumentCopy" v-copyText="scope.row.chainHash" v-copyText:callback="onCopyHash" />
            </div>
            <span v-else class="text-gray-400">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="signChainDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 二级认证弹窗（提交复核 / 放行 / 驳回共用）：凭证只存弹窗内存，后端拒签时保持打开供改正重试 -->
    <SignAuthDialog ref="signAuthRef" v-model="signAuthVisible" @confirmed="handleSignConfirmed" />
  </div>
</template>

<script setup lang="ts" name="MesDhrRecord">
import {
  listDhr,
  getDhr,
  listDhrSections,
  listDhrSignatures,
  verifyDhr,
  buildDhr,
  rebuildDhr,
  delDhr,
  submitDhr,
  releaseDhr,
  rejectDhr,
  archiveDhr,
  exportDhr
} from '@/api/mes/dhr';
import type { DhrRecordVo, DhrRecordQuery, DhrRecordForm, DhrSectionVo, DhrVerifyVo, DhrSignatureVo, SignVerifyResult } from '@/api/mes/dhr/types';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import type { ProWorkOrderVO } from '@/api/mes/pro/types';
import SignAuthDialog from '@/components/sign/SignAuthDialog.vue';
import type { FormInstance } from 'element-plus';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_dhr_status, mes_dhr_section } = toRefs<any>(proxy?.useDict('mes_dhr_status', 'mes_dhr_section'));

/**
 * 状态兜底文案：字典 mes_dhr_status 由后端种子，但字典同步存在延迟 / 新租户未同步的可能，
 * 字典取不到时用本地映射渲染，避免状态列整列空白。
 */
const STATUS_FALLBACK: Record<string, { label: string; type: 'success' | 'info' | 'warning' | 'danger' | 'primary' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '已提交', type: 'warning' },
  RELEASED: { label: '已放行', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  ARCHIVED: { label: '已归档', type: 'primary' }
};

/** 分册编码兜底文案（10 个分册，与后端 mes_dhr_section 字典一致） */
const SECTION_FALLBACK: Record<string, string> = {
  WO_INFO: '工单信息',
  PROCESS: '工序执行',
  MATERIAL: '物料使用',
  EQUIPMENT: '设备记录',
  TOOLING: '工装记录',
  SOP: '作业指导书',
  QC: '质量检验',
  DATA_COLLECT: '数据采集',
  SN_TRACE: 'SN追溯',
  NCR: '不合格品处理'
};

/** 分册内容列名中文映射；映射不到时原样展示键名（不隐藏未知字段） */
const COLUMN_LABEL: Record<string, string> = {
  stepNo: '工序号',
  stepName: '工序名称',
  processCode: '工序编码',
  processName: '工序名称',
  seq: '序号',
  materialCode: '物料编码',
  materialName: '物料名称',
  lotNo: '批次号',
  qty: '数量',
  useQty: '用量',
  unit: '单位',
  equipmentCode: '设备编码',
  equipmentName: '设备名称',
  runTime: '运行时长',
  toolingCode: '工装编码',
  toolingName: '工装名称',
  sopCode: 'SOP编码',
  sopName: 'SOP名称',
  version: '版本',
  itemName: '检验项',
  standardValue: '标准值',
  actualValue: '实测值',
  result: '结果',
  checkResult: '检验结论',
  inspector: '检验员',
  checkTime: '检验时间',
  collectTime: '采集时间',
  pointCode: '采集点',
  pointName: '采集点名称',
  sn: 'SN',
  operatorName: '操作人',
  operateTime: '操作时间',
  ncrNo: '不合格单号',
  defectQty: '不合格数量',
  disposition: '处置方式',
  woNo: '工单号',
  productCode: '产品编码',
  productName: '产品名称',
  plannedQty: '计划数量',
  completedQty: '完工数量',
  scrapQty: '报废数量',
  beginTime: '开始时间',
  endTime: '结束时间',
  remark: '备注'
};

const AUTH_METHOD_TEXT: Record<string, string> = { LOGIN_ONLY: '仅登录态', PASSWORD: '登录密码' };

const loading = ref(false);
const showSearch = ref(true);
const list = ref<DhrRecordVo[]>([]);
const total = ref(0);
const dateRange = ref<string[]>([]);
const queryRef = ref<FormInstance>();

const queryParams = ref<DhrRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  dhrNo: undefined,
  workOrderNo: undefined,
  materialCode: undefined,
  lotNo: undefined,
  sn: undefined,
  status: undefined
});

/** 状态下拉：优先字典，字典未同步时用兜底映射，保证筛选框可用 */
const statusOptions = computed(() => {
  const dict = mes_dhr_status.value ?? [];
  if (dict.length) return dict.map((d: any) => ({ label: d.label, value: d.value }));
  return Object.keys(STATUS_FALLBACK).map((value) => ({ label: STATUS_FALLBACK[value].label, value }));
});

const statusDictReady = computed(() => (mes_dhr_status.value ?? []).length > 0);
const statusLabel = (value?: string) => STATUS_FALLBACK[value ?? '']?.label ?? value ?? '—';
const statusTagType = (value?: string) => STATUS_FALLBACK[value ?? '']?.type ?? 'info';
const authMethodText = (value?: string) => (value ? (AUTH_METHOD_TEXT[value] ?? value) : '—');
/** 哈希过长，列表/面板只保留前缀，完整值由 tooltip 与复制按钮提供 */
const shortHash = (hash: string) => (hash.length > 20 ? `${hash.slice(0, 20)}…` : hash);
const onCopyHash = () => {
  proxy?.$modal.msgSuccess('哈希已复制');
};

/** 分册标题：优先后端 sectionName，其次字典，最后兜底文案/编码 */
const sectionTitle = (section: DhrSectionVo) => {
  if (section.sectionName) return section.sectionName;
  const dict = mes_dhr_section.value ?? [];
  const hit = dict.find((d: any) => d.value === section.sectionCode);
  return hit?.label ?? SECTION_FALLBACK[section.sectionCode ?? ''] ?? section.sectionCode ?? '未命名分册';
};
const columnLabel = (key: string) => COLUMN_LABEL[key] ?? key;
/** 明细值兜底：空值显示「—」，嵌套对象序列化，避免表格出现 [object Object] */
const cellFormatter = (_row: any, _col: any, cellValue: any) => {
  if (cellValue === null || cellValue === undefined || cellValue === '') return '—';
  if (typeof cellValue === 'object') return JSON.stringify(cellValue);
  return String(cellValue);
};
/**
 * 签名链校验结论文案。
 * <p>后端 {@code DhrVerifyVo.chainVerify} 是 {@code VerifyResultVo} **对象**
 * （{@code passed/chainPassed/brokenAt/checkedCount/messages}），不是布尔或文案；
 * 若按标量插值会渲染成 `[object Object]`，且断链位置与明细永远看不到 ——
 * 而这两项正是 DHR 合规台账的核心结论。此处显式展开。</p>
 */
const chainVerifyText = (value?: SignVerifyResult | string | boolean | null) => {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean') return value ? '通过' : '未通过';
  if (typeof value === 'string') return value;
  const passed = value.chainPassed ?? value.passed;
  const parts: string[] = [passed === true ? '通过' : passed === false ? '未通过' : '—'];
  if (value.brokenAt !== null && value.brokenAt !== undefined) {
    parts.push(`断链于签名 ${value.brokenAt}`);
  }
  if (typeof value.checkedCount === 'number') {
    parts.push(`已校验 ${value.checkedCount} 条`);
  }
  if (Array.isArray(value.messages) && value.messages.length) {
    parts.push(value.messages.join('；'));
  }
  return parts.join('，');
};

// ==================== 列表 ====================

const getList = async () => {
  loading.value = true;
  try {
    proxy?.addDateRange(queryParams.value, dateRange.value);
    const res: any = await listDhr(queryParams.value);
    list.value = res.rows ?? [];
    total.value = res.total ?? 0;
  } catch (error) {
    // 拦截器已统一提示，这里兜底避免未处理的 Promise 拒绝并把列表置空
    console.error('批记录列表加载失败:', error);
    list.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  dateRange.value = [];
  handleQuery();
}

const handleExport = () => {
  proxy?.addDateRange(queryParams.value, dateRange.value);
  exportDhr(queryParams.value);
};

// ==================== 生成批记录 ====================

const buildFormRef = ref<FormInstance>();
const buildDialog = reactive({ visible: false, submitting: false });
const buildWoNo = ref('');
const buildForm = reactive<DhrRecordForm>({ workOrderId: '', lotNo: '', sn: '', remark: '' });
const buildRules: ElFormRules = {
  workOrderId: [{ required: true, message: '请选择工单', trigger: 'change' }]
};

const woDialog = reactive({
  visible: false,
  loading: false,
  list: [] as ProWorkOrderVO[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' }
});

const openBuild = () => {
  buildForm.workOrderId = '';
  buildForm.lotNo = '';
  buildForm.sn = '';
  buildForm.remark = '';
  buildWoNo.value = '';
  buildFormRef.value?.clearValidate();
  buildDialog.visible = true;
};

const openWoDialog = () => {
  woDialog.query.pageNum = 1;
  woDialog.query.woNo = '';
  woDialog.visible = true;
  queryWos();
};

const queryWos = async () => {
  woDialog.loading = true;
  try {
    const res: any = await listWorkOrder(woDialog.query as any);
    woDialog.list = res.rows ?? [];
    woDialog.total = res.total ?? 0;
  } catch (error) {
    console.error('工单列表加载失败:', error);
    woDialog.list = [];
    woDialog.total = 0;
  } finally {
    woDialog.loading = false;
  }
};

const selectWo = (row: ProWorkOrderVO) => {
  // 批记录契约中的 workOrderId 即 MES 工单主键（雪花 ID，按字符串传递）
  buildForm.workOrderId = String(row.mesWoId);
  buildWoNo.value = row.woNo;
  woDialog.visible = false;
  buildFormRef.value?.validateField('workOrderId');
};

const submitBuild = () => {
  buildFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    buildDialog.submitting = true;
    try {
      await buildDhr({ ...buildForm, lotNo: buildForm.lotNo || undefined, sn: buildForm.sn || undefined, remark: buildForm.remark || undefined });
      proxy?.$modal.msgSuccess('批记录生成成功');
      buildDialog.visible = false;
      getList();
    } finally {
      buildDialog.submitting = false;
    }
  });
};

// ==================== 详情 ====================

interface ParsedSection extends DhrSectionVo {
  rows: Record<string, any>[];
  columns: string[];
}

const detail = reactive({ visible: false, loading: false, data: null as DhrRecordVo | null });
const activeSections = ref<string[]>([]);

/** contentJson 解析：支持字符串 JSON 或已是对象/数组，异常时返回空数组不阻断详情渲染 */
const parseContentJson = (raw?: string): Record<string, any>[] => {
  if (!raw) return [];
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (Array.isArray(parsed)) return parsed.filter((item) => item && typeof item === 'object') as Record<string, any>[];
    if (parsed && typeof parsed === 'object') return [parsed as Record<string, any>];
    return [];
  } catch (error) {
    console.error('批记录分册内容解析失败:', error);
    return [];
  }
};

/** 分册按 sortOrder 升序渲染，并预解析明细行与列 */
const parsedSections = computed<ParsedSection[]>(() => {
  const sections = [...(detail.data?.sections ?? [])];
  sections.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  return sections.map((section) => {
    const rows = parseContentJson(section.contentJson);
    return { ...section, rows, columns: rows.length ? Object.keys(rows[0]) : [] };
  });
});

const handleDetail = async (row: DhrRecordVo) => {
  detail.visible = true;
  detail.loading = true;
  detail.data = null;
  activeSections.value = [];
  try {
    const res = await getDhr(row.dhrId);
    const data = res.data ?? null;
    // 详情接口未下发 sections 时回落到分册列表接口
    if (data && !data.sections) {
      const sectionRes = await listDhrSections(row.dhrId);
      data.sections = sectionRes.data ?? [];
    }
    detail.data = data;
    activeSections.value = parsedSections.value.map((section) => section.sectionId);
  } catch (error) {
    console.error('批记录详情加载失败:', error);
  } finally {
    detail.loading = false;
  }
};

// ==================== 状态流转（重建 / 删除 / 归档） ====================

const handleRebuild = async (row: DhrRecordVo) => {
  try {
    await proxy!.$modal.confirm(`确认重建批记录 ${row.dhrNo}？将按当前工单 / 批次 / SN 数据重新生成分册与快照哈希。`);
  } catch {
    return;
  }
  await rebuildDhr(row.dhrId);
  proxy?.$modal.msgSuccess('重建成功');
  getList();
};

const handleDelete = async (row: DhrRecordVo) => {
  try {
    await proxy!.$modal.confirm(`是否确认删除批记录 ${row.dhrNo}？`);
  } catch {
    return;
  }
  await delDhr(row.dhrId);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleArchive = async (row: DhrRecordVo) => {
  try {
    await proxy!.$modal.confirm(`确认归档批记录 ${row.dhrNo}？归档后不可再变更。`);
  } catch {
    return;
  }
  await archiveDhr(row.dhrId);
  proxy?.$modal.msgSuccess('归档成功');
  getList();
};

// ==================== 签署（提交复核 / 放行 / 驳回） ====================

const signAuthRef = ref<InstanceType<typeof SignAuthDialog>>();
const signAuthVisible = ref(false);
const signAction = ref<'SUBMIT' | 'RELEASE' | 'REJECT'>('SUBMIT');
const signTarget = ref<DhrRecordVo | null>(null);
const rejectReason = ref('');

const SIGN_SUCCESS_TEXT: Record<'SUBMIT' | 'RELEASE' | 'REJECT', string> = {
  SUBMIT: '提交复核成功',
  RELEASE: '放行成功',
  REJECT: '驳回成功'
};

/** 提交复核 / 放行：打开二级认证弹窗（凭证只存弹窗内存） */
const openSign = (row: DhrRecordVo, action: 'SUBMIT' | 'RELEASE') => {
  signAction.value = action;
  signTarget.value = row;
  rejectReason.value = '';
  signAuthVisible.value = true;
};

/** 驳回：先收集驳回原因，再走二级认证 */
const handleReject = async (row: DhrRecordVo) => {
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt(`请输入批记录 ${row.dhrNo} 的驳回原因`, '驳回批记录', {
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (val: string) => (val && val.trim() ? true : '驳回原因不能为空')
    });
    reason = (value ?? '').trim();
  } catch {
    return;
  }
  signAction.value = 'REJECT';
  signTarget.value = row;
  rejectReason.value = reason;
  signAuthVisible.value = true;
};

/**
 * 二级认证通过后调签署接口。
 * 失败（如「签署密码错误」）时保持认证弹窗打开，只复位按钮提交中状态让用户改正重试；
 * 此处不再本地 toast —— 后端拒签的错误提示由 utils/request.ts 拦截器统一负责。
 */
const handleSignConfirmed = async (auth: { authMethod: string; credential: string }) => {
  const row = signTarget.value;
  if (!row) return;
  signAuthRef.value?.setLoading(true);
  try {
    if (signAction.value === 'SUBMIT') {
      await submitDhr(row.dhrId, { authMethod: auth.authMethod, credential: auth.credential });
    } else if (signAction.value === 'RELEASE') {
      await releaseDhr(row.dhrId, { authMethod: auth.authMethod, credential: auth.credential });
    } else {
      await rejectDhr(row.dhrId, { authMethod: auth.authMethod, credential: auth.credential, reason: rejectReason.value });
    }
  } catch (error) {
    console.error('批记录签署失败:', error);
    signAuthRef.value?.setLoading(false);
    return;
  }
  proxy?.$modal.msgSuccess(SIGN_SUCCESS_TEXT[signAction.value]);
  signAuthVisible.value = false;
  signTarget.value = null;
  rejectReason.value = '';
  getList();
};

// ==================== 校验 / 签名链 ====================

const verifyDialog = reactive({ visible: false, loading: false, dhrNo: '', data: null as DhrVerifyVo | null });

const handleVerify = async (row: DhrRecordVo) => {
  verifyDialog.dhrNo = row.dhrNo ?? '';
  verifyDialog.data = null;
  verifyDialog.visible = true;
  verifyDialog.loading = true;
  try {
    const res = await verifyDhr(row.dhrId);
    verifyDialog.data = res.data ?? null;
  } catch (error) {
    console.error('批记录校验失败:', error);
  } finally {
    verifyDialog.loading = false;
  }
};

const signChainDialog = reactive({ visible: false, loading: false, dhrNo: '', list: [] as DhrSignatureVo[] });

const handleSignChain = async (row: DhrRecordVo) => {
  signChainDialog.dhrNo = row.dhrNo ?? '';
  signChainDialog.list = [];
  signChainDialog.visible = true;
  signChainDialog.loading = true;
  try {
    const res = await listDhrSignatures(row.dhrId);
    signChainDialog.list = res.data ?? [];
  } catch (error) {
    console.error('批记录签名链加载失败:', error);
  } finally {
    signChainDialog.loading = false;
  }
};

onMounted(getList);
</script>

<style scoped>
.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.section-name {
  font-weight: 500;
}
.hash-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.hash-text {
  font-family: monospace;
  word-break: break-all;
}
.ml8 {
  margin-left: 8px;
}
</style>
