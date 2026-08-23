<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="日期" prop="recordDate">
            <el-date-picker
              v-model="queryParams.recordDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              clearable
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工人姓名" prop="workerName">
            <el-input v-model="queryParams.workerName" placeholder="请输入工人姓名" clearable @keyup.enter="handleQuery" />
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="2.5">
        <el-button type="primary" plain icon="Search" @click="loadFailRecords">查询 FAIL 记录</el-button>
      </el-col>
      <el-col :span="6">
        <el-alert type="info" :closable="false" show-icon>
          <template #title> 复查窗口:FAIL 记录需在 24h 内复核(误判改 PASS / 代打卡确认 FAIL)。操作会写入审计备注。 </template>
        </el-alert>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadFailRecords"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="考勤ID" align="center" prop="attendanceId" width="90" />
      <el-table-column label="工人" align="left" prop="workerName" min-width="110" show-overflow-tooltip />
      <el-table-column label="考勤日期" align="center" prop="recordDate" width="110" />
      <el-table-column label="上班打卡" align="center" prop="clockInTime" width="160" />
      <el-table-column label="下班打卡" align="center" prop="clockOutTime" width="160" />
      <el-table-column label="考勤结果" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.attendanceStatus)" size="small">{{ statusLabel(scope.row.attendanceStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="人脸状态" align="center" width="100">
        <template #default>
          <el-tag type="danger" size="small">FAIL</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="相似度" align="center" width="90">
        <template #default="scope">
          {{ scope.row.faceSimilarity != null ? scope.row.faceSimilarity.toFixed(4) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="验证时间" align="center" prop="faceVerifyTime" width="160" />
      <el-table-column label="备注" align="left" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="success" icon="Check" @click="handleMarkPass(scope.row)" v-hasPermi="['sch:attendance:review']">误判</el-button>
          <el-button link type="danger" icon="Warning" @click="handleMarkProxy(scope.row)" v-hasPermi="['sch:attendance:review']">代打卡</el-button>
          <el-button link type="primary" icon="Picture" @click="handleViewPhoto(scope.row)">照片</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="loadFailRecords"
    />

    <!-- 复查确认对话框 -->
    <el-dialog v-model="reviewDialog.visible" :title="reviewDialog.title" width="440px" append-to-body>
      <el-form :model="reviewDialog" label-width="100px">
        <el-form-item label="复查结果">
          <el-tag :type="reviewDialog.action === 'pass' ? 'success' : 'danger'" size="small">
            {{ reviewDialog.action === 'pass' ? '标记误判 → 改 PASS' : '确认代打卡 → 保持 FAIL' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="复查备注">
          <el-input v-model="reviewDialog.remark" type="textarea" :rows="3" placeholder="可选,记录复核依据(写入审计)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button :type="reviewDialog.action === 'pass' ? 'success' : 'danger'" :loading="reviewDialog.submitting" @click="confirmReview">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 打卡照片查看 -->
    <el-dialog v-model="photoDialog.visible" title="打卡人脸照片" width="360px" append-to-body>
      <el-image v-if="photoDialog.src" :src="photoDialog.src" fit="contain" style="width: 100%; border-radius: 8px" />
      <el-empty v-else description="无照片(未上传/未存证)" />
      <div v-if="photoDialog.ossId" style="margin-top: 8px; color: #909399; font-size: 12px">存证 ossId: {{ photoDialog.ossId }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listAttendance, markFacePass, markFaceProxy, AttendanceVO } from '@/api/erp/sch/attendance';

const loading = ref(false);
const showSearch = ref(true);
const dataList = ref<AttendanceVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  workerName: '',
  recordDate: '',
  faceVerifyStatus: 'FAIL'
});

const reviewDialog = reactive({
  visible: false,
  title: 'FAIL 复查',
  action: 'pass' as 'pass' | 'proxy',
  remark: '',
  submitting: false,
  current: null as AttendanceVO | null
});

const photoDialog = reactive({
  visible: false,
  src: '',
  ossId: null as number | null
});

onMounted(() => {
  loadFailRecords();
});

function loadFailRecords() {
  loading.value = true;
  listAttendance({
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    workerName: queryParams.workerName || undefined,
    recordDate: queryParams.recordDate || undefined,
    faceVerifyStatus: 'FAIL'
  })
    .then((res) => {
      dataList.value = res.rows || [];
      total.value = res.total || 0;
    })
    .finally(() => (loading.value = false));
}

function handleQuery() {
  queryParams.pageNum = 1;
  loadFailRecords();
}

function resetQuery() {
  queryParams.workerName = '';
  queryParams.recordDate = '';
  queryParams.pageNum = 1;
  loadFailRecords();
}

function handleMarkPass(row: AttendanceVO) {
  reviewDialog.action = 'pass';
  reviewDialog.title = `误判确认 - ${row.workerName}(${row.attendanceId})`;
  reviewDialog.remark = '';
  reviewDialog.current = row;
  reviewDialog.visible = true;
}

function handleMarkProxy(row: AttendanceVO) {
  reviewDialog.action = 'proxy';
  reviewDialog.title = `代打卡确认 - ${row.workerName}(${row.attendanceId})`;
  reviewDialog.remark = '';
  reviewDialog.current = row;
  reviewDialog.visible = true;
}

function confirmReview() {
  if (!reviewDialog.current) return;
  reviewDialog.submitting = true;
  const id = reviewDialog.current.attendanceId;
  const action =
    reviewDialog.action === 'pass' ? markFacePass(id, reviewDialog.remark || undefined) : markFaceProxy(id, reviewDialog.remark || undefined);
  action
    .then(() => {
      ElMessage.success(reviewDialog.action === 'pass' ? '已标记误判(PASS)' : '已确认代打卡(FAIL)');
      reviewDialog.visible = false;
      loadFailRecords();
    })
    .finally(() => (reviewDialog.submitting = false));
}

function handleViewPhoto(row: AttendanceVO) {
  photoDialog.ossId = row.faceImageOssId ?? null;
  photoDialog.visible = true;
  // 私有桶图片需走后端代理下载(临时 presigned URL 或代理流),此处先展示 ossId 存证信息
  photoDialog.src = row.faceImageOssId ? `/erp/sch/attendance/face-photo/${row.faceImageOssId}` : '';
}

function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  const map: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    normal: 'success',
    late: 'warning',
    early_leave: 'warning',
    absent: 'danger',
    leave: 'info'
  };
  return map[status] || 'info';
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    normal: '正常',
    late: '迟到',
    early_leave: '早退',
    absent: '旷工',
    leave: '请假'
  };
  return map[status] || status;
}
</script>
