<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">BOM清单</span>
          <div class="card-actions">
            <el-button type="primary" @click="handleSave" v-if="!isViewMode">保存</el-button>
            <el-button @click="handleClose">关闭</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="父项物料" prop="parentMaterialName">
              <el-input v-model="form.parentMaterialName" placeholder="父项物料信息" readonly />
              <el-input v-model="form.parentMaterialId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BOM版本号" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BOM名称" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入BOM名称" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker
                v-model="form.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生效日期"
                style="width: 100%"
                :readonly="isViewMode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="expiryDate">
              <el-date-picker
                v-model="form.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择失效日期"
                style="width: 100%"
                :readonly="isViewMode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型" :disabled="isViewMode" clearable style="width: 100%">
                <el-option v-for="dict in prd_material_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- BOM类型 / 工艺路线 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="BOM类型" prop="bomType">
              <el-select v-model="form.bomType" placeholder="请选择BOM类型" :disabled="isViewMode" clearable style="width: 100%">
                <el-option v-for="dict in bd_bom_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工艺路线" prop="routingId">
              <el-select
                v-model="form.routingId"
                placeholder="请选择工艺路线"
                :disabled="isViewMode"
                clearable
                filterable
                style="width: 100%"
                @change="handleRoutingChange"
              >
                <el-option
                  v-for="item in routingOptions"
                  :key="item.routingId"
                  :label="item.routingCode + ' - ' + item.routingName + (item.isCurrent === 1 ? '(当前)' : '')"
                  :value="item.routingId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8" v-if="form.routingId">
            <el-form-item label="路线版本">
              <el-input v-model="currentRoutingVersion" readonly placeholder="—" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 状态信息 -->
        <el-divider content-position="left">状态信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="是否当前版本" prop="isCurrent">
              <el-radio-group v-model="form.isCurrent" :disabled="isViewMode">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status" :disabled="isViewMode">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 备注 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- BOM子项物料信息表格 -->
      <el-divider content-position="center">物料明细</el-divider>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="物料明细" name="details">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary" icon="Plus" @click="addMaterialRow" v-if="!isViewMode">添加物料</el-button>
            </el-col>
          </el-row>

          <el-table :data="materialItems" style="width: 100%">
            <el-table-column type="index" width="50" align="center" label="序号" />
            <el-table-column label="子项物料" align="center" prop="childMaterialName" min-width="200">
              <template #default="scope">
                <el-input v-model="scope.row.childMaterialName" placeholder="请选择子项物料" readonly v-show="isViewMode" />
                <el-input v-model="scope.row.childMaterialId" style="display: none" />
                <el-row :gutter="10" v-show="!isViewMode">
                  <el-col :span="18">
                    <el-input v-model="scope.row.childMaterialName" placeholder="请选择子项物料" readonly>
                      <template #append>
                        <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                      </template>
                    </el-input>
                  </el-col>
                  <el-col :span="6">
                    <el-button v-if="scope.row.childMaterialId" type="danger" icon="Delete" @click="clearMaterial(scope.$index)" />
                  </el-col>
                </el-row>
              </template>
            </el-table-column>
            <el-table-column label="单位父项用量" align="center" prop="quantityPerParent" width="140">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.quantityPerParent"
                  :min="0"
                  controls-position="right"
                  :disabled="isViewMode"
                  :precision="6"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="损耗率(%)" align="center" prop="lossRate" width="140">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.lossRate"
                  :min="0"
                  :max="100"
                  controls-position="right"
                  :disabled="isViewMode"
                  :precision="4"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="工序步骤" align="center" prop="operationStep" width="180">
              <template #default="scope">
                <el-select
                  v-model="scope.row.operationStep"
                  placeholder="选择工序步骤"
                  clearable
                  filterable
                  :disabled="isViewMode"
                  style="width: 100%"
                >
                  <el-option
                    v-for="step in routingSteps"
                    :key="step.stepId"
                    :label="step.sequenceNum + ' - ' + (step.processName || '未命名')"
                    :value="step.sequenceNum"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="提前期(小时)" align="center" prop="leadTime" width="140">
              <template #default="scope">
                <el-input-number v-model="scope.row.leadTime" :min="0" controls-position="right" :disabled="isViewMode" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="来源类型" align="center" prop="sourceType" width="140">
              <template #default="scope">
                <el-select v-model="scope.row.sourceType" placeholder="请选择来源类型" :disabled="isViewMode" clearable style="width: 100%">
                  <el-option v-for="dict in prd_material_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="生效日期" align="center" prop="effectiveDate" width="140">
              <template #default="scope">
                <el-date-picker
                  v-model="scope.row.effectiveDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择生效日期"
                  style="width: 100%"
                  :readonly="isViewMode"
                />
              </template>
            </el-table-column>
            <el-table-column label="失效日期" align="center" prop="expiryDate" width="140">
              <template #default="scope">
                <el-date-picker
                  v-model="scope.row.expiryDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择失效日期"
                  style="width: 100%"
                  :readonly="isViewMode"
                />
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" prop="status" width="100">
              <template #default="scope">
                <el-select v-model="scope.row.status" placeholder="请选择状态" disabled style="width: 100%">
                  <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="是否倒冲" align="center" width="100">
              <template #default="scope">
                <el-switch v-model="scope.row.backflushFlag" :active-value="1" :inactive-value="0" v-if="!isViewMode" />
                <span v-else>{{ scope.row.backflushFlag === 1 ? '是' : '否' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="140" v-if="!isViewMode">
              <template #default="scope">
                <el-button link type="primary" @click="openAlternativeDialog(scope.row)" v-if="scope.row.detailId">替代料</el-button>
                <el-button link type="danger" @click="removeMaterialRow(scope.$index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="BOM树形视图" name="tree">
          <div class="bom-tree-container">
            <el-tree
              v-loading="treeLoading"
              :data="bomTreeData"
              :props="treeProps"
              node-key="materialId"
              default-expand-all
              :expand-on-click-node="false"
            >
              <template #default="{ data }">
                <div class="bom-tree-node" :class="{ 'phantom-node': data.isPhantom }">
                  <el-tag
                    :type="data.nodeType === 'ASSEMBLY' ? 'warning' : data.nodeType === 'PHANTOM' ? 'info' : 'success'"
                    size="small"
                    :effect="data.nodeType === 'PHANTOM' ? 'plain' : 'light'"
                  >
                    {{ data.nodeType === 'ASSEMBLY' ? '组件' : data.nodeType === 'PHANTOM' ? '虚拟件' : '零件' }}
                  </el-tag>
                  <span class="node-label">
                    {{ data.materialCode }} - {{ data.materialName }}
                    <span v-if="data.materialSpec" class="node-spec">({{ data.materialSpec }})</span>
                  </span>
                  <span class="node-quantity" v-if="data.level > 0">
                    × {{ data.quantityPerParent }}
                    <span v-if="data.lossRate > 0" class="loss-rate">(损耗{{ data.lossRate }}%)</span>
                  </span>
                  <el-tag v-if="data.sourceType" size="small" type="info" class="source-tag">
                    {{ data.sourceType }}
                  </el-tag>
                </div>
              </template>
            </el-tree>
            <el-empty v-if="!treeLoading && bomTreeData.length === 0" description="暂无BOM数据" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="缩进式BOM" name="indented">
          <el-table :data="indentedBomData" v-loading="indentedLoading" style="width: 100%">
            <el-table-column label="层级" prop="level" width="60" align="center" />
            <el-table-column label="物料编码" prop="materialCode" width="150">
              <template #default="scope">
                <span :style="{ paddingLeft: scope.row.level * 20 + 'px' }">
                  <el-icon v-if="scope.row.expandable" style="color: #e6a23c"><Folder /></el-icon>
                  <el-icon v-else style="color: #67c23a"><Document /></el-icon>
                  {{ scope.row.materialCode }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" prop="materialName" min-width="150" />
            <el-table-column label="规格型号" prop="materialSpec" width="120" />
            <el-table-column label="类型" prop="nodeType" width="80" align="center">
              <template #default="scope">
                <el-tag
                  :type="scope.row.nodeType === 'ASSEMBLY' ? 'warning' : scope.row.nodeType === 'PHANTOM' ? 'info' : 'success'"
                  size="small"
                  :effect="scope.row.nodeType === 'PHANTOM' ? 'plain' : 'light'"
                >
                  {{ scope.row.nodeType === 'ASSEMBLY' ? '组件' : scope.row.nodeType === 'PHANTOM' ? '虚拟件' : '零件' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="单位用量" prop="quantityPerParent" width="100" align="right" />
            <el-table-column label="损耗率(%)" prop="lossRate" width="100" align="right" />
            <el-table-column label="累计用量" prop="cumulativeQuantity" width="120" align="right">
              <template #default="scope">
                <span :class="{ 'leaf-material': scope.row.nodeType === 'PART' }">
                  {{ scope.row.cumulativeQuantity }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="单位成本" prop="unitCost" width="100" align="right">
              <template #default="scope">
                {{ scope.row.unitCost != null ? scope.row.unitCost.toFixed(2) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="行成本" prop="lineCost" width="120" align="right">
              <template #default="scope">
                <span :class="{ 'leaf-material': scope.row.nodeType === 'PART' }">
                  {{ scope.row.lineCost != null ? scope.row.lineCost.toFixed(2) : '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="来源" prop="sourceType" width="80" align="center" />
            <el-table-column label="提前期(h)" prop="leadTime" width="100" align="right" />
          </el-table>
          <el-empty v-if="!indentedLoading && indentedBomData.length === 0" description="暂无BOM数据" />
        </el-tab-pane>

        <el-tab-pane label="成本卷积" name="cost">
          <div v-loading="costLoading">
            <template v-if="costRollupData">
              <!-- 成本汇总卡片 -->
              <el-row :gutter="16" style="margin-bottom: 16px">
                <el-col :span="8">
                  <el-card shadow="hover">
                    <el-statistic title="物料成本合计" :value="costRollupData.totalMaterialCost" :precision="2" prefix="¥" />
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card shadow="hover">
                    <el-statistic title="工序成本合计" :value="costRollupData.totalRoutingCost" :precision="2" prefix="¥" />
                    <el-tag v-if="!costRollupData.routingCostAvailable" type="warning" size="small" style="margin-top: 4px">
                      部分工序缺费率,工序成本为0
                    </el-tag>
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card shadow="hover">
                    <el-statistic title="单位总成本" :value="costRollupData.totalCostPerUnit" :precision="2" prefix="¥" />
                  </el-card>
                </el-col>
              </el-row>

              <!-- 物料成本明细 -->
              <h4>物料成本明细（按叶子聚合）</h4>
              <el-table :data="costRollupData.materialLines" size="small" style="width: 100%; margin-bottom: 16px">
                <el-table-column label="物料编码" prop="materialCode" width="140" />
                <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
                <el-table-column label="规格" prop="materialSpec" width="100" />
                <el-table-column label="累计用量" prop="cumulativeQuantity" width="120" align="right" />
                <el-table-column label="单位成本" prop="unitCost" width="100" align="right">
                  <template #default="scope">{{ scope.row.unitCost?.toFixed(2) }}</template>
                </el-table-column>
                <el-table-column label="行成本" prop="lineCost" width="120" align="right">
                  <template #default="scope">{{ scope.row.lineCost?.toFixed(2) }}</template>
                </el-table-column>
              </el-table>

              <!-- 工序成本明细 -->
              <h4>工序成本明细</h4>
              <el-table
                v-if="costRollupData.routingLines && costRollupData.routingLines.length > 0"
                :data="costRollupData.routingLines"
                size="small"
                style="width: 100%"
              >
                <el-table-column label="序号" prop="sequenceNum" width="60" align="center" />
                <el-table-column label="工序" prop="processName" width="120" />
                <el-table-column label="工位" prop="workstationName" width="120" />
                <el-table-column label="标准工时(h)" prop="standardHours" width="100" align="right" />
                <el-table-column label="人数" prop="laborCount" width="60" align="center" />
                <el-table-column label="人工费率" prop="laborRate" width="100" align="right" />
                <el-table-column label="机台数" prop="machineCount" width="60" align="center" />
                <el-table-column label="机器费率" prop="machineRate" width="100" align="right" />
                <el-table-column label="步骤成本" prop="stepCost" width="120" align="right">
                  <template #default="scope">{{ scope.row.stepCost?.toFixed(2) ?? '-' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="未关联工艺路线或无工序步骤" />
            </template>
            <el-empty v-else-if="!costLoading" description="暂无成本数据（请先保存BOM后查看）" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 物料选择对话框 -->
    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />

    <!-- 替代料管理对话框 -->
    <el-dialog title="替代料管理" v-model="alternativeOpen" width="1100px" append-to-body @close="handleAlternativeClose">
      <el-alert v-if="alternativeCurrentDetail" type="info" :closable="false" show-icon style="margin-bottom: 12px">
        子项物料：{{ alternativeCurrentDetail.childMaterialName }}（用量 {{ alternativeCurrentDetail.quantityPerParent }}）
      </el-alert>
      <el-row :gutter="10" class="mb8" v-if="!isViewMode">
        <el-col :span="1.5">
          <el-button type="primary" icon="Plus" @click="addAlternativeRow">添加替代料</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" icon="Check" @click="saveAlternatives">保存</el-button>
        </el-col>
      </el-row>
      <el-table :data="alternativeItems" style="width: 100%">
        <el-table-column type="index" width="50" align="center" label="序号" />
        <el-table-column label="替代物料" align="center" min-width="200">
          <template #default="scope">
            <el-input v-model="scope.row.altMaterialName" placeholder="请选择替代物料" readonly>
              <template #append>
                <el-button icon="Search" @click="openAltMaterialSelect(scope.$index)" :disabled="isViewMode" />
              </template>
            </el-input>
          </template>
        </el-table-column>
        <el-table-column label="替代用量" align="center" width="140">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.altQuantity"
              :min="0"
              :precision="6"
              controls-position="right"
              :disabled="isViewMode"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="优先级" align="center" width="110">
          <template #default="scope">
            <el-input-number v-model="scope.row.priority" :min="1" controls-position="right" :disabled="isViewMode" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="替代类型" align="center" width="130">
          <template #default="scope">
            <el-select v-model="scope.row.substitutionType" placeholder="请选择" :disabled="isViewMode" style="width: 100%">
              <el-option v-for="dict in bd_substitution_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" width="150">
          <template #default="scope">
            <el-date-picker
              v-model="scope.row.effectiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
              :readonly="isViewMode"
            />
          </template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" width="150">
          <template #default="scope">
            <el-date-picker
              v-model="scope.row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
              :readonly="isViewMode"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" v-if="!isViewMode">
          <template #default="scope">
            <el-button link type="danger" @click="removeAlternativeRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="alternativeItems.length === 0" description="暂无替代料" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="alternativeOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BomForm">
import {
  getBom,
  addBom,
  updateBom,
  listBomDetails,
  addBomDetail,
  updateBomDetail,
  delBomDetail,
  saveBomDetailsBatch,
  getBomTree,
  getIndentedBom,
  costRollup
} from '@/api/erp/bd/bom';
import { getMaterial } from '@/api/erp/bd/material';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';
import { listRouting, getRoutingSteps } from '@/api/erp/bd/routing';
import { listAlternativeByDetail, saveAlternativeBatch } from '@/api/erp/bd/bom/alternative';
import { useRouter, useRoute } from 'vue-router';

import type { BomForm } from '@/api/erp/bd/bom/types';

const router = useRouter();
const route = useRoute();
const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false, sys_core_status, bd_material_type, prd_material_source_type, prd_backflush_flag, bd_bom_type, bd_substitution_type } =
  toRefs<any>(
    proxy.useDict(
      'sys_true_or_false',
      'sys_core_status',
      'bd_material_type',
      'prd_material_source_type',
      'prd_backflush_flag',
      'bd_bom_type',
      'bd_substitution_type'
    )
  );

// 表单相关
const formRef = ref(null);
const form = ref<BomForm>({
  bomId: null,
  parentMaterialId: null,
  parentMaterialName: null,
  bomVersion: '1.0',
  bomName: null,
  effectiveDate: null,
  expiryDate: null,
  isCurrent: null,
  status: null,
  remark: null,
  sourceType: null,
  bomType: null,
  routingId: null
});

// 查看模式标识
const isViewMode = ref(false);

// 当前正在选择物料的行索引
const currentMaterialRowIndex = ref(-1);

const rules = ref({
  parentMaterialName: [{ required: true, message: '父项物料不能为空', trigger: 'change' }],
  bomVersion: [{ required: true, message: 'BOM版本号不能为空', trigger: 'blur' }],
  bomName: [{ required: true, message: 'BOM名称不能为空', trigger: 'blur' }]
});

// 物料子项表格数据
const materialItems = ref([
  {
    detailId: null,
    childMaterialId: null,
    childMaterialName: '',
    quantityPerParent: 1.0,
    lossRate: 0.0,
    operationStep: null,
    leadTime: 0,
    effectiveDate: null,
    expiryDate: null,
    sourceType: null,
    status: 1,
    remark: '',
    backflushFlag: 0
  }
]);

// 被删除的已有明细ID列表（编辑模式下用户点击"删除"移除的、已持久化的明细）
// 保存时统一调用后端删除接口，避免 saveBomDetailsBatch 仅 upsert 导致删除不生效
const removedDetailIds = ref<(string | number)[]>([]);

// BOM树形视图相关
const activeTab = ref('details');
const bomTreeData = ref([]);
const treeLoading = ref(false);
const treeProps = {
  children: 'children',
  label: 'materialName'
};
const indentedBomData = ref([]);
const indentedLoading = ref(false);

// 物料选择对话框
const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
// 物料选择模式：detail=选择子项物料，alt=选择替代物料
const materialSelectMode = ref('detail');
const currentAltRowIndex = ref(-1);

// 工艺路线选项
const routingOptions = ref([]);
const currentRoutingVersion = ref('');
const routingSteps = ref([]);

// 成本卷积相关
const costRollupData = ref(null);
const costLoading = ref(false);

// 替代料管理相关
const alternativeOpen = ref(false);
const alternativeItems = ref([]);
const alternativeCurrentDetail = ref(null);

// 页面加载时初始化数据
onMounted(() => {
  const id = route.query.id;
  const action = route.query.action;

  // 设置查看模式
  isViewMode.value = action === 'view';

  // 加载工艺路线选项
  loadRoutingOptions();

  if (id) {
    getBomDetails(id);
  }
});

// 加载工艺路线选项（仅加载启用状态）
function loadRoutingOptions() {
  listRouting({ status: 1, pageNum: 1, pageSize: 1000 })
    .then((response) => {
      routingOptions.value = response.rows || [];
    })
    .catch(() => {
      routingOptions.value = [];
    });
}

// 工艺路线变更：同步显示版本号，并加载该路线的工序步骤供BOM行选择
function handleRoutingChange(routingId) {
  const matched = routingOptions.value.find((r) => r.routingId === routingId);
  currentRoutingVersion.value = matched ? matched.routingVersion : '';
  if (routingId) {
    getRoutingSteps(routingId)
      .then((res) => {
        routingSteps.value = res.data || [];
      })
      .catch(() => {
        routingSteps.value = [];
      });
  } else {
    routingSteps.value = [];
  }
}

// 获取BOM详情
function getBomDetails(id) {
  // 获取BOM主表信息
  getBom(id).then((response) => {
    form.value = response.data;

    // 如果有 parentMaterialId 但没有 parentMaterialName，则查询物料信息
    if (form.value.parentMaterialId) {
      getMaterialNameById(form.value.parentMaterialId);
    }

    // 同步工艺路线版本号显示
    if (form.value.routingId) {
      handleRoutingChange(form.value.routingId);
    }

    // 获取BOM子表信息
    getBomDetailsList(id);
  });
}

// 获取BOM子表数据
function getBomDetailsList(bomId) {
  // 重新加载明细时清空删除记录，避免残留已不存在的 detailId
  removedDetailIds.value = [];
  listBomDetails(bomId)
    .then((response) => {
      if (response.rows && response.rows.length > 0) {
        materialItems.value = response.rows.map((item) => ({
          detailId: item.detailId || null,
          childMaterialId: item.childMaterialId || null,
          childMaterialName: item.childMaterialName || '',
          quantityPerParent: item.quantityPerParent !== undefined ? item.quantityPerParent : 1.0,
          lossRate: item.lossRate !== undefined ? item.lossRate : 0.0,
          operationStep: item.operationStep || null,
          leadTime: item.leadTime !== undefined ? item.leadTime : 0,
          effectiveDate: item.effectiveDate || null,
          expiryDate: item.expiryDate || null,
          sourceType: item.sourceType || null,
          status: item.status !== undefined ? item.status : 1,
          remark: item.remark || '',
          backflushFlag: item.backflushFlag !== undefined ? item.backflushFlag : 0
        }));
      } else {
        // 如果没有子表数据，保持默认的一行空数据
        materialItems.value = [
          {
            detailId: null,
            childMaterialId: null,
            childMaterialName: '',
            quantityPerParent: 1.0,
            lossRate: 0.0,
            operationStep: null,
            leadTime: 0,
            effectiveDate: null,
            expiryDate: null,
            sourceType: null,
            status: 1,
            remark: '',
            backflushFlag: 0
          }
        ];
      }
    })
    .catch(() => {
      console.warn('获取BOM明细数据失败');
    });
}

// 加载BOM树形数据
function loadBomTree() {
  if (!form.value.bomId) return;
  treeLoading.value = true;
  getBomTree(form.value.bomId)
    .then((response) => {
      bomTreeData.value = response.data ? [response.data] : [];
      treeLoading.value = false;
    })
    .catch(() => {
      treeLoading.value = false;
    });
}

// 加载缩进式BOM数据
function loadIndentedBom() {
  if (!form.value.bomId) return;
  indentedLoading.value = true;
  getIndentedBom(form.value.bomId)
    .then((response) => {
      indentedBomData.value = response.rows || [];
      indentedLoading.value = false;
    })
    .catch(() => {
      indentedLoading.value = false;
    });
}

// Tab切换处理
function handleTabChange(tab) {
  if (tab === 'tree' && bomTreeData.value.length === 0) {
    loadBomTree();
  } else if (tab === 'indented' && indentedBomData.value.length === 0) {
    loadIndentedBom();
  } else if (tab === 'cost' && !costRollupData.value && form.value.bomId) {
    loadCostRollup();
  }
}

// 加载成本卷积
function loadCostRollup() {
  if (!form.value.bomId) return;
  costLoading.value = true;
  costRollup(form.value.bomId)
    .then((res) => {
      costRollupData.value = res.data;
    })
    .catch(() => {
      costRollupData.value = null;
    })
    .finally(() => {
      costLoading.value = false;
    });
}

// 新增方法：通过ID获取物料名称
function getMaterialNameById(materialId) {
  getMaterial(materialId)
    .then((response) => {
      form.value.parentMaterialName = response.data.materialName;
    })
    .catch(() => {
      console.warn('获取物料名称失败');
    });
}

// 打开物料选择对话框
function openMaterialSelect(rowIndex) {
  materialSelectMode.value = 'detail';
  currentMaterialRowIndex.value = rowIndex;
  materialDialogTitle.value = '选择子项物料';
  materialSelectDialogRef.value?.open();
}

// 打开替代料物料选择对话框
function openAltMaterialSelect(rowIndex) {
  materialSelectMode.value = 'alt';
  currentAltRowIndex.value = rowIndex;
  materialDialogTitle.value = '选择替代物料';
  materialSelectDialogRef.value?.open();
}

// 物料选择回调
function handleMaterialSelected(row) {
  if (materialSelectMode.value === 'alt') {
    if (currentAltRowIndex.value >= 0) {
      alternativeItems.value[currentAltRowIndex.value].altMaterialId = row.materialId;
      alternativeItems.value[currentAltRowIndex.value].altMaterialCode = row.materialCode;
      alternativeItems.value[currentAltRowIndex.value].altMaterialName = row.materialName;
    }
  } else if (currentMaterialRowIndex.value >= 0) {
    materialItems.value[currentMaterialRowIndex.value].childMaterialId = row.materialId;
    materialItems.value[currentMaterialRowIndex.value].childMaterialName = row.materialName;
  }
  currentMaterialRowIndex.value = -1;
  currentAltRowIndex.value = -1;
}

// 打开替代料管理对话框
function openAlternativeDialog(detailRow) {
  if (!detailRow.detailId) {
    proxy.$modal.msgWarning('请先保存BOM明细后再管理替代料');
    return;
  }
  if (!form.value.bomId) {
    proxy.$modal.msgWarning('请先保存BOM主表');
    return;
  }
  alternativeCurrentDetail.value = detailRow;
  alternativeOpen.value = true;
  listAlternativeByDetail(detailRow.detailId)
    .then((response) => {
      alternativeItems.value = (response.data || []).map((item) => ({
        alternativeId: item.alternativeId,
        detailId: item.detailId,
        bomId: item.bomId,
        altMaterialId: item.altMaterialId,
        altMaterialCode: item.altMaterialCode,
        altMaterialName: item.altMaterialName,
        altQuantity: item.altQuantity,
        priority: item.priority,
        substitutionType: item.substitutionType,
        effectiveDate: item.effectiveDate,
        expiryDate: item.expiryDate,
        status: item.status,
        remark: item.remark
      }));
    })
    .catch(() => {
      alternativeItems.value = [];
    });
}

// 新增替代料行
function addAlternativeRow() {
  alternativeItems.value.push({
    alternativeId: null,
    detailId: alternativeCurrentDetail.value.detailId,
    bomId: form.value.bomId,
    altMaterialId: null,
    altMaterialCode: '',
    altMaterialName: '',
    altQuantity: alternativeCurrentDetail.value.quantityPerParent || 1,
    priority: alternativeItems.value.length + 1,
    substitutionType: 'FULL',
    effectiveDate: null,
    expiryDate: null,
    status: 1,
    remark: ''
  });
}

// 删除替代料行
function removeAlternativeRow(index) {
  alternativeItems.value.splice(index, 1);
}

// 保存替代料（批量：先删后插）
function saveAlternatives() {
  const detailId = alternativeCurrentDetail.value.detailId;
  const bomId = form.value.bomId;
  // 校验替代料行
  const invalid = alternativeItems.value.find((item) => !item.altMaterialId || item.altQuantity === null || item.altQuantity === undefined);
  if (invalid) {
    proxy.$modal.msgWarning('请完善替代物料和替代用量');
    return;
  }
  saveAlternativeBatch(detailId, bomId, alternativeItems.value)
    .then(() => {
      proxy.$modal.msgSuccess('替代料保存成功');
    })
    .catch(() => {
      proxy.$modal.msgError('替代料保存失败');
    });
}

// 替代料对话框关闭
function handleAlternativeClose() {
  alternativeItems.value = [];
  alternativeCurrentDetail.value = null;
}

// 清除已选择的物料
function clearMaterial(rowIndex) {
  materialItems.value[rowIndex].childMaterialId = null;
  materialItems.value[rowIndex].childMaterialName = '';
}

// 新增物料行
function addMaterialRow() {
  materialItems.value.push({
    detailId: null,
    childMaterialId: null,
    childMaterialName: '',
    quantityPerParent: 1.0,
    lossRate: 0.0,
    operationStep: null,
    leadTime: 0,
    effectiveDate: null,
    expiryDate: null,
    sourceType: null,
    status: 1,
    remark: '',
    backflushFlag: 0
  });
}

// 删除物料行
function removeMaterialRow(index) {
  if (materialItems.value.length <= 1) {
    proxy.$modal.msgWarning('至少保留一行数据');
    return;
  }
  // 记录被删除的已有明细ID，保存时统一调用后端删除接口
  const removed = materialItems.value[index];
  if (removed && removed.detailId) {
    removedDetailIds.value.push(removed.detailId);
  }
  materialItems.value.splice(index, 1);
}

// 保存按钮
function handleSave() {
  formRef.value.validate((valid) => {
    if (valid) {
      if (form.value.bomId) {
        // 更新BOM主表和子表
        updateBomAndDetails();
      } else {
        // 新增BOM主表和子表
        addBomAndDetails();
      }
    }
  });
}

// 新增BOM主表和子表数据
function addBomAndDetails() {
  // 先保存主表
  addBom(form.value)
    .then((response) => {
      const bomId = response.data.bomId; // 获取新创建的BOM ID
      proxy.$modal.msgSuccess('主表新增成功');

      // 保存子表数据
      saveBomDetails(bomId)
        .then(() => {
          proxy.$modal.msgSuccess('子表保存成功');
          handleClose();
        })
        .catch((error) => {
          console.error('保存子表失败:', error);
          proxy.$modal.msgError('子表保存失败');
        });
    })
    .catch((error) => {
      console.error('新增主表失败:', error);
      proxy.$modal.msgError('主表新增失败');
    });
}

// 更新BOM主表和子表数据
function updateBomAndDetails() {
  // 先更新主表
  updateBom(form.value)
    .then((response) => {
      proxy.$modal.msgSuccess('主表修改成功');

      // 保存子表数据
      saveBomDetails(form.value.bomId)
        .then(() => {
          proxy.$modal.msgSuccess('子表保存成功');
          handleClose();
        })
        .catch((error) => {
          console.error('保存子表失败:', error);
          proxy.$modal.msgError('子表保存失败');
        });
    })
    .catch((error) => {
      console.error('更新主表失败:', error);
      proxy.$modal.msgError('主表更新失败');
    });
}

// 保存BOM明细数据的通用方法
// 流程：1) 删除用户在编辑过程中移除的已有明细 2) 批量保存（upsert）有效行
function saveBomDetails(bomId) {
  // 过滤掉空行数据并添加 bomId
  const validMaterialItems = materialItems.value
    .filter((item) => item.childMaterialId && item.childMaterialName)
    .map((item) => ({
      ...item,
      bomId: bomId,
      sourceType: item.sourceType || form.value.sourceType
    }));

  // 1. 先删除被移除的已有明细（即使没有有效行也要执行删除）
  const deleteRemoved = removedDetailIds.value.length > 0 ? delBomDetail(removedDetailIds.value) : Promise.resolve();

  // 2. 再批量保存有效行
  const saveValid = validMaterialItems.length > 0 ? saveBomDetailsBatch(validMaterialItems) : Promise.resolve();

  return Promise.all([deleteRemoved, saveValid]).then(() => {
    // 保存成功后清空删除记录，避免重复提交
    removedDetailIds.value = [];
  });
}

// 关闭按钮
function handleClose() {
  //router.push('/erp/bd/bom')
  const obj = { path: '/erp/bd/bom' };
  proxy.$tab.closeOpenPage(obj);
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

:deep(.el-input-number .el-input__inner) {
  text-align: center;
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

.bom-tree-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
  min-height: 300px;
  max-height: 600px;
  overflow-y: auto;
}

.bom-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  flex: 1;
}

/* 虚拟件节点：灰色虚线样式，提示该层会被跳过 */
.bom-tree-node.phantom-node .node-label {
  color: #909399;
  font-style: italic;
  text-decoration: line-through dashed;
}

.node-label {
  font-size: 14px;
  color: #303133;
}

.node-spec {
  color: #909399;
  font-size: 12px;
}

.node-quantity {
  color: #409eff;
  font-weight: 500;
  margin-left: 8px;
}

.loss-rate {
  color: #e6a23c;
  font-size: 12px;
}

.source-tag {
  margin-left: auto;
}

.leaf-material {
  color: #67c23a;
  font-weight: 600;
}

:deep(.el-tree-node__content) {
  height: 36px;
}
</style>
