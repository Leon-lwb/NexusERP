<template>
  <span>
    <el-button :type="btnType" @click="applySubmit()" :loading="submitLoading">{{ text }}</el-button>
    <el-dialog :title="firstInitiatorTitle" v-model="firstInitiatorOpen" width="50%" append-to-body>
      <el-alert title="选择提醒" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="重新发起新流程按钮"> 重新发起新流程会删除之前发起的任务,重新开始. </el-descriptions-item>
        <el-descriptions-item label="继续发起老流程按钮"> 继续发起流程就在原来流程基础上继续流转. </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="ReStartByDataId(true)">重新发起新流程</el-button>
          <el-button type="primary" @click="ReStartByDataId(false)">继续发起老流程</el-button>
          <el-button @click="firstInitiatorOpen = false">取 消</el-button>
        </span>
      </template>
    </el-dialog>

    <!--挂载关联多个流程-->
    <el-dialog :title="flowTitle" v-model="flowOpen" width="70%" append-to-body>
      <el-row :gutter="64">
        <el-col :span="20" :xs="64" style="width: 100%">
          <el-table ref="singleTable" :data="processList" border highlight-current-row style="width: 100%">
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="主键" align="center" prop="id" v-if="true" />
            <el-table-column label="业务表单名称" align="center" prop="businessName" />
            <el-table-column label="业务服务名称" align="center" prop="businessService" />
            <el-table-column label="流程名称" align="center" prop="flowName" />
            <el-table-column label="关联流程发布主键" align="center" prop="deployId" />
            <el-table-column label="前端路由地址" align="center" prop="routeName" />
            <el-table-column label="组件注入方法" align="center" prop="component" />
            <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button size="small" type="primary" @click="selectProcess(scope.row)">确定</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </el-dialog>
  </span>
</template>

<script lang="ts" setup>
import { startByDataId, isFirstInitiator, deleteActivityAndJoin, getProcesss } from '@/api/workflow/process/index';

defineOptions({ name: 'ActApplyBtn' });
const props = defineProps({
  btnType: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'danger' | 'info' | 'text' | 'default' | ''>,
    default: 'primary',
    required: false
  },
  dataId: {
    type: String,
    default: '',
    required: true
  },
  serviceName: {
    type: String,
    default: '',
    required: true
  },
  variables: {
    type: Object,
    default: () => ({})
  },
  text: {
    type: String,
    default: '提交申请',
    required: false
  }
});

const emit = defineEmits(['success']);

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const modalVisible = ref(false);
const submitLoading = ref(false);
const form = ref<any>({});
const firstInitiatorOpen = ref(false);
const firstInitiatorTitle = ref('');
// 关联流程数据
const processList = ref<any>([]);
const flowOpen = ref(false);
const flowTitle = ref('');
const selectFlowId = ref(''); //选择或使用的流程ID
const error = ref('');

const selectProcess = (row) => {
  selectFlowId.value = row.id;
  flowOpen.value = false;
  const params = Object.assign(
    {
      dataId: props.dataId
    },
    props.variables
  );
  startByDataId(props.dataId, selectFlowId.value, props.serviceName, params)
    .then((res) => {
      if (res.code == 200) {
        proxy?.$modal.msgSuccess(res.msg);
        emit('success');
      } else {
        proxy?.$modal.msgError(res.msg);
      }
    })
    .finally(() => (submitLoading.value = false));
};
const ReStartByDataId = (isNewFlow: boolean) => {
  if (isNewFlow) {
    submitLoading.value = true;
    deleteActivityAndJoin(props.dataId, props.variables)
      .then((res) => {
        if (res.code == 200 && res.data) {
          //若删除成功
          const params = Object.assign(
            {
              dataId: props.dataId
            },
            props.variables
          );
          startByDataId(props.dataId, selectFlowId.value, props.serviceName, params).then((res) => {
            if (res.code == 200) {
              firstInitiatorOpen.value = false;
              proxy?.$modal.msgSuccess(res.msg);
              emit('success');
            } else {
              proxy?.$modal.msgError(res.msg);
            }
          });
        }
      })
      .finally(() => (submitLoading.value = false));
  } else {
    //继续原有流程流转，跳到流程处理界面上
    router.push({
      path: '/flowable/task/record/index',
      query: {
        procInsId: props.variables.processInstanceId,
        deployId: props.variables.deployId,
        taskId: props.variables.taskId,
        businessKey: props.dataId,
        nodeType: '',
        category: 'zdyyw',
        finished: 'true'
      }
    });
  }
};
const applySubmit = () => {
  if (props.dataId && props.dataId.length < 1) {
    error.value = '必须传入参数dataId';
    proxy?.$modal.msgError(error.value);
    return;
  }
  if (props.serviceName && props.serviceName.length < 1) {
    error.value = '必须传入参数serviceName';
    proxy?.$modal.msgError(error.value);
    return;
  } else {
    error.value = '';
  }
  //对于自定义业务,判断是否是驳回或退回的第一个发起人节点
  submitLoading.value = true;
  isFirstInitiator(props.dataId, props.variables)
    .then((res) => {
      if (res.code === 200 && res.data) {
        //若是，弹出窗口选择重新发起新流程还是继续老流程
        firstInitiatorTitle.value = '根据自己需要进行选择';
        firstInitiatorOpen.value = true;
      } else {
        submitLoading.value = true;
        const processParams = {
          serviceName: props.serviceName
        };
        getProcesss(processParams)
          .then((res) => {
            /**查询关联流程信息 */
            processList.value = res.data;
            submitLoading.value = false;
            if (processList.value && processList.value.length > 1) {
              flowOpen.value = true;
            } else if (processList.value && processList.value.length === 1) {
              selectFlowId.value = res.data[0].id;
              const params = Object.assign(
                {
                  dataId: props.dataId
                },
                props.variables
              );
              startByDataId(props.dataId, selectFlowId.value, props.serviceName, params)
                .then((res) => {
                  if (res.code == 200) {
                    proxy?.$modal.msgSuccess(res.msg);
                    emit('success');
                  } else {
                    proxy?.$modal.msgError(res.msg);
                  }
                })
                .finally(() => (submitLoading.value = false));
            } else {
              proxy?.$modal.msgError('检查该业务是否已经关联流程！');
            }
          })
          .finally(() => (submitLoading.value = false));
      }
    })
    .finally(() => (submitLoading.value = false));
};
</script>
