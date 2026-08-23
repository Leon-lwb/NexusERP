<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && !props.disabled && form.woStatus === 'CREATED' && form.woId"
              >提交审批</el-button
            >
            <el-button type="info" @click="onViewFlow" v-if="form.woStatus === 'APPROVING' || form.woStatus === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="workRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="工单编号" prop="woNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.woNo" placeholder="请输入工单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
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
            <el-form-item label="来源计划" prop="planName">
              <el-input v-model="form.planName" placeholder="请选择来源计划" readonly>
                <template #append>
                  <el-button icon="Search" @click="openPlanSelect" :disabled="isViewMode || props.disabled" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option v-for="dict in prd_source_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="销售订单编号" prop="sourceNo">
              <el-input v-model="form.sourceNo" placeholder="选计划后自动带出" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="产品编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请选择产品物料" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect" :disabled="isViewMode || props.disabled" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="产品名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="form.plannedQty" :min="0" :precision="2" :step="1" placeholder="请输入计划数量" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="已完成数量" prop="completedQty">
              <el-input-number v-model="form.completedQty" :min="0" :precision="2" :step="1" placeholder="报工回写" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="报废数量" prop="scrapQty">
              <el-input-number v-model="form.scrapQty" :min="0" :precision="2" :step="1" placeholder="报工回写" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单位" prop="unitId">
              <el-select v-model="form.unitId" placeholder="请选择单位" @change="handleUnitChange">
                <el-option v-for="item in uomOptions" :key="item.uomId" :label="item.uomName" :value="item.uomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BOM名称" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请选择BOM" readonly>
                <template #append>
                  <el-button icon="Search" @click="openBomSelect" :disabled="isViewMode || props.disabled" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="BOM版本" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="选择BOM后自动带出" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主车间" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请选择主车间" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMainWorkshopSelect" :disabled="isViewMode || props.disabled" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">资源分配</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配车间" prop="workshopList">
              <div style="display: flex; align-items: flex-start; gap: 10px">
                <el-select
                  v-model="form.workshopList"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="3"
                  placeholder="请选择车间"
                  style="flex: 1; min-width: 300px"
                  :disabled="isViewMode || props.disabled"
                  @remove-tag="handleWorkshopRemove"
                >
                  <el-option v-for="item in workshopOptions" :key="item.workshopId" :label="item.workshopName" :value="item.workshopId" />
                </el-select>
                <el-button type="primary" icon="Search" @click="openWorkshopSelect" :disabled="isViewMode || props.disabled">选择</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">分配工作站</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              icon="Plus"
              @click="openWorkstationSelect"
              :disabled="isViewMode || !form.workshopList || form.workshopList.length === 0"
              v-if="!isViewMode && !props.disabled"
              >添加工作站</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <span v-if="!isViewMode && !props.disabled && (!form.workshopList || form.workshopList.length === 0)" style="color: #909399"
              >请先选择车间</span
            >
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配工作站">
              <el-table :data="form.workstationList" border size="small" style="width: 100%; margin-bottom: 10px">
                <el-table-column type="index" label="顺序" width="60" align="center" />
                <el-table-column label="工作站编码" prop="resourceCode" width="120" />
                <el-table-column label="工作站名称" prop="resourceName" />
                <el-table-column label="所属车间" prop="parentName" width="150" />
                <el-table-column label="操作" width="100" align="center" v-if="!isViewMode && !props.disabled">
                  <template #default="scope">
                    <el-button type="danger" link icon="Delete" @click="removeWorkstation(scope.$index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">分配班组</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              icon="Plus"
              @click="openTeamSelect"
              :disabled="isViewMode || !form.workshopList || form.workshopList.length === 0"
              v-if="!isViewMode && !props.disabled"
              >添加班组</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <span v-if="!isViewMode && !props.disabled && (!form.workshopList || form.workshopList.length === 0)" style="color: #909399"
              >请先选择车间</span
            >
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配班组">
              <el-table :data="form.teamList" border size="small" style="width: 100%; margin-bottom: 10px">
                <el-table-column type="index" label="顺序" width="60" align="center" />
                <el-table-column label="班组编码" prop="resourceCode" width="120" />
                <el-table-column label="班组名称" prop="resourceName" />
                <el-table-column label="所属车间" prop="parentName" width="150" />
                <el-table-column label="操作" width="100" align="center" v-if="!isViewMode && !props.disabled">
                  <template #default="scope">
                    <el-button type="danger" link icon="Delete" @click="removeTeam(scope.$index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">计划信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="负责人" prop="responsibleName">
              <el-input v-model="form.responsibleName" placeholder="请选择负责人" readonly>
                <template #append>
                  <el-button icon="Search" @click="openUserSelect" :disabled="isViewMode || props.disabled" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker clearable v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划开始日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker clearable v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划结束日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="实际开始时间" prop="actualStartDate">
              <el-date-picker clearable v-model="form.actualStartDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择实际开始时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实际结束时间" prop="actualEndDate">
              <el-date-picker clearable v-model="form.actualEndDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择实际结束时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="产品规格" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">审批信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode || props.disabled || form.woStatus !== 'CREATED'"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工单状态" prop="woStatus">
              <dict-tag :options="prd_wo_status" :value="form.woStatus" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否倒冲" prop="backflushFlag">
              <el-switch v-model="form.backflushFlag" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="form.woStatus === 'CREATED' && flowOptions.length === 0 && !form.selectFlowId">
          <el-col :span="24">
            <el-alert
              type="warning"
              show-icon
              :closable="false"
              title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 prdWorkOrderService。"
            />
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-dialog title="选择生产计划" v-model="planSelectOpen" width="1000px" append-to-body>
      <el-form :model="planQueryParams" :inline="true">
        <el-form-item label="计划编号" prop="planNo">
          <el-input v-model="planQueryParams.planNo" placeholder="请输入计划编号" clearable @keyup.enter="getPlanList" />
        </el-form-item>
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="planQueryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="getPlanList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getPlanList">搜索</el-button>
          <el-button icon="Refresh" @click="resetPlanQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="planLoading" :data="planList" @row-dblclick="selectPlan">
        <el-table-column label="计划编号" align="center" prop="planNo" />
        <el-table-column label="计划名称" align="center" prop="planName" />
        <el-table-column label="计划类型" align="center" prop="planType">
          <template #default="scope">
            <dict-tag :options="prd_plan_type" :value="scope.row.planType" />
          </template>
        </el-table-column>
        <el-table-column label="计划日期" align="center" prop="planDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="prd_plan_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectPlan(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="planTotal > 0"
        :total="planTotal"
        v-model:page="planQueryParams.pageNum"
        v-model:limit="planQueryParams.pageSize"
        @pagination="getPlanList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="planSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择产品物料" @select="handleMaterialSelected" />

    <el-dialog title="选择BOM" v-model="bomSelectOpen" width="1200px" append-to-body>
      <el-form :model="bomQueryParams" :inline="true" label-width="80px">
        <el-form-item label="BOM名称" prop="bomName">
          <el-input v-model="bomQueryParams.bomName" placeholder="请输入BOM名称" clearable @keyup.enter="getBomList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getBomList">搜索</el-button>
          <el-button
            icon="Refresh"
            @click="
              bomQueryParams.bomName = null;
              getBomList();
            "
            >重置</el-button
          >
        </el-form-item>
      </el-form>

      <el-table v-loading="bomLoading" :data="bomList" @row-dblclick="selectBom" highlight-current-row style="width: 100%" height="400">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="BOM版本号" align="center" prop="bomVersion" />
        <el-table-column label="BOM名称" align="center" prop="bomName" />
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectBom(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="bomTotal > 0"
        :total="bomTotal"
        v-model:page="bomQueryParams.pageNum"
        v-model:limit="bomQueryParams.pageSize"
        @pagination="getBomList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="bomSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择车间" v-model="workshopSelectOpen" width="800px" append-to-body>
      <el-form :model="workshopQueryParams" :inline="true">
        <el-form-item label="车间编码" prop="workshopCode">
          <el-input v-model="workshopQueryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item label="车间名称" prop="workshopName">
          <el-input v-model="workshopQueryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWorkshopList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWorkshopQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="workshopLoading" :data="workshopList" @selection-change="handleWorkshopSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="车间编码" align="center" prop="workshopCode" />
        <el-table-column label="车间名称" align="center" prop="workshopName" />
        <el-table-column label="车间类型" align="center" prop="workshopType" />
        <el-table-column label="负责人" align="center" prop="managerName" />
      </el-table>

      <pagination
        v-show="workshopTotal > 0"
        :total="workshopTotal"
        v-model:page="workshopQueryParams.pageNum"
        v-model:limit="workshopQueryParams.pageSize"
        @pagination="getWorkshopList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmWorkshopSelect">确 定</el-button>
          <el-button @click="workshopSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择主车间" v-model="mainWorkshopSelectOpen" width="800px" append-to-body>
      <el-form :model="mainWorkshopQueryParams" :inline="true">
        <el-form-item label="车间编码" prop="workshopCode">
          <el-input v-model="mainWorkshopQueryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="getMainWorkshopList" />
        </el-form-item>
        <el-form-item label="车间名称" prop="workshopName">
          <el-input v-model="mainWorkshopQueryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="getMainWorkshopList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getMainWorkshopList">搜索</el-button>
          <el-button icon="Refresh" @click="resetMainWorkshopQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="mainWorkshopLoading" :data="mainWorkshopList" highlight-current-row @current-change="handleMainWorkshopSelect">
        <el-table-column label="车间编码" align="center" prop="workshopCode" />
        <el-table-column label="车间名称" align="center" prop="workshopName" />
        <el-table-column label="车间类型" align="center" prop="workshopType" />
        <el-table-column label="负责人" align="center" prop="managerName" />
      </el-table>

      <pagination
        v-show="mainWorkshopTotal > 0"
        :total="mainWorkshopTotal"
        v-model:page="mainWorkshopQueryParams.pageNum"
        v-model:limit="mainWorkshopQueryParams.pageSize"
        @pagination="getMainWorkshopList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmMainWorkshopSelect">确 定</el-button>
          <el-button @click="mainWorkshopSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择工作站" v-model="workstationSelectOpen" width="800px" append-to-body>
      <el-alert
        v-if="form.workshopList && form.workshopList.length > 0"
        :title="`已选车间：${getSelectedWorkshopNames()}`"
        type="info"
        :closable="false"
        style="margin-bottom: 15px"
      />
      <el-form :model="workstationQueryParams" :inline="true">
        <el-form-item label="所属车间" prop="workshopId">
          <el-select v-model="workstationQueryParams.workshopId" placeholder="请选择车间" clearable style="width: 200px">
            <el-option v-for="item in selectedWorkshopOptions" :key="item.workshopId" :label="item.workshopName" :value="item.workshopId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作站编码" prop="workstationCode">
          <el-input v-model="workstationQueryParams.workstationCode" placeholder="请输入工作站编码" clearable @keyup.enter="getWorkstationList" />
        </el-form-item>
        <el-form-item label="工作站名称" prop="workstationName">
          <el-input v-model="workstationQueryParams.workstationName" placeholder="请输入工作站名称" clearable @keyup.enter="getWorkstationList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWorkstationList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWorkstationQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="workstationLoading" :data="filteredWorkstationList" @selection-change="handleWorkstationSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="工作站编码" align="center" prop="workstationCode" />
        <el-table-column label="工作站名称" align="center" prop="workstationName" />
        <el-table-column label="所属车间" align="center" prop="workshopName" />
        <el-table-column label="设备类型" align="center" prop="equipmentType" />
      </el-table>

      <pagination
        v-show="workstationTotal > 0"
        :total="workstationTotal"
        v-model:page="workstationQueryParams.pageNum"
        v-model:limit="workstationQueryParams.pageSize"
        @pagination="getWorkstationList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmWorkstationSelect">确 定</el-button>
          <el-button @click="workstationSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择班组" v-model="teamSelectOpen" width="800px" append-to-body>
      <el-form :model="teamQueryParams" :inline="true">
        <el-form-item label="班组编码" prop="teamCode">
          <el-input v-model="teamQueryParams.teamCode" placeholder="请输入班组编码" clearable @keyup.enter="getTeamList" />
        </el-form-item>
        <el-form-item label="班组名称" prop="teamName">
          <el-input v-model="teamQueryParams.teamName" placeholder="请输入班组名称" clearable @keyup.enter="getTeamList" />
        </el-form-item>
        <el-form-item label="所属车间" prop="workshopId">
          <el-select v-model="teamQueryParams.workshopId" placeholder="请选择车间" clearable style="width: 150px">
            <el-option v-for="item in selectedWorkshopOptions" :key="item.workshopId" :label="item.workshopName" :value="item.workshopId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getTeamList">搜索</el-button>
          <el-button icon="Refresh" @click="resetTeamQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="teamLoading" :data="teamList" @selection-change="handleTeamSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="班组编码" align="center" prop="teamCode" />
        <el-table-column label="班组名称" align="center" prop="teamName" />
        <el-table-column label="所属车间" align="center" prop="workshopName" />
        <el-table-column label="班组类型" align="center" prop="teamType" />
        <el-table-column label="负责人" align="center" prop="leaderName" />
      </el-table>

      <pagination
        v-show="teamTotal > 0"
        :total="teamTotal"
        v-model:page="teamQueryParams.pageNum"
        v-model:limit="teamQueryParams.pageSize"
        @pagination="getTeamList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmTeamSelect">确 定</el-button>
          <el-button @click="teamSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择负责人" v-model="userSelectOpen" width="800px" append-to-body>
      <el-form :model="userQueryParams" :inline="true">
        <el-form-item label="用户编码" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户编码" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item label="用户名称" prop="nickName">
          <el-input v-model="userQueryParams.nickName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getUserList">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" highlight-current-row @current-change="handleUserSelect">
        <el-table-column label="用户编码" align="center" prop="userName" />
        <el-table-column label="用户名称" align="center" prop="nickName" />
        <el-table-column label="部门" align="center" prop="deptName" />
      </el-table>

      <pagination
        v-show="userTotal > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmUserSelect">确 定</el-button>
          <el-button @click="userSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PrdWorkForm">
import { getWork, addWork, updateWork, submitWorkFlow, getWorkProcId } from '@/api/erp/prd/work';
import { listPlan } from '@/api/erp/prd/plan';
import { previewCode } from '@/api/system/autocode/rule';
import { getOrderLine } from '@/api/erp/so/orderheader';
import { listWorkshop } from '@/api/erp/bd/workshop';
import { listWorkstation } from '@/api/erp/bd/workstation';
import { listTeam } from '@/api/erp/sch/team';
import { listUser } from '@/api/system/user';
import { listMeasureAll } from '@/api/erp/bd/measure';
import { listBom } from '@/api/erp/bd/bom';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { getWorkOrderResourceByWoId, saveWorkOrderResourceBatch, RESOURCE_TYPE } from '@/api/erp/prd/work-order-resource';
import { getProcesss } from '@/api/workflow/process/index';
import type { WorkForm } from '@/api/erp/prd/work/types';

const { proxy } = getCurrentInstance() as any;
const { prd_source_type, so_order_priority, prd_wo_status, prd_plan_type, prd_plan_status, prd_backflush_flag } = toRefs<any>(
  proxy.useDict('prd_source_type', 'so_order_priority', 'prd_wo_status', 'prd_plan_type', 'prd_plan_status', 'prd_backflush_flag')
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

const form = ref<WorkForm>({});

const selectedWorkshopOptions = computed(() => {
  if (!form.value.workshopList || !workshopOptions.value) {
    return [];
  }
  return workshopOptions.value.filter((item) => form.value.workshopList.includes(item.workshopId));
});

const filteredWorkstationList = computed(() => {
  if (!workstationList.value || !form.value.workshopList) {
    return [];
  }
  return workstationList.value.filter((item) => form.value.workshopList.includes(item.workshopId));
});
const isViewMode = ref(false);
const title = ref('');
const autoGenFlag = ref(false);
const loadingFlow = ref(false);
const flowOptions = ref([]);

// 表单禁用状态（考虑 workflow 嵌入和查看模式）
const formDisabled = computed(() => isViewMode.value || props.disabled);

const rules = ref({
  woNo: [{ required: true, message: '工单编号不能为空', trigger: 'blur' }],
  materialName: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
});

const planSelectOpen = ref(false);
const planList = ref([]);
const planLoading = ref(false);
const planTotal = ref(0);
const planQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  planNo: null,
  planName: null,
  isCurrent: 1,
  status: 'CONFIRMED'
});

// 物料选择
const materialSelectDialogRef = ref<any>(null);

// 计量单位下拉
const uomOptions = ref<any[]>([]);

// BOM 选择
const bomSelectOpen = ref(false);
const bomList = ref([]);
const bomLoading = ref(false);
const bomTotal = ref(0);
const bomQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  bomName: null,
  isCurrent: 1,
  status: 1
});

const workshopSelectOpen = ref(false);
const workshopList = ref([]);
const workshopLoading = ref(false);
const workshopTotal = ref(0);
const workshopQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workshopCode: null,
  workshopName: null
});
const workshopOptions = ref([]);
const selectedWorkshops = ref([]);

const mainWorkshopSelectOpen = ref(false);
const mainWorkshopList = ref([]);
const mainWorkshopLoading = ref(false);
const mainWorkshopTotal = ref(0);
const mainWorkshopQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workshopCode: null,
  workshopName: null
});
const selectedMainWorkshop = ref(null);

const workstationSelectOpen = ref(false);
const workstationList = ref([]);
const workstationLoading = ref(false);
const workstationTotal = ref(0);
const workstationQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workstationCode: null,
  workstationName: null,
  workshopId: null
});
const selectedWorkstations = ref([]);

const teamSelectOpen = ref(false);
const teamList = ref([]);
const teamLoading = ref(false);
const teamTotal = ref(0);
const teamQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  teamCode: null,
  teamName: null,
  workshopId: null
});
const selectedTeams = ref([]);

const userSelectOpen = ref(false);
const userList = ref([]);
const userLoading = ref(false);
const userTotal = ref(0);
const userQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  userName: null,
  nickName: null
});
const selectedUser = ref(null);

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action;
  isViewMode.value = action === 'view';

  loadUomList();

  if (action === 'add') {
    loadFlows();
  }

  if (id) {
    initPage(id);
  }
});

function initPage(id) {
  const action = route.query.action;

  if (action === 'view') {
    isViewMode.value = true;
    title.value = '查看生产工单';
    if (id) {
      getInfo(id);
    }
  } else if (action === 'edit') {
    isViewMode.value = false;
    title.value = '修改生产工单';
    if (id) {
      getInfo(id);
    }
    loadFlows();
  } else if (action === 'add') {
    isViewMode.value = false;
    title.value = '新增生产工单';
    reset();
  }
}

function getInfo(id) {
  getWork(id).then((response) => {
    form.value = response.data;
    if (!form.value.workshopList) {
      form.value.workshopList = [];
    }
    if (!form.value.workstationList) {
      form.value.workstationList = [];
    }
    if (!form.value.teamList) {
      form.value.teamList = [];
    }
    loadWorkshopOptions().then(() => {
      loadWorkOrderResources(id);
    });
    tryAutoSelect();
  });
}

function loadWorkOrderResources(woId) {
  getWorkOrderResourceByWoId(woId)
    .then((response) => {
      const resources = response.data || [];

      form.value.workshopList = [];
      form.value.workstationList = [];
      form.value.teamList = [];

      resources.forEach((resource) => {
        if (resource.resourceType === RESOURCE_TYPE.WORKSHOP) {
          form.value.workshopList.push(resource.resourceId);
        } else if (resource.resourceType === RESOURCE_TYPE.WORKSTATION) {
          form.value.workstationList.push({
            resourceId: resource.resourceId,
            resourceCode: resource.resourceCode,
            resourceName: resource.resourceName,
            parentId: resource.parentId,
            parentName: getWorkshopNameById(resource.parentId)
          });
        } else if (resource.resourceType === RESOURCE_TYPE.TEAM) {
          form.value.teamList.push({
            resourceId: resource.resourceId,
            resourceCode: resource.resourceCode,
            resourceName: resource.resourceName,
            parentId: resource.parentId,
            parentName: getWorkshopNameById(resource.parentId)
          });
        }
      });

      form.value.workstationList.sort((a, b) => a.seq - b.seq);
      form.value.teamList.sort((a, b) => a.seq - b.seq);
    })
    .catch((error) => {
      // 资源接口失败（权限/网络/异常）此前被静默吞掉，导致车间/工作站/班组列表空白
      console.error('加载工单资源分配失败 woId=' + woId, error);
      proxy.$modal.msgWarning('加载车间/工作站/班组分配失败，请检查接口权限或网络');
    });
}

function getWorkshopNameById(workshopId) {
  const workshop = workshopOptions.value.find((w) => w.workshopId === workshopId);
  return workshop ? workshop.workshopName : '';
}

function getSelectedWorkshopNames() {
  if (!form.value.workshopList || !workshopOptions.value) {
    return '';
  }
  const names = form.value.workshopList.map((id) => {
    const workshop = workshopOptions.value.find((w) => w.workshopId === id);
    return workshop ? workshop.workshopName : '';
  });
  return names.join('、');
}

function reset() {
  autoGenFlag.value = false;
  form.value = {
    woId: null,
    woNo: null,
    planId: null,
    planName: null,
    sourceType: null,
    sourceId: null,
    sourceNo: null,
    sourceLineId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    plannedQty: null,
    completedQty: null,
    scrapQty: null,
    unitId: null,
    unitName: null,
    planStartDate: null,
    planEndDate: null,
    actualStartDate: null,
    actualEndDate: null,
    woStatus: null,
    productionLineId: null,
    workshopList: [],
    workstationList: [],
    teamList: [],
    responsibleId: null,
    responsibleName: null,
    priority: null,
    bomId: null,
    bomVersion: null,
    remark: null,
    bomName: null,
    backflushFlag: 0,
    attr2: null,
    attr3: null,
    attr4: null,
    selectFlowId: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm('workRef');
}

function submitForm() {
  proxy.$refs['workRef'].validate((valid) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.woNo = undefined;
      }
      const resourceList = buildResourceList();

      if (form.value.woId != null) {
        updateWork(form.value).then((response) => {
          saveWorkOrderResourceBatch(form.value.woId, resourceList)
            .then(() => {
              proxy.$modal.msgSuccess('修改成功');
              router.push('/erp/prdExec/work');
            })
            .catch((error) => {
              // 资源分配保存失败此前被静默吞掉（工单已更新但资源未落库）
              console.error('保存资源分配失败:', error);
              proxy.$modal.msgWarning('工单已保存，但资源分配保存失败，请编辑后重新保存');
              router.push('/erp/prdExec/work');
            });
        });
      } else {
        addWork(form.value)
          .then((response) => {
            const woId = response.data?.woId || response.data?.data?.woId || response.data;
            if (!woId) {
              proxy.$modal.msgError('获取工单ID失败');
              return;
            }
            saveWorkOrderResourceBatch(woId, resourceList)
              .then(() => {
                proxy.$modal.msgSuccess('新增成功');
                router.push('/erp/prdExec/work');
              })
              .catch((error) => {
                console.error('保存资源分配失败:', error);
                proxy.$modal.msgWarning('工单已保存，但资源分配保存失败，请编辑后重新保存');
                router.push('/erp/prdExec/work');
              });
          })
          .catch((error) => {
            console.error('保存工单失败:', error);
            proxy.$modal.msgError('保存失败');
          });
      }
    }
  });
}

function buildResourceList() {
  const resourceList = [];

  if (form.value.workshopList && form.value.workshopList.length > 0) {
    form.value.workshopList.forEach((workshopId, index) => {
      const workshop = workshopOptions.value.find((w) => w.workshopId === workshopId);
      if (workshop) {
        resourceList.push({
          resourceType: RESOURCE_TYPE.WORKSHOP,
          resourceId: workshopId,
          resourceCode: workshop.workshopCode,
          resourceName: workshop.workshopName,
          seq: index + 1
        });
      }
    });
  }

  if (form.value.workstationList && form.value.workstationList.length > 0) {
    form.value.workstationList.forEach((item, index) => {
      resourceList.push({
        resourceType: RESOURCE_TYPE.WORKSTATION,
        resourceId: item.resourceId,
        resourceCode: item.resourceCode,
        resourceName: item.resourceName,
        parentId: item.parentId,
        parentType: RESOURCE_TYPE.WORKSHOP,
        seq: index + 1
      });
    });
  }

  if (form.value.teamList && form.value.teamList.length > 0) {
    form.value.teamList.forEach((item, index) => {
      resourceList.push({
        resourceType: RESOURCE_TYPE.TEAM,
        resourceId: item.resourceId,
        resourceCode: item.resourceCode,
        resourceName: item.resourceName,
        parentId: item.parentId,
        parentType: RESOURCE_TYPE.WORKSHOP,
        seq: index + 1
      });
    });
  }

  return resourceList;
}

function cancel() {
  const obj = { path: '/erp/prdExec/work' };
  proxy.$tab.closeOpenPage(obj);
}

function openPlanSelect() {
  planSelectOpen.value = true;
  resetPlanQuery();
  getPlanList();
}

function openWorkshopSelect() {
  workshopSelectOpen.value = true;
  resetWorkshopQuery();
  getWorkshopList();
  loadWorkshopOptions();
}

function loadWorkshopOptions() {
  return listWorkshop({ pageNum: 1, pageSize: 1000 }).then((response) => {
    workshopOptions.value = response.rows || [];
  });
}

function getWorkshopList() {
  workshopLoading.value = true;
  listWorkshop(workshopQueryParams.value).then((response) => {
    workshopList.value = response.rows;
    workshopTotal.value = response.total;
    workshopLoading.value = false;
  });
}

function resetWorkshopQuery() {
  workshopQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workshopCode: null,
    workshopName: null
  };
}

function handleWorkshopSelectionChange(selection) {
  selectedWorkshops.value = selection;
}

function confirmWorkshopSelect() {
  if (selectedWorkshops.value.length === 0) {
    proxy.$modal.msgWarning('请至少选择一个车间');
    return;
  }

  if (!form.value.workshopList) {
    form.value.workshopList = [];
  }

  selectedWorkshops.value.forEach((workshop) => {
    if (!form.value.workshopList.includes(workshop.workshopId)) {
      form.value.workshopList.push(workshop.workshopId);
    }
  });

  selectedWorkshops.value.forEach((workshop) => {
    const exists = workshopOptions.value.find((w) => w.workshopId === workshop.workshopId);
    if (!exists) {
      workshopOptions.value.push(workshop);
    }
  });

  workshopSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已添加 ${selectedWorkshops.value.length} 个车间`);
}

function removeWorkstation(index) {
  form.value.workstationList.splice(index, 1);
}

function handleWorkshopRemove(workshopId) {
  if (form.value.workstationList && form.value.workstationList.length > 0) {
    form.value.workstationList = form.value.workstationList.filter((ws) => ws.parentId !== workshopId);
  }
  if (form.value.teamList && form.value.teamList.length > 0) {
    form.value.teamList = form.value.teamList.filter((team) => team.parentId !== workshopId);
  }
  if (form.value.workshopId === workshopId) {
    form.value.workshopId = null;
    form.value.workshopName = null;
  }
}

function openMainWorkshopSelect() {
  mainWorkshopSelectOpen.value = true;
  resetMainWorkshopQuery();
  getMainWorkshopList();
  loadWorkshopOptions();
}

function getMainWorkshopList() {
  mainWorkshopLoading.value = true;
  listWorkshop(mainWorkshopQueryParams.value).then((response) => {
    mainWorkshopList.value = response.rows;
    mainWorkshopTotal.value = response.total;
    mainWorkshopLoading.value = false;
  });
}

function resetMainWorkshopQuery() {
  mainWorkshopQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workshopCode: null,
    workshopName: null
  };
  selectedMainWorkshop.value = null;
}

function handleMainWorkshopSelect(row) {
  selectedMainWorkshop.value = row;
}

function confirmMainWorkshopSelect() {
  if (!selectedMainWorkshop.value) {
    proxy.$modal.msgWarning('请选择一个车间');
    return;
  }

  const workshop = selectedMainWorkshop.value;
  form.value.workshopId = workshop.workshopId;
  form.value.workshopName = workshop.workshopName;

  if (!form.value.workshopList) {
    form.value.workshopList = [];
  }
  if (!form.value.workshopList.includes(workshop.workshopId)) {
    form.value.workshopList.push(workshop.workshopId);
  }

  const exists = workshopOptions.value.find((w) => w.workshopId === workshop.workshopId);
  if (!exists) {
    workshopOptions.value.push(workshop);
  }

  mainWorkshopSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已设置主车间：${workshop.workshopName}`);
}

function openWorkstationSelect() {
  workstationSelectOpen.value = true;
  resetWorkstationQuery();
  getWorkstationList();
  loadWorkshopOptions();
}

function getWorkstationList() {
  workstationLoading.value = true;
  listWorkstation(workstationQueryParams.value).then((response) => {
    workstationList.value = response.rows;
    workstationTotal.value = response.total;
    workstationLoading.value = false;
  });
}

function resetWorkstationQuery() {
  workstationQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workstationCode: null,
    workstationName: null,
    workshopId: null
  };
}

function handleWorkstationSelectionChange(selection) {
  selectedWorkstations.value = selection;
}

function confirmWorkstationSelect() {
  if (selectedWorkstations.value.length === 0) {
    proxy.$modal.msgWarning('请至少选择一个工作站');
    return;
  }

  if (!form.value.workstationList) {
    form.value.workstationList = [];
  }

  selectedWorkstations.value.forEach((workstation) => {
    const exists = form.value.workstationList.find((w) => w.resourceId === workstation.workstationId);
    if (!exists) {
      form.value.workstationList.push({
        resourceId: workstation.workstationId,
        resourceCode: workstation.workstationCode,
        resourceName: workstation.workstationName,
        parentId: workstation.workshopId,
        parentName: workstation.workshopName
      });
    }
  });

  workstationSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已添加 ${selectedWorkstations.value.length} 个工作站`);
}

function openTeamSelect() {
  teamSelectOpen.value = true;
  resetTeamQuery();
  if (form.value.workshopList && form.value.workshopList.length === 1) {
    teamQueryParams.value.workshopId = form.value.workshopList[0];
  }
  getTeamList();
}

function getTeamList() {
  teamLoading.value = true;
  listTeam(teamQueryParams.value).then((response) => {
    teamList.value = response.rows;
    teamTotal.value = response.total;
    teamLoading.value = false;
  });
}

function resetTeamQuery() {
  teamQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    teamCode: null,
    teamName: null,
    workshopId: null
  };
  selectedTeams.value = [];
}

function handleTeamSelectionChange(selection) {
  selectedTeams.value = selection;
}

function confirmTeamSelect() {
  if (selectedTeams.value.length === 0) {
    proxy.$modal.msgWarning('请至少选择一个班组');
    return;
  }

  if (!form.value.teamList) {
    form.value.teamList = [];
  }

  selectedTeams.value.forEach((team) => {
    const exists = form.value.teamList.find((t) => t.resourceId === team.teamId);
    if (!exists) {
      form.value.teamList.push({
        resourceId: team.teamId,
        resourceCode: team.teamCode,
        resourceName: team.teamName,
        parentId: team.workshopId,
        parentName: team.workshopName
      });
    }
  });

  teamSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已添加 ${selectedTeams.value.length} 个班组`);
}

function removeTeam(index) {
  form.value.teamList.splice(index, 1);
}

function openUserSelect() {
  userSelectOpen.value = true;
  resetUserQuery();
  getUserList();
}

function getUserList() {
  userLoading.value = true;
  listUser(userQueryParams.value).then((response) => {
    userList.value = response.rows;
    userTotal.value = response.total;
    userLoading.value = false;
  });
}

function resetUserQuery() {
  userQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    userName: null,
    nickName: null
  };
  selectedUser.value = null;
}

function handleUserSelect(row) {
  selectedUser.value = row;
}

function confirmUserSelect() {
  if (!selectedUser.value) {
    proxy.$modal.msgWarning('请选择一个负责人');
    return;
  }

  const user = selectedUser.value;
  form.value.responsibleId = user.userId;
  form.value.responsibleName = user.nickName;

  userSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已选择负责人：${user.nickName}`);
}

function getPlanList() {
  planLoading.value = true;
  listPlan(planQueryParams.value).then((response) => {
    planList.value = response.rows;
    planTotal.value = response.total;
    planLoading.value = false;
  });
}

function resetPlanQuery() {
  proxy.resetForm('planQueryForm');
  planQueryParams.value.pageNum = 1;
  planQueryParams.value.isCurrent = 1;
  planQueryParams.value.status = 'CONFIRMED';
}

async function selectPlan(row) {
  form.value.planId = row.planId;
  form.value.planName = row.planName;
  form.value.sourceType = 'PLAN';
  form.value.sourceId = row.planId;
  form.value.sourceNo = row.referenceNo;
  form.value.plannedQty = row.totalQuantity;
  form.value.planStartDate = row.startDate;
  form.value.planEndDate = row.endDate;
  form.value.priority = row.priority;
  form.value.remark = row.remark;

  if (row.bomId) {
    form.value.bomId = row.bomId;
  }
  if (row.bomVersion) {
    form.value.bomVersion = row.bomVersion;
  }
  if (row.bomName) {
    form.value.bomName = row.bomName;
  }

  if (row.referenceNo) {
    try {
      const response = await getOrderLine(row.referenceNo);
      if (response.data && response.data.length > 0) {
        const orderLine = response.data[0];
        form.value.materialId = orderLine.materialId;
        form.value.materialCode = orderLine.materialCode;
        form.value.materialName = orderLine.materialName;
        form.value.materialSpec = orderLine.materialSpec;
        // 订单明细仅有单位名称 unit, 无 unitId/unitName
        form.value.unitName = orderLine.unit;
        proxy.$modal.msgSuccess('已自动填充物料信息');
      } else {
        proxy.$modal.msgWarning('未找到销售订单明细，请手动填写物料信息');
      }
    } catch (error) {
      console.error('获取销售订单明细失败:', error);
      proxy.$modal.msgWarning('获取物料信息失败，请手动填写');
    }
  } else {
    proxy.$modal.msgWarning('计划没有关联销售订单，请手动填写物料信息');
  }

  planSelectOpen.value = false;
}

/** 加载计量单位下拉选项 */
function loadUomList() {
  listMeasureAll().then((response: any) => {
    uomOptions.value = response.data || response.rows || [];
  });
}

/** 单位下拉变更：同步单位名称 */
function handleUnitChange(uomId) {
  const matched = uomOptions.value.find((u: any) => u.uomId === uomId);
  form.value.unitName = matched ? matched.uomName : null;
}

/** 打开物料选择弹窗 */
function openMaterialSelect() {
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调：带出物料及基本单位 */
function handleMaterialSelected(row: any) {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  form.value.materialSpec = row.materialSpec;
  if (row.baseUomId) {
    form.value.unitId = row.baseUomId;
    form.value.unitName = row.baseUomName;
  }
  proxy.$modal.msgSuccess(`已选择物料：${row.materialName}`);
}

/** 打开 BOM 选择弹窗 */
function openBomSelect() {
  bomSelectOpen.value = true;
  bomQueryParams.value.pageNum = 1;
  bomQueryParams.value.bomName = null;
  getBomList();
}

/** 查询 BOM 列表 */
function getBomList() {
  bomLoading.value = true;
  listBom({ ...bomQueryParams.value, isCurrent: 1, status: 1 })
    .then((response) => {
      bomList.value = response.rows;
      bomTotal.value = response.total;
      bomLoading.value = false;
    })
    .catch(() => {
      bomLoading.value = false;
    });
}

/** 选择 BOM：带出 bomId/Name/Version */
function selectBom(row: any) {
  form.value.bomId = row.bomId;
  form.value.bomName = row.bomName;
  form.value.bomVersion = row.bomVersion;
  bomSelectOpen.value = false;
  proxy.$modal.msgSuccess(`已选择BOM：${row.bomName}`);
}

function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('PRD_WORK_ORDER_CODE').then((response: any) => {
      form.value.woNo = response.data || response;
    });
  } else {
    form.value.woNo = null;
  }
}

/**
 * 将流程下发的业务数据写入本地状态
 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  form.value = merged;
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'prdWorkOrderService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

/**
 * 尝试自动选择流程（仅一个选项时自动选中）
 */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).woId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '生产工单（流程）' : '生产工单';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

/**
 * 提交审批（Flowable）
 */
function handleSubmitFlow() {
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning('请先选择审批流程');
    return;
  }
  if (form.value.woId != null) {
    updateWork(form.value).then(() => {
      submitWorkFlow(String(form.value.woId), String(form.value.selectFlowId))
        .then((res: any) => {
          if (res.code === 200) {
            proxy.$modal.msgSuccess('提交审批成功');
            router.push('/erp/prdExec/work');
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

/**
 * 查看流程
 */
function onViewFlow() {
  getWorkProcId(String(form.value.woId))
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
