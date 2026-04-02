<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="workRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <!-- 基本信息 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="工单编号" prop="woNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.woNo" placeholder="请输入工单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源计划ID" prop="planId">
              <el-input v-model="form.planId" placeholder="请输入来源计划ID" readonly>
                <template #append>
                  <el-button icon="Search" @click="openPlanSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" readonly>
                <template #append>
                  <el-button icon="Search" @click="openPlanSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option v-for="dict in prd_source_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="来源单据ID" prop="sourceId">
              <el-input v-model="form.sourceId" placeholder="请输入来源单据ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="销售订单编号" prop="sourceNo">
              <el-input v-model="form.sourceNo" placeholder="请输入销售订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源单据行ID" prop="sourceLineId">
              <el-input v-model="form.sourceLineId" placeholder="请输入来源单据行ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品物料ID" prop="materialId">
              <el-input v-model="form.materialId" placeholder="请输入产品物料ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="产品编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品编码" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input v-model="form.plannedQty" placeholder="请输入计划数量" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="已完成数量" prop="completedQty">
              <el-input v-model="form.completedQty" placeholder="请输入已完成数量" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="报废数量" prop="scrapQty">
              <el-input v-model="form.scrapQty" placeholder="请输入报废数量" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单位id" prop="unitId">
              <el-input v-model="form.unitId" placeholder="请输入单位id" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单位名称" prop="unitName">
              <el-input v-model="form.unitName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="bomName" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入bomName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="BOM版本" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="主车间" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请选择主车间" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMainWorkshopSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 资源分配 - 车间 -->
        <el-divider content-position="left">资源分配</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配车间" prop="workshopList">
              <div style="display: flex; align-items: flex-start; gap: 10px;">
                <el-select
                  v-model="form.workshopList"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="3"
                  placeholder="请选择车间"
                  style="flex: 1; min-width: 300px;"
                  :disabled="isViewMode"
                  @remove-tag="handleWorkshopRemove">
                  <el-option
                    v-for="item in workshopOptions"
                    :key="item.workshopId"
                    :label="item.workshopName"
                    :value="item.workshopId" />
                </el-select>
                <el-button type="primary" icon="Search" @click="openWorkshopSelect" :disabled="isViewMode">选择</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 资源分配 - 工作站 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配工作站">
              <el-table :data="form.workstationList" border size="small" style="width: 100%; margin-bottom: 10px;">
                <el-table-column type="index" label="顺序" width="60" align="center" />
                <el-table-column label="工作站编码" prop="resourceCode" width="120" />
                <el-table-column label="工作站名称" prop="resourceName" />
                <el-table-column label="所属车间" prop="parentName" width="150" />
                <el-table-column label="操作" width="100" align="center" v-if="!isViewMode">
                  <template #default="scope">
                    <el-button type="danger" link icon="Delete" @click="removeWorkstation(scope.$index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button type="primary" icon="Plus" @click="openWorkstationSelect" :disabled="isViewMode || !form.workshopList || form.workshopList.length === 0" v-if="!isViewMode">添加工作站</el-button>
              <span v-if="!isViewMode && (!form.workshopList || form.workshopList.length === 0)" style="color: #909399; margin-left: 10px;">请先选择车间</span>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 资源分配 - 班组 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="分配班组">
              <el-table :data="form.teamList" border size="small" style="width: 100%; margin-bottom: 10px;">
                <el-table-column type="index" label="顺序" width="60" align="center" />
                <el-table-column label="班组编码" prop="resourceCode" width="120" />
                <el-table-column label="班组名称" prop="resourceName" />
                <el-table-column label="所属车间" prop="parentName" width="150" />
                <el-table-column label="操作" width="100" align="center" v-if="!isViewMode">
                  <template #default="scope">
                    <el-button type="danger" link icon="Delete" @click="removeTeam(scope.$index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button type="primary" icon="Plus" @click="openTeamSelect" :disabled="isViewMode || !form.workshopList || form.workshopList.length === 0" v-if="!isViewMode">添加班组</el-button>
              <span v-if="!isViewMode && (!form.workshopList || form.workshopList.length === 0)" style="color: #909399; margin-left: 10px;">请先选择车间</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="负责人" prop="responsibleName">
              <el-input v-model="form.responsibleName" placeholder="请选择负责人" readonly>
                <template #append>
                  <el-button icon="Search" @click="openUserSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工单状态" prop="woStatus">
              <el-radio-group v-model="form.woStatus">
                <el-radio v-for="dict in prd_wo_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 时间信息 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker clearable v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker clearable v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划结束日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="实际开始时间" prop="actualStartDate">
              <el-date-picker clearable v-model="form.actualStartDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择实际开始时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="实际结束时间" prop="actualEndDate">
              <el-date-picker clearable v-model="form.actualEndDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择实际结束时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 长字段单独行显示 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="产品规格" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
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
        
        <!-- 预留字段 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="bom名称" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入bomName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段2" prop="attr2">
              <el-input v-model="form.attr2" placeholder="请输入预留字段2" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段3" prop="attr3">
              <el-input v-model="form.attr3" placeholder="请输入预留字段3" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段4" prop="attr4">
              <el-input v-model="form.attr4" placeholder="请输入预留字段4" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
    
    <!-- 生产计划选择对话框 -->
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
        <el-table-column label="计划开始日期" align="center" prop="startDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.startDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划结束日期" align="center" prop="endDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.endDate, '{y}-{m}-{d}') }}</span>
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

      <pagination v-show="planTotal > 0" :total="planTotal" v-model:page="planQueryParams.pageNum"
        v-model:limit="planQueryParams.pageSize" @pagination="getPlanList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="planSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 车间选择对话框 -->
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

      <pagination v-show="workshopTotal > 0" :total="workshopTotal" v-model:page="workshopQueryParams.pageNum"
        v-model:limit="workshopQueryParams.pageSize" @pagination="getWorkshopList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmWorkshopSelect">确 定</el-button>
          <el-button @click="workshopSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 主车间选择对话框 -->
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

      <pagination v-show="mainWorkshopTotal > 0" :total="mainWorkshopTotal" v-model:page="mainWorkshopQueryParams.pageNum"
        v-model:limit="mainWorkshopQueryParams.pageSize" @pagination="getMainWorkshopList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmMainWorkshopSelect">确 定</el-button>
          <el-button @click="mainWorkshopSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 工作站选择对话框 -->
    <el-dialog title="选择工作站" v-model="workstationSelectOpen" width="800px" append-to-body>
      <el-alert
        v-if="form.workshopList && form.workshopList.length > 0"
        :title="`已选车间：${getSelectedWorkshopNames()}`"
        type="info"
        :closable="false"
        style="margin-bottom: 15px;"
      />
      <el-form :model="workstationQueryParams" :inline="true">
        <el-form-item label="所属车间" prop="workshopId">
          <el-select v-model="workstationQueryParams.workshopId" placeholder="请选择车间" clearable style="width: 200px;">
            <el-option
              v-for="item in selectedWorkshopOptions"
              :key="item.workshopId"
              :label="item.workshopName"
              :value="item.workshopId" />
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

      <pagination v-show="workstationTotal > 0" :total="workstationTotal" v-model:page="workstationQueryParams.pageNum"
        v-model:limit="workstationQueryParams.pageSize" @pagination="getWorkstationList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmWorkstationSelect">确 定</el-button>
          <el-button @click="workstationSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 班组选择对话框 -->
    <el-dialog title="选择班组" v-model="teamSelectOpen" width="800px" append-to-body>
      <el-form :model="teamQueryParams" :inline="true">
        <el-form-item label="班组编码" prop="teamCode">
          <el-input v-model="teamQueryParams.teamCode" placeholder="请输入班组编码" clearable @keyup.enter="getTeamList" />
        </el-form-item>
        <el-form-item label="班组名称" prop="teamName">
          <el-input v-model="teamQueryParams.teamName" placeholder="请输入班组名称" clearable @keyup.enter="getTeamList" />
        </el-form-item>
        <el-form-item label="所属车间" prop="workshopId">
          <el-select v-model="teamQueryParams.workshopId" placeholder="请选择车间" clearable style="width: 150px;">
            <el-option
              v-for="item in selectedWorkshopOptions"
              :key="item.workshopId"
              :label="item.workshopName"
              :value="item.workshopId" />
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

      <pagination v-show="teamTotal > 0" :total="teamTotal" v-model:page="teamQueryParams.pageNum"
        v-model:limit="teamQueryParams.pageSize" @pagination="getTeamList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmTeamSelect">确 定</el-button>
          <el-button @click="teamSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 负责人选择对话框 - 硬编码：只查询岗位为车间主任(post_code='2003')的人员 -->
    <el-dialog title="选择负责人" v-model="userSelectOpen" width="800px" append-to-body>
      <el-form :model="userQueryParams" :inline="true">
        <el-form-item label="用户编码" prop="userCode">
          <el-input v-model="userQueryParams.userCode" placeholder="请输入用户编码" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getUserList">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" highlight-current-row @current-change="handleUserSelect">
        <el-table-column label="用户编码" align="center" prop="userCode" />
        <el-table-column label="用户名称" align="center" prop="userName" />
        <el-table-column label="岗位" align="center" prop="postName" />
        <el-table-column label="部门" align="center" prop="deptName" />
      </el-table>

      <pagination v-show="userTotal > 0" :total="userTotal" v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize" @pagination="getUserList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmUserSelect">确 定</el-button>
          <el-button @click="userSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WorkForm">
import { getWork, addWork, updateWork } from "@/api/core/prd/work"
import { listPlan } from "@/api/core/prd/plan"
import { genCode } from "@/api/system/autocode/rule"
import { getOrderLine } from "@/api/core/so/orderheader"
import { listWorkshop } from "@/api/core/bd/workshop"
import { listWorkstation } from "@/api/core/bd/workstation"
import { listTeam } from "@/api/core/sch/team"
import { listUserByPostId } from "@/api/system/user"
import { getWorkOrderResourceByWoId, saveWorkOrderResourceBatch, RESOURCE_TYPE } from "@/api/core/prd/workOrderResource"

const { proxy } = getCurrentInstance()
const { prd_source_type, so_order_priority, prd_wo_status, prd_plan_type, prd_plan_status } = proxy.useDict('prd_source_type', 'so_order_priority', 'prd_wo_status', 'prd_plan_type', 'prd_plan_status')
const route = useRoute()
const router = useRouter()

const form = ref({})

// 计算属性：已选车间的选项列表
const selectedWorkshopOptions = computed(() => {
  if (!form.value.workshopList || !workshopOptions.value) {
    return []
  }
  return workshopOptions.value.filter(item => form.value.workshopList.includes(item.workshopId))
})

// 计算属性：过滤后的工作站列表（只显示已选车间的工作站）
const filteredWorkstationList = computed(() => {
  if (!workstationList.value || !form.value.workshopList) {
    return []
  }
  // 只显示已选车间的工作站
  return workstationList.value.filter(item => form.value.workshopList.includes(item.workshopId))
})
const isViewMode = ref(false)
const title = ref("")
const autoGenFlag = ref(false)

// 添加表单验证规则
const rules = ref({
  woNo: [{ required: true, message: "工单编号不能为空", trigger: "blur" }],
  materialName: [{ required: true, message: "产品名称不能为空", trigger: "blur" }],
  plannedQty: [{ required: true, message: "计划数量不能为空", trigger: "blur" }]
})

// 生产计划选择相关变量
const planSelectOpen = ref(false)
const planList = ref([])
const planLoading = ref(false)
const planTotal = ref(0)
const planQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  planNo: null,
  planName: null,
  isCurrent: 1,      // 只显示当前版本
  status: 'CONFIRMED' // 只显示已确认状态
})

// 车间选择相关变量
const workshopSelectOpen = ref(false)
const workshopList = ref([])
const workshopLoading = ref(false)
const workshopTotal = ref(0)
const workshopQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workshopCode: null,
  workshopName: null
})
const workshopOptions = ref([])
const selectedWorkshops = ref([])

// 主车间选择相关变量
const mainWorkshopSelectOpen = ref(false)
const mainWorkshopList = ref([])
const mainWorkshopLoading = ref(false)
const mainWorkshopTotal = ref(0)
const mainWorkshopQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workshopCode: null,
  workshopName: null
})
const selectedMainWorkshop = ref(null)

// 工作站选择相关变量
const workstationSelectOpen = ref(false)
const workstationList = ref([])
const workstationLoading = ref(false)
const workstationTotal = ref(0)
const workstationQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workstationCode: null,
  workstationName: null,
  workshopId: null
})
const selectedWorkstations = ref([])

// 班组选择相关变量
const teamSelectOpen = ref(false)
const teamList = ref([])
const teamLoading = ref(false)
const teamTotal = ref(0)
const teamQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  teamCode: null,
  teamName: null,
  workshopId: null
})
const selectedTeams = ref([])

// 负责人选择相关变量 - 硬编码：只查询岗位为车间主任(post_code='2003')的人员
const userSelectOpen = ref(false)
const userList = ref([])
const userLoading = ref(false)
const userTotal = ref(0)
const userQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  userCode: null,
  userName: null
})
const selectedUser = ref(null)

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  // 设置查看模式
  isViewMode.value = action === 'view'
  console.log('id:', id);

  if (id) {
    initPage(id)
  }
})

function initPage(id) {
  const action = route.query.action

  if (action === 'view') {
    // 查看模式
    isViewMode.value = true
    title.value = "查看生产工单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改生产工单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增生产工单"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getWork(id).then(response => {
    form.value = response.data
    // 确保资源列表已初始化
    if (!form.value.workshopList) {
      form.value.workshopList = []
    }
    if (!form.value.workstationList) {
      form.value.workstationList = []
    }
    if (!form.value.teamList) {
      form.value.teamList = []
    }
    // 先加载车间选项，再加载资源分配数据
    loadWorkshopOptions().then(() => {
      // 加载资源分配数据
      loadWorkOrderResources(id)
    })
  })
}

// 加载工单资源分配数据
function loadWorkOrderResources(woId) {
  getWorkOrderResourceByWoId(woId).then(response => {
    const resources = response.data || []

    // 初始化资源列表
    form.value.workshopList = []
    form.value.workstationList = []
    form.value.teamList = []

    // 按资源类型分组
    resources.forEach(resource => {
      if (resource.resourceType === RESOURCE_TYPE.WORKSHOP) {
        form.value.workshopList.push(resource.resourceId)
      } else if (resource.resourceType === RESOURCE_TYPE.WORKSTATION) {
        form.value.workstationList.push({
          resourceId: resource.resourceId,
          resourceCode: resource.resourceCode,
          resourceName: resource.resourceName,
          parentId: resource.parentId,
          parentName: getWorkshopNameById(resource.parentId)
        })
      } else if (resource.resourceType === RESOURCE_TYPE.TEAM) {
        form.value.teamList.push({
          resourceId: resource.resourceId,
          resourceCode: resource.resourceCode,
          resourceName: resource.resourceName,
          parentId: resource.parentId,
          parentName: getWorkshopNameById(resource.parentId)
        })
      }
    })

    // 按顺序排序
    form.value.workstationList.sort((a, b) => a.seq - b.seq)
    form.value.teamList.sort((a, b) => a.seq - b.seq)
  })
}

// 根据车间ID获取车间名称
function getWorkshopNameById(workshopId) {
  const workshop = workshopOptions.value.find(w => w.workshopId === workshopId)
  return workshop ? workshop.workshopName : ''
}

// 获取已选车间名称列表
function getSelectedWorkshopNames() {
  if (!form.value.workshopList || !workshopOptions.value) {
    return ''
  }
  const names = form.value.workshopList.map(id => {
    const workshop = workshopOptions.value.find(w => w.workshopId === id)
    return workshop ? workshop.workshopName : ''
  })
  return names.join('、')
}

// 表单重置
function reset() {
  autoGenFlag.value = false
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
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("workRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["workRef"].validate(valid => {
    if (valid) {
      // 构建资源分配列表
      const resourceList = buildResourceList()
      
      if (form.value.woId != null) {
        updateWork(form.value).then(response => {
          // 保存资源分配
          saveWorkOrderResourceBatch(form.value.woId, resourceList).then(() => {
            proxy.$modal.msgSuccess("修改成功")
            router.push('/core/prd/prdwork')
          })
        })
      } else {
        addWork(form.value).then(response => {
          // 获取新增的工单ID，兼容不同的返回结构
          const woId = response.data?.woId || response.data?.data?.woId || response.data
          if (!woId) {
            proxy.$modal.msgError("获取工单ID失败")
            return
          }
          // 保存资源分配
          saveWorkOrderResourceBatch(woId, resourceList).then(() => {
            proxy.$modal.msgSuccess("新增成功")
            router.push('/core/prd/prdwork')
          }).catch(error => {
            console.error('保存资源分配失败:', error)
            proxy.$modal.msgWarning("工单已保存，但资源分配保存失败，请编辑后重新保存")
            router.push('/core/prd/prdwork')
          })
        }).catch(error => {
          console.error('保存工单失败:', error)
          proxy.$modal.msgError("保存失败")
        })
      }
    }
  })
}

// 构建资源分配列表
function buildResourceList() {
  const resourceList = []
  
  // 添加车间资源
  if (form.value.workshopList && form.value.workshopList.length > 0) {
    form.value.workshopList.forEach((workshopId, index) => {
      const workshop = workshopOptions.value.find(w => w.workshopId === workshopId)
      if (workshop) {
        resourceList.push({
          resourceType: RESOURCE_TYPE.WORKSHOP,
          resourceId: workshopId,
          resourceCode: workshop.workshopCode,
          resourceName: workshop.workshopName,
          seq: index + 1
        })
      }
    })
  }
  
  // 添加工作站资源
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
      })
    })
  }

  // 添加班组资源
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
      })
    })
  }

  return resourceList
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/prd/prdwork' }
  proxy.$tab.closeOpenPage(obj)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

// 打开生产计划选择对话框
function openPlanSelect() {
  planSelectOpen.value = true
  resetPlanQuery()
  getPlanList()
}

// 打开车间选择对话框
function openWorkshopSelect() {
  workshopSelectOpen.value = true
  resetWorkshopQuery()
  getWorkshopList()
  // 加载车间选项
  loadWorkshopOptions()
}

// 加载车间选项
function loadWorkshopOptions() {
  return listWorkshop({ pageNum: 1, pageSize: 1000 }).then(response => {
    workshopOptions.value = response.rows || []
  })
}

// 查询车间列表
function getWorkshopList() {
  workshopLoading.value = true
  listWorkshop(workshopQueryParams.value).then(response => {
    workshopList.value = response.rows
    workshopTotal.value = response.total
    workshopLoading.value = false
  })
}

// 重置车间查询条件
function resetWorkshopQuery() {
  workshopQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workshopCode: null,
    workshopName: null
  }
}

// 车间选择变化
function handleWorkshopSelectionChange(selection) {
  selectedWorkshops.value = selection
}

// 确认车间选择
function confirmWorkshopSelect() {
  if (selectedWorkshops.value.length === 0) {
    proxy.$modal.msgWarning("请至少选择一个车间")
    return
  }

  // 确保workshopList已初始化
  if (!form.value.workshopList) {
    form.value.workshopList = []
  }

  // 将选中的车间添加到表单
  selectedWorkshops.value.forEach(workshop => {
    if (!form.value.workshopList.includes(workshop.workshopId)) {
      form.value.workshopList.push(workshop.workshopId)
    }
  })
  
  // 更新车间选项
  selectedWorkshops.value.forEach(workshop => {
    const exists = workshopOptions.value.find(w => w.workshopId === workshop.workshopId)
    if (!exists) {
      workshopOptions.value.push(workshop)
    }
  })
  
  workshopSelectOpen.value = false
  proxy.$modal.msgSuccess(`已添加 ${selectedWorkshops.value.length} 个车间`)
}

// 删除工作站
function removeWorkstation(index) {
  form.value.workstationList.splice(index, 1)
}

// 处理车间删除事件
function handleWorkshopRemove(workshopId) {
  // 删除该车间下所有工作站
  if (form.value.workstationList && form.value.workstationList.length > 0) {
    form.value.workstationList = form.value.workstationList.filter(
      ws => ws.parentId !== workshopId
    )
  }
  // 删除该车间下所有班组
  if (form.value.teamList && form.value.teamList.length > 0) {
    form.value.teamList = form.value.teamList.filter(
      team => team.parentId !== workshopId
    )
  }
  // 如果删除的是主车间，清空主车间
  if (form.value.workshopId === workshopId) {
    form.value.workshopId = null
    form.value.workshopName = null
  }
}

// 打开主车间选择对话框
function openMainWorkshopSelect() {
  mainWorkshopSelectOpen.value = true
  resetMainWorkshopQuery()
  getMainWorkshopList()
  // 加载车间选项
  loadWorkshopOptions()
}

// 查询主车间列表
function getMainWorkshopList() {
  mainWorkshopLoading.value = true
  listWorkshop(mainWorkshopQueryParams.value).then(response => {
    mainWorkshopList.value = response.rows
    mainWorkshopTotal.value = response.total
    mainWorkshopLoading.value = false
  })
}

// 重置主车间查询条件
function resetMainWorkshopQuery() {
  mainWorkshopQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workshopCode: null,
    workshopName: null
  }
  selectedMainWorkshop.value = null
}

// 主车间选择变化
function handleMainWorkshopSelect(row) {
  selectedMainWorkshop.value = row
}

// 确认主车间选择
function confirmMainWorkshopSelect() {
  if (!selectedMainWorkshop.value) {
    proxy.$modal.msgWarning("请选择一个车间")
    return
  }

  const workshop = selectedMainWorkshop.value

  // 设置主车间
  form.value.workshopId = workshop.workshopId
  form.value.workshopName = workshop.workshopName

  // 自动添加到分配车间列表
  if (!form.value.workshopList) {
    form.value.workshopList = []
  }
  if (!form.value.workshopList.includes(workshop.workshopId)) {
    form.value.workshopList.push(workshop.workshopId)
  }

  // 更新车间选项
  const exists = workshopOptions.value.find(w => w.workshopId === workshop.workshopId)
  if (!exists) {
    workshopOptions.value.push(workshop)
  }

  mainWorkshopSelectOpen.value = false
  proxy.$modal.msgSuccess(`已设置主车间：${workshop.workshopName}`)
}

// 打开工作站选择对话框
function openWorkstationSelect() {
  workstationSelectOpen.value = true
  resetWorkstationQuery()
  getWorkstationList()
  // 加载车间选项
  loadWorkshopOptions()
}

// 查询工作站列表
function getWorkstationList() {
  workstationLoading.value = true
  listWorkstation(workstationQueryParams.value).then(response => {
    workstationList.value = response.rows
    workstationTotal.value = response.total
    workstationLoading.value = false
  })
}

// 重置工作站查询条件
function resetWorkstationQuery() {
  workstationQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    workstationCode: null,
    workstationName: null,
    workshopId: null
  }
}

// 工作站选择变化
function handleWorkstationSelectionChange(selection) {
  selectedWorkstations.value = selection
}

// 确认工作站选择
function confirmWorkstationSelect() {
  if (selectedWorkstations.value.length === 0) {
    proxy.$modal.msgWarning("请至少选择一个工作站")
    return
  }

  // 确保workstationList已初始化
  if (!form.value.workstationList) {
    form.value.workstationList = []
  }

  // 将选中的工作站添加到表单
  selectedWorkstations.value.forEach(workstation => {
    const exists = form.value.workstationList.find(w => w.resourceId === workstation.workstationId)
    if (!exists) {
      form.value.workstationList.push({
        resourceId: workstation.workstationId,
        resourceCode: workstation.workstationCode,
        resourceName: workstation.workstationName,
        parentId: workstation.workshopId,
        parentName: workstation.workshopName
      })
    }
  })

  workstationSelectOpen.value = false
  proxy.$modal.msgSuccess(`已添加 ${selectedWorkstations.value.length} 个工作站`)
}

// 打开班组选择对话框
function openTeamSelect() {
  teamSelectOpen.value = true
  resetTeamQuery()
  // 如果有多个车间，默认不设置workshopId过滤，让用户在对话框中选择
  // 如果只有一个车间，自动设置workshopId
  if (form.value.workshopList && form.value.workshopList.length === 1) {
    teamQueryParams.value.workshopId = form.value.workshopList[0]
  }
  getTeamList()
}

// 查询班组列表
function getTeamList() {
  teamLoading.value = true
  listTeam(teamQueryParams.value).then(response => {
    teamList.value = response.rows
    teamTotal.value = response.total
    teamLoading.value = false
  })
}

// 重置班组查询条件
function resetTeamQuery() {
  teamQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    teamCode: null,
    teamName: null,
    workshopId: null
  }
  selectedTeams.value = []
}

// 班组选择变化
function handleTeamSelectionChange(selection) {
  selectedTeams.value = selection
}

// 确认班组选择
function confirmTeamSelect() {
  if (selectedTeams.value.length === 0) {
    proxy.$modal.msgWarning("请至少选择一个班组")
    return
  }

  // 确保teamList已初始化
  if (!form.value.teamList) {
    form.value.teamList = []
  }

  // 将选中的班组添加到表单
  selectedTeams.value.forEach(team => {
    const exists = form.value.teamList.find(t => t.resourceId === team.teamId)
    if (!exists) {
      form.value.teamList.push({
        resourceId: team.teamId,
        resourceCode: team.teamCode,
        resourceName: team.teamName,
        parentId: team.workshopId,
        parentName: team.workshopName
      })
    }
  })

  teamSelectOpen.value = false
  proxy.$modal.msgSuccess(`已添加 ${selectedTeams.value.length} 个班组`)
}

// 删除班组
function removeTeam(index) {
  form.value.teamList.splice(index, 1)
}

// 打开负责人选择对话框 - 硬编码：只查询岗位为车间主任(post_code='2003')的人员
function openUserSelect() {
  userSelectOpen.value = true
  resetUserQuery()
  getUserList()
}

// 查询用户列表 - 硬编码：只查询岗位为车间主任的人员(post_id = 2003)
function getUserList() {
  userLoading.value = true
  // 硬编码：postId = 2003 表示车间主任岗位
  const postId = 16
  listUserByPostId(postId, userQueryParams.value).then(response => {
    userList.value = response.rows
    userTotal.value = response.total
    userLoading.value = false
  })
}

// 重置用户查询条件
function resetUserQuery() {
  userQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    userCode: null,
    userName: null
  }
  selectedUser.value = null
}

// 用户选择变化（单选）
function handleUserSelect(row) {
  selectedUser.value = row
}

// 确认用户选择
function confirmUserSelect() {
  if (!selectedUser.value) {
    proxy.$modal.msgWarning("请选择一个负责人")
    return
  }

  const user = selectedUser.value
  // 赋值负责人ID和姓名
  form.value.responsibleId = user.userId
  form.value.responsibleName = user.userName

  userSelectOpen.value = false
  proxy.$modal.msgSuccess(`已选择负责人：${user.userName}`)
}

// 查询生产计划列表
function getPlanList() {
  planLoading.value = true
  listPlan(planQueryParams.value).then(response => {
    planList.value = response.rows
    planTotal.value = response.total
    planLoading.value = false
  })
}

// 重置生产计划查询条件
function resetPlanQuery() {
  proxy.resetForm("planQueryForm")
  planQueryParams.value.pageNum = 1
  planQueryParams.value.isCurrent = 1
  planQueryParams.value.status = 'CONFIRMED'
}

// 选择生产计划
async function selectPlan(row) {
  // 计划基本信息
  form.value.planId = row.planId
  form.value.planName = row.planName

  // 来源信息
  form.value.sourceType = 'PLAN' // 设置来源类型为计划
  form.value.sourceId = row.planId // 来源单据ID（使用计划ID）
  form.value.sourceNo = row.referenceNo // 销售订单编号/参考单据号

  // 数量信息
  form.value.plannedQty = row.totalQuantity // 计划总数量

  // 日期信息
  form.value.planStartDate = row.startDate // 计划开始日期
  form.value.planEndDate = row.endDate // 计划结束日期

  // 其他信息
  form.value.priority = row.priority // 优先级
  form.value.remark = row.remark // 备注

  // BOM信息（如果计划中有）
  if (row.bomId) {
    form.value.bomId = row.bomId
  }
  if (row.bomVersion) {
    form.value.bomVersion = row.bomVersion
  }
  if (row.bomName) {
    form.value.bomName = row.bomName
  }

  // 根据销售订单编号查询物料信息
  if (row.referenceNo) {
    try {
      const response = await getOrderLine(row.referenceNo)
      if (response.data && response.data.length > 0) {
        // 取第一条订单行数据
        const orderLine = response.data[0]
        form.value.materialId = orderLine.materialId
        form.value.materialCode = orderLine.materialCode
        form.value.materialName = orderLine.materialName
        form.value.materialSpec = orderLine.materialSpec
        form.value.unitId = orderLine.unitId
        form.value.unitName = orderLine.unitName
        proxy.$modal.msgSuccess("已自动填充物料信息")
      } else {
        proxy.$modal.msgWarning("未找到销售订单明细，请手动填写物料信息")
      }
    } catch (error) {
      console.error('获取销售订单明细失败:', error)
      proxy.$modal.msgWarning("获取物料信息失败，请手动填写")
    }
  } else {
    proxy.$modal.msgWarning("计划没有关联销售订单，请手动填写物料信息")
  }

  planSelectOpen.value = false
}

// 自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('PRD_WORK_ORDER_CODE').then(response => {
      form.value.woNo = response;
    });
  } else {
    form.value.woNo = null;
  }
}

// 解析时间格式
function parseTime(time, pattern) {
  if (arguments.length === 0 || !time) {
    return null
  }
  const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
  let date
  if (typeof time === 'object') {
    date = time
  } else {
    if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
      time = parseInt(time)
    } else if (typeof time === 'string') {
      time = time.replace(new RegExp(/-/gm), '/').replace('T', ' ').replace(new RegExp(/\.[\d]{3}/gm), '')
    }
    if ((typeof time === 'number') && (time.toString().length === 10)) {
      time = time * 1000
    }
    date = new Date(time)
  }
  const formatObj = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  const time_str = format.replace(/{(y|m|d|h|i|s|a)+}/g, (result, key) => {
    let value = formatObj[key]
    // Note: getDay() returns 0 on Sunday
    if (key === 'a') { return ['日', '一', '二', '三', '四', '五', '六'][value] }
    if (result.length > 0 && value < 10) {
      value = '0' + value
    }
    return value || 0
  })
  return time_str
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>