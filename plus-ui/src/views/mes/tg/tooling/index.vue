<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工装编码" prop="toolingCode">
            <el-input v-model="queryParams.toolingCode" placeholder="请输入工装编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工装名称" prop="toolingName">
            <el-input v-model="queryParams.toolingName" placeholder="请输入工装名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="类型" prop="toolingType">
            <el-select v-model="queryParams.toolingType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in mes_tooling_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_tooling_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:tg:tooling:add']">新增工装</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Bell" @click="handleDue()" v-hasPermi="['mes:tg:tooling:list']">保养到期</el-button>
      </el-col>
      <!-- MES-TG-05：校准到期/超期预警（服务端窗口：今天 ~ 今天+7 天，含已超期） -->
      <el-col :span="1.5">
        <el-button type="danger" plain icon="AlarmClock" @click="handleCalibrationDue()" v-hasPermi="['mes:tg:tooling:list']">校准到期</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:tg:tooling:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip :row-class-name="rowClassName" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工装名称" prop="toolingName" min-width="130" />
      <el-table-column label="类型" prop="toolingType" align="center" width="90">
        <template #default="scope"><dict-tag :options="mes_tooling_type" :value="scope.row.toolingType" /></template>
      </el-table-column>
      <el-table-column label="车间" prop="workshopName" width="110" />
      <el-table-column label="寿命(次)" align="right" width="120">
        <template #default="scope">
          <span :class="lifeWarn(scope.row) ? 'text-danger' : ''"> {{ scope.row.usedCount || 0 }} / {{ scope.row.totalLifeCount ?? '-' }} </span>
        </template>
      </el-table-column>
      <!-- MES-TG-01/TG-05：校准字段（后端 TgToolingVo.calibrationCycleDays / lastCalibrationDate /
           nextCalibrationDate / calibrationResult / calibrationOverdue），超期行由 rowClassName 高亮 -->
      <el-table-column label="校准周期(天)" prop="calibrationCycleDays" align="right" width="110">
        <template #default="scope">{{ scope.row.calibrationCycleDays ?? '未纳入' }}</template>
      </el-table-column>
      <el-table-column label="上次校准日" align="center" width="110">
        <template #default="scope">{{ fmtDate(scope.row.lastCalibrationDate) }}</template>
      </el-table-column>
      <el-table-column label="下次校准日" align="center" width="110">
        <template #default="scope">
          <span :class="scope.row.calibrationOverdue ? 'text-danger' : ''">{{ fmtDate(scope.row.nextCalibrationDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="校准结果" align="center" width="100">
        <template #default="scope">
          <dict-tag v-if="scope.row.calibrationResult" :options="mes_tooling_calibration_result" :value="scope.row.calibrationResult" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" width="90">
        <template #default="scope"><dict-tag :options="mes_tooling_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="400" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'IDLE'"
            v-hasPermi="['mes:tg:tooling:edit']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            icon="Promotion"
            @click="handleCheckout(scope.row)"
            v-if="scope.row.status === 'IDLE'"
            v-hasPermi="['mes:tg:tooling:checkout']"
            >领用</el-button
          >
          <el-button
            link
            type="warning"
            icon="Back"
            @click="handleReturn(scope.row)"
            v-if="scope.row.status === 'IN_USE'"
            v-hasPermi="['mes:tg:tooling:return']"
            >归还</el-button
          >
          <el-button
            link
            type="warning"
            icon="Tools"
            @click="handleStartMaintain(scope.row)"
            v-if="scope.row.status === 'IDLE'"
            v-hasPermi="['mes:tg:tooling:maintain']"
            >开始保养</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleCompleteMaintain(scope.row)"
            v-if="scope.row.status === 'MAINTENANCE'"
            v-hasPermi="['mes:tg:tooling:maintain']"
            >完成保养</el-button
          >
          <!-- MES-TG-01：登记校准（服务端 CAS IDLE -> CALIBRATING -> IDLE，并算下次校准日） -->
          <el-button
            link
            type="primary"
            icon="AlarmClock"
            @click="handleCalibrate(scope.row)"
            v-if="scope.row.status === 'IDLE'"
            v-hasPermi="['mes:tg:tooling:calibrate']"
            >登记校准</el-button
          >
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleScrap(scope.row)"
            v-if="['IDLE', 'MAINTENANCE'].includes(scope.row.status)"
            v-hasPermi="['mes:tg:tooling:scrap']"
            >报废</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改工装对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="toolingRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工装编码" prop="toolingCode">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%">
                <el-input v-model="form.toolingCode" :disabled="autoGenFlag" placeholder="留空自动生成" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-text="生成" @change="handleAutoGenChange" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工装名称" prop="toolingName"><el-input v-model="form.toolingName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工装类型" prop="toolingType">
              <el-select v-model="form.toolingType" placeholder="请选择类型" style="width: 100%">
                <el-option v-for="dict in mes_tooling_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属车间">
              <el-input v-model="form.workshopName" placeholder="请选择车间" readonly>
                <template #append><el-button icon="Search" @click="openWorkshopDialog()" /></template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="总寿命(次)">
              <el-input-number v-model="form.totalLifeCount" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总寿命(小时)">
              <el-input-number v-model="form.totalLifeHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采购成本">
              <el-input-number v-model="form.purchaseCost" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- MES-TG-01：把工装纳入校准管理（校准周期决定下次校准日，为空表示不纳入） -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="校准周期(天)">
              <el-input-number v-model="form.calibrationCycleDays" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次校准日">
              <el-date-picker v-model="form.nextCalibrationDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm()">确 定</el-button>
        <el-button @click="cancel()">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 领用对话框 -->
    <el-dialog title="工装领用" v-model="checkoutDialog.visible" width="500px" append-to-body>
      <el-form :model="checkoutForm" label-width="90px">
        <el-form-item label="操作员" required>
          <el-input v-model="checkoutForm.operatorName" placeholder="请选择操作员" readonly>
            <template #append><el-button icon="Search" @click="openUserDialog('operator')" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="工单">
          <el-input v-model="checkoutForm.woNo" placeholder="可选,选择工单" readonly>
            <template #append><el-button icon="Search" @click="openWoDialog()" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="工位">
          <el-input v-model="checkoutForm.workstationName" placeholder="可选,选择工位" readonly>
            <template #append><el-button icon="Search" @click="openWorkstationDialog()" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="checkoutForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitCheckout()">确认领用</el-button>
        <el-button @click="checkoutDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 归还对话框 -->
    <el-dialog title="工装归还" v-model="returnDialog.visible" width="500px" append-to-body>
      <el-form :model="returnForm" label-width="90px">
        <el-form-item label="使用次数" required>
          <el-input-number v-model="returnForm.usageCount" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="使用时长">
          <el-input-number v-model="returnForm.usageHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="returnForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitReturn()">确认归还</el-button>
        <el-button @click="returnDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 完成保养对话框 -->
    <el-dialog title="完成保养(归零)" v-model="maintainDialog.visible" width="500px" append-to-body>
      <el-form :model="maintainForm" label-width="90px">
        <el-form-item label="保养类型">
          <el-select v-model="maintainForm.maintainType" placeholder="请选择" style="width: 100%">
            <el-option v-for="dict in tg_maintenance_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养人">
          <el-input v-model="maintainForm.maintainerName" placeholder="请选择保养人" readonly>
            <template #append><el-button icon="Search" @click="openUserDialog('maintainer')" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="保养内容"><el-input v-model="maintainForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保养成本">
              <el-input-number v-model="maintainForm.cost" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="maintainForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitCompleteMaintain()">确认完成</el-button>
        <el-button @click="maintainDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 登记校准(MES-TG-01): IDLE -> CALIBRATING -> IDLE, 下次校准日由服务端按 校准日期+周期 计算 -->
    <el-dialog title="登记校准" v-model="calibrationDialog.visible" width="520px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-2"
        title="下次校准日由服务端按「校准日期 + 校准周期」计算，不接受手工指定"
        description="校准结果为「不合格」时，服务端把下次校准日置为本次日期，工装立即进入校准超期（不可领用/进站），复校通过前不得使用。"
      />
      <el-form :model="calibrationForm" label-width="110px">
        <el-form-item label="工装">
          <el-input :model-value="calibrationToolingLabel" disabled />
        </el-form-item>
        <el-form-item label="校准日期">
          <el-date-picker
            v-model="calibrationForm.calibrationDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="留空取当前日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="校准周期(天)">
          <el-input-number v-model="calibrationForm.calibrationCycleDays" :min="0" controls-position="right" style="width: 100%" />
          <div class="text-gray-400 text-xs mt-1">留空沿用台账上的校准周期；为 0 或留空表示不纳入校准管理</div>
        </el-form-item>
        <el-form-item label="校准结果">
          <el-select v-model="calibrationForm.calibrationResult" style="width: 100%">
            <el-option v-for="d in mes_tooling_calibration_result" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="校准备注">
          <el-input v-model="calibrationForm.calibrationRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="calibrationSubmitting" @click="submitCalibration()">确认登记</el-button>
        <el-button @click="calibrationDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 校准到期/超期预警(MES-TG-05): 今天 ~ 今天+7 天, 含已超期 -->
    <el-dialog title="校准到期预警" v-model="calibrationDueDialog.visible" width="880px" append-to-body>
      <el-table :data="calibrationDueDialog.list" show-overflow-tooltip max-height="420">
        <el-table-column label="工装编码" prop="toolingCode" min-width="140" />
        <el-table-column label="工装名称" prop="toolingName" min-width="130" />
        <el-table-column label="校准周期(天)" prop="calibrationCycleDays" align="right" width="110" />
        <el-table-column label="上次校准日" align="center" width="110">
          <template #default="scope">{{ fmtDate(scope.row.lastCalibrationDate) }}</template>
        </el-table-column>
        <el-table-column label="下次校准日" align="center" width="110">
          <template #default="scope">
            <span :class="scope.row.calibrationOverdue ? 'text-danger' : ''">{{ fmtDate(scope.row.nextCalibrationDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="校准状态" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.calibrationOverdue" type="danger" size="small">已超期</el-tag>
            <el-tag v-else type="warning" size="small">即将到期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_status" :value="scope.row.status" /></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="calibrationDueDialog.list.length === 0" description="近 7 天内无校准到期工装" />
    </el-dialog>

    <!-- 车间选择对话框 -->
    <el-dialog v-model="workshopDialog.visible" title="选择车间" width="800px" append-to-body>
      <el-form :model="workshopDialog.query" :inline="true">
        <el-form-item label="车间编码"
          ><el-input v-model="workshopDialog.query.workshopCode" clearable @keyup.enter="queryWorkshops()"
        /></el-form-item>
        <el-form-item label="车间名称"
          ><el-input v-model="workshopDialog.query.workshopName" clearable @keyup.enter="queryWorkshops()"
        /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWorkshops()">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="workshopDialog.list" height="380" @row-dblclick="selectWorkshop">
        <el-table-column label="车间编码" prop="workshopCode" width="160" />
        <el-table-column label="车间名称" prop="workshopName" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWorkshop(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="workshopDialog.total > 0"
        :total="workshopDialog.total"
        v-model:page="workshopDialog.query.pageNum"
        v-model:limit="workshopDialog.query.pageSize"
        @pagination="queryWorkshops"
      />
    </el-dialog>

    <!-- 工位选择对话框 -->
    <el-dialog v-model="workstationDialog.visible" title="选择工位" width="800px" append-to-body>
      <el-form :model="workstationDialog.query" :inline="true">
        <el-form-item label="工位编码"
          ><el-input v-model="workstationDialog.query.workstationCode" clearable @keyup.enter="queryWorkstations()"
        /></el-form-item>
        <el-form-item label="工位名称"
          ><el-input v-model="workstationDialog.query.workstationName" clearable @keyup.enter="queryWorkstations()"
        /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWorkstations()">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="workstationDialog.list" height="380" @row-dblclick="selectWorkstation">
        <el-table-column label="工位编码" prop="workstationCode" width="160" />
        <el-table-column label="工位名称" prop="workstationName" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWorkstation(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="workstationDialog.total > 0"
        :total="workstationDialog.total"
        v-model:page="workstationDialog.query.pageNum"
        v-model:limit="workstationDialog.query.pageSize"
        @pagination="queryWorkstations"
      />
    </el-dialog>

    <!-- 用户选择对话框(操作员/保养人复用) -->
    <el-dialog v-model="userDialog.visible" title="选择人员" width="640px" append-to-body>
      <el-form :model="userDialog.query" :inline="true">
        <el-form-item label="账号"><el-input v-model="userDialog.query.userName" clearable @keyup.enter="queryUsers()" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="userDialog.query.nickName" clearable @keyup.enter="queryUsers()" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryUsers()">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="userDialog.list" height="380" @row-dblclick="selectUser">
        <el-table-column label="账号" prop="userName" width="140" />
        <el-table-column label="昵称" prop="nickName" min-width="140" />
        <el-table-column label="部门" prop="deptName" min-width="140" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectUser(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.query.pageNum"
        v-model:limit="userDialog.query.pageSize"
        @pagination="queryUsers"
      />
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="800px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号"><el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWos()" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWos()">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="380" @row-dblclick="selectWo">
        <el-table-column label="工单编号" prop="woNo" width="160" />
        <el-table-column label="产品" prop="productName" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" prop="woStatus" width="110">
          <template #default="scope"><dict-tag :options="prd_wo_status" :value="scope.row.woStatus" /></template>
        </el-table-column>
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

    <!-- 保养到期列表 -->
    <el-dialog title="保养到期预警" v-model="dueDialog.visible" width="800px" append-to-body>
      <el-table :data="dueDialog.list" show-overflow-tooltip>
        <el-table-column label="工装编码" prop="toolingCode" min-width="140" />
        <el-table-column label="工装名称" prop="toolingName" min-width="130" />
        <el-table-column label="类型" prop="toolingType" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_type" :value="scope.row.toolingType" /></template>
        </el-table-column>
        <el-table-column label="已用(次)" prop="usedCount" align="right" width="100" />
        <el-table-column label="剩余(次)" prop="remainingCount" align="right" width="100">
          <template #default="scope"
            ><span class="text-danger">{{ scope.row.remainingCount }}</span></template
          >
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_status" :value="scope.row.status" /></template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="TgTooling">
import {
  listTooling,
  getTooling,
  addTooling,
  updateTooling,
  delTooling,
  checkoutTooling,
  returnTooling,
  startMaintainTooling,
  completeMaintainTooling,
  scrapTooling,
  listMaintenanceDue,
  listCalibrationDue,
  registerCalibration
} from '@/api/mes/tg/tooling';
import type { TgToolingVO, TgToolingQuery, TgCheckoutBo, TgMaintainCompleteBo, TgCalibrationBo } from '@/api/mes/tg/tooling';
import { previewCode } from '@/api/system/autocode/rule';
import { listWorkshop } from '@/api/erp/bd/workshop';
import { listWorkstation } from '@/api/erp/bd/workstation';
import { listUser } from '@/api/system/user';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_tooling_status, mes_tooling_type, tg_maintenance_type, prd_wo_status, mes_tooling_calibration_result } = toRefs<any>(
  proxy?.useDict('mes_tooling_status', 'mes_tooling_type', 'tg_maintenance_type', 'prd_wo_status', 'mes_tooling_calibration_result')
);

const toolingRef = ref<FormInstance>();
const autoGenFlag = ref(false);
const currentToolingId = ref<number>(0);

const checkoutDialog = reactive({ visible: false });
const checkoutForm = reactive<TgCheckoutBo>({ operatorId: 0 } as TgCheckoutBo);
const returnDialog = reactive({ visible: false });
const returnForm = reactive<{ usageCount: number; usageHours: number; remark: string }>({ usageCount: 1, usageHours: 0, remark: '' });
const maintainDialog = reactive({ visible: false });
const maintainForm = reactive<TgMaintainCompleteBo>({ maintainType: 'PLANNED' });
const dueDialog = reactive({ visible: false, list: [] as TgToolingVO[] });

// ===== 校准(MES-TG-01/TG-05) =====
const calibrationDialog = reactive({ visible: false });
const calibrationSubmitting = ref(false);
const calibrationToolingLabel = ref('');
const calibrationForm = reactive<TgCalibrationBo>({ calibrationResult: 'PASS' });
const calibrationDueDialog = reactive({ visible: false, list: [] as TgToolingVO[] });

const userSelectTarget = ref<'operator' | 'maintainer'>('operator');
const workshopDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, workshopCode: '', workshopName: '' }
});
const workstationDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, workstationCode: '', workstationName: '' }
});
const userDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, userName: '', nickName: '' }
});
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' }
});

const queryParams = ref<TgToolingQuery>({
  pageNum: 1,
  pageSize: 10,
  toolingCode: undefined,
  toolingName: undefined,
  toolingType: undefined,
  status: undefined
});

const rules = {
  toolingName: [{ required: true, message: '工装名称不能为空', trigger: 'blur' }],
  toolingType: [{ required: true, message: '工装类型不能为空', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd,
  handleUpdate
} = useCrud<TgToolingVO, TgToolingQuery>({
  apis: { list: listTooling, get: getTooling, add: addTooling, update: updateTooling, del: delTooling },
  idField: 'toolingId',
  entityName: '工装',
  formRef: toolingRef,
  queryParams,
  initForm: () => ({
    toolingId: undefined,
    toolingCode: '',
    toolingName: '',
    toolingType: '',
    spec: '',
    workshopId: undefined,
    workshopName: '',
    totalLifeCount: undefined,
    totalLifeHours: undefined,
    purchaseDate: '',
    purchaseCost: undefined,
    calibrationCycleDays: undefined,
    nextCalibrationDate: '',
    remark: ''
  }),
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: (formData: Partial<TgToolingVO>) => {
    // 新增时勾选自动生成则清空编码, 由后端生成
    if (autoGenFlag.value && formData.toolingId == null) {
      formData.toolingCode = undefined;
    }
  }
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// ===== 领用/归还/保养/报废 =====
const handleCheckout = (row: TgToolingVO) => {
  currentToolingId.value = row.toolingId;
  Object.assign(checkoutForm, {
    operatorId: 0,
    operatorName: '',
    woId: undefined,
    woNo: '',
    workstationId: undefined,
    workstationName: '',
    remark: ''
  });
  checkoutDialog.visible = true;
};
const submitCheckout = async () => {
  if (!checkoutForm.operatorId) {
    proxy?.$modal.msgWarning('请选择操作员');
    return;
  }
  await checkoutTooling(currentToolingId.value, checkoutForm);
  proxy?.$modal.msgSuccess('领用成功');
  checkoutDialog.visible = false;
  getList();
};
const handleReturn = (row: TgToolingVO) => {
  currentToolingId.value = row.toolingId;
  Object.assign(returnForm, { usageCount: 1, usageHours: 0, remark: '' });
  returnDialog.visible = true;
};
const submitReturn = async () => {
  await returnTooling(currentToolingId.value, { usageCount: returnForm.usageCount, usageHours: returnForm.usageHours, remark: returnForm.remark });
  proxy?.$modal.msgSuccess('归还成功');
  returnDialog.visible = false;
  getList();
};
const handleStartMaintain = async (row: TgToolingVO) => {
  const remark = await proxy?.$modal.prompt('请输入保养原因(可选)', '开始保养', { inputType: 'textarea' });
  await startMaintainTooling(row.toolingId, (remark as string) || '');
  proxy?.$modal.msgSuccess('已开始保养');
  getList();
};
const handleCompleteMaintain = (row: TgToolingVO) => {
  currentToolingId.value = row.toolingId;
  Object.assign(maintainForm, {
    planId: undefined,
    maintainType: 'PLANNED',
    maintainerId: undefined,
    maintainerName: '',
    description: '',
    cost: undefined,
    remark: ''
  });
  maintainDialog.visible = true;
};
const submitCompleteMaintain = async () => {
  await completeMaintainTooling(currentToolingId.value, maintainForm);
  proxy?.$modal.msgSuccess('保养完成,计数已归零');
  maintainDialog.visible = false;
  getList();
};
const handleScrap = async (row: TgToolingVO) => {
  const reason = await proxy?.$modal.prompt('请输入报废原因', '工装报废', { inputType: 'textarea' });
  await scrapTooling(row.toolingId, (reason as string) || '');
  proxy?.$modal.msgSuccess('报废成功');
  getList();
};
const handleDue = async () => {
  const res = await listMaintenanceDue();
  dueDialog.list = res.data;
  dueDialog.visible = true;
};

// ===== 校准(MES-TG-01/TG-05) =====

/** 校准到期/超期预警：服务端窗口为 今天 ~ 今天+7 天，含已超期 */
const handleCalibrationDue = async () => {
  const res = await listCalibrationDue();
  calibrationDueDialog.list = res.data || [];
  calibrationDueDialog.visible = true;
};

/** 打开「登记校准」对话框 */
const handleCalibrate = (row: TgToolingVO) => {
  currentToolingId.value = row.toolingId;
  calibrationToolingLabel.value = `${row.toolingCode} - ${row.toolingName}`;
  Object.assign(calibrationForm, {
    calibrationCycleDays: row.calibrationCycleDays,
    calibrationDate: undefined,
    calibrationResult: 'PASS',
    calibratedBy: undefined,
    calibrationRemark: '',
    remark: undefined
  });
  calibrationDialog.visible = true;
};

/** 提交登记校准：下次校准日由服务端计算（TgToolingServiceImpl.registerCalibration），前端不传 */
const submitCalibration = async () => {
  if (!calibrationForm.calibrationResult) {
    proxy?.$modal.msgWarning('请选择校准结果');
    return;
  }
  calibrationSubmitting.value = true;
  try {
    await registerCalibration(currentToolingId.value, calibrationForm);
    proxy?.$modal.msgSuccess('校准已登记');
    calibrationDialog.visible = false;
    getList();
  } finally {
    calibrationSubmitting.value = false;
  }
};

/** 校准超期行高亮（MES-TG-01，后端 TgToolingVo.calibrationOverdue） */
const rowClassName = ({ row }: { row: TgToolingVO }) => (row.calibrationOverdue ? 'calibration-overdue-row' : '');

/** 日期格式化：截取到日（与 mes/eq/oee、mes/eq/repair/statistics 同型实现） */
const fmtDate = (s?: string) => (s ? String(s).substring(0, 10) : '-');

const lifeWarn = (row: TgToolingVO) => {
  if (!row.totalLifeCount) return false;
  return (row.usedCount || 0) >= row.totalLifeCount * 0.9;
};

// ===== 编码自动生成 =====
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    previewCode('MES_TOOLING_CODE').then((response) => {
      form.value.toolingCode = response as unknown as string;
    });
  } else {
    form.value.toolingCode = '';
  }
};

// ===== 选择器对话框 =====
const openWorkshopDialog = () => {
  workshopDialog.visible = true;
  queryWorkshops();
};
const queryWorkshops = () => {
  listWorkshop(workshopDialog.query as any).then((res: any) => {
    workshopDialog.list = res.rows || [];
    workshopDialog.total = res.total || 0;
  });
};
const selectWorkshop = (row: any) => {
  form.value.workshopId = row.workshopId;
  form.value.workshopName = row.workshopName;
  workshopDialog.visible = false;
};
const openWorkstationDialog = () => {
  workstationDialog.visible = true;
  queryWorkstations();
};
const queryWorkstations = () => {
  listWorkstation(workstationDialog.query as any).then((res: any) => {
    workstationDialog.list = res.rows || [];
    workstationDialog.total = res.total || 0;
  });
};
const selectWorkstation = (row: any) => {
  checkoutForm.workstationId = row.workstationId;
  checkoutForm.workstationName = row.workstationName;
  workstationDialog.visible = false;
};
const openUserDialog = (target: 'operator' | 'maintainer') => {
  userSelectTarget.value = target;
  userDialog.visible = true;
  queryUsers();
};
const queryUsers = () => {
  listUser(userDialog.query as any).then((res: any) => {
    userDialog.list = res.rows || [];
    userDialog.total = res.total || 0;
  });
};
const selectUser = (row: any) => {
  if (userSelectTarget.value === 'operator') {
    checkoutForm.operatorId = row.userId;
    checkoutForm.operatorName = row.nickName;
  } else {
    maintainForm.maintainerId = row.userId;
    maintainForm.maintainerName = row.nickName;
  }
  userDialog.visible = false;
};
const openWoDialog = () => {
  woDialog.visible = true;
  queryWos();
};
const queryWos = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const selectWo = (row: any) => {
  checkoutForm.woId = row.mesWoId;
  checkoutForm.woNo = row.woNo;
  woDialog.visible = false;
};

getList();
</script>

<style scoped>
.text-danger {
  color: var(--el-color-danger);
  font-weight: bold;
}
/* MES-TG-01：校准超期行高亮（row-class-name 落在 <tr> 上，需 :deep 穿透 scoped） */
:deep(.calibration-overdue-row) > td.el-table__cell {
  background-color: var(--el-color-danger-light-9);
}
</style>
