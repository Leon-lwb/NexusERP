<template>
  <div class="app-container">
    <el-tabs tab-position="top" v-model="activeName" :model-value="processed === true ? 'approval' : 'form'" @tab-click="changeTab">
      <el-tab-pane label="任务办理" name="approval" v-if="processed === true">

        <!-- 自定义路由业务表单在「表单信息」展示；任务办理仅保留审批操作 -->
        <el-card class="box-card" shadow="hover" v-if="taskFormVisible && !taskFormCustom.visible">
          <template #header>
            <span>填写表单</span>
          </template>
          <div class="cu-content">
            <!-- 自定义业务表单（基于路由组件） -->
            <component v-if="taskFormCustom.visible"
              ref="taskCustomFormRef"
              :disabled="taskFormCustom.disabled"
              :is="taskFormCustom.formComponent"
              :model="taskFormCustom.model"
              :customFormData="taskFormCustom.customFormData"
              :isNew="taskFormCustom.isNew"
            />
            <!-- 表单设计器 / OA 表单 -->
            <div class="form-container" v-else style="max-width: 100%;">
              <FormRender
                :form-content="JSON.stringify(getFormContentWithWiderLabels())"
                :initial-data="getInitialMainData(taskFormData.value)"
                :dict-options="dictOptions"
                ref="taskFormRenderRef"
                label-position="right"
                label-width="140px"
              />
            </div>
          </div>
        </el-card>
        <el-card class="box-card" shadow="hover">
          <template #header>
            <span>审批流程</span>
          </template>
          <el-row>
            <el-col :span="20" :offset="2">
              <el-form ref="taskFormRef" :model="taskForm" :rules="rules" label-width="120px">
                <el-form-item label="审批意见" prop="comment">
                  <el-input type="textarea" :rows="5" v-model="taskForm.comment" placeholder="请输入 审批意见" />
                </el-form-item>
                <el-form-item label="抄送人" prop="copyUserIds">
                  <el-tag :key="index" v-for="(item, index) in copyUser" :disable-transitions="false" @close="handleClose('copy', item)">
                    {{ item.nickName }}
                  </el-tag>
                  <el-button class="button-new-tag" type="primary" icon="el-icon-plus" circle @click="onSelectCopyUsers" />
                </el-form-item>
                <el-form-item label="指定审批人" prop="copyUserIds">
                  <el-tag :key="index" v-for="(item, index) in nextUser" :disable-transitions="false" @close="handleClose('next', item)">
                    {{ item.nickName }}
                  </el-tag>
                  <el-button class="button-new-tag" type="primary" icon="el-icon-plus" circle @click="onSelectNextUsers" />
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
          <el-row :gutter="10" type="flex" justify="center">
            <el-col :span="1.5">
              <el-button icon="CircleCheck" type="success" @click="handleComplete">通过</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="ChatLineSquare" type="primary" @click="handleDelegate">委派</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="Switch" type="success" @click="handleTransfer">转办</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="Plus" type="primary" @click="handleAddSign">加签</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="Position" type="success" @click="handleJump">跳转</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="RefreshLeft" type="warning" @click="handleReturn">退回</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button icon="CircleClose" type="danger" @click="handleReject">驳回</el-button>
            </el-col>
            <el-col :span="1.5">
                <el-button icon="Back" type="danger" @click="handleRefuse">拒绝</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="表单信息" name="form" v-if="showFormTab">
        <div v-if="customForm.visible"> <!-- 自定义表单 -->
          <component ref="refCustomForm" :disabled="customForm.disabled" :is="customForm.formComponent" :model="customForm.model"
            :customFormData="customForm.customFormData" :isNew = "customForm.isNew"></component>
        </div>
        <div v-if="formVisible">
          <!-- 纯自定义路由表单仅由上方 dynamic 组件展示，避免再渲染空 FormRender -->
          <template v-for="(item, index) in processFormList" :key="index">
            <el-card v-if="!item.formValues?.routeName" class="box-card" shadow="never">
              <template #header>
                <span>{{ item.title?.replace(/\(null\)/g, '') || '' }}</span>
              </template>
              <!--流程处理表单模块-->
              <div class="cu-content">
                <!-- 发起人节点 OA 且可编辑时渲染第一张表单 -->
                <FormRender v-if = "dictReady && startUserForm.isStartUserNode && startUserForm.editFormType === 'OA' && processed === true && index === 0"
                   :form-content="getEditableFormContent(item)" :initial-data="getInitialMainData(item)" :dict-options="dictOptions" label-width="200px" label-position="right" :ref="setFormRef">
                </FormRender>
                <!-- 其他节点：根据 disabled 决定是否只读，否则可编辑 -->
                <FormRender v-else-if="dictReady && item.disabled && !startUserForm.isStartUserNode" :form-content="getReadonlyFormContent(item)" :initial-data="getInitialMainData(item)" :dict-options="dictOptions" label-width="200px" label-position="right" :ref="setFormRef" />
                <FormRender v-else-if="dictReady" :form-content="getEditableFormContent(item)" :initial-data="getInitialMainData(item)" :dict-options="dictOptions" label-width="200px" label-position="right" :ref="setFormRef" />
              </div>
            </el-card>
          </template>
        </div>
        <div style="margin-left:10%;margin-bottom: 30px">
           <!--对上传文件进行显示处理，临时方案 add by wanghuan 2022-07-27 -->
           <el-upload action="#" :on-preview="handleFilePreview" :file-list="fileList" v-if="fileDisplay" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="流转记录" name="record">
        <el-card class="box-card" shadow="never">
          <el-col :span="20" :offset="2">
            <div class="block">
              <el-timeline>
                <el-timeline-item v-for="(item, index) in historyProcNodeList" :key="index" :type="tagType(item.endTime)">
                  <p style="font-weight: 700">{{ item.activityName }}</p>
                  <el-card v-if="item.activityType === 'startEvent'" class="box-card" shadow="hover">
                    {{ item.assigneeName }} 在 {{ item.createTime }} 发起流程
                  </el-card>
                  <el-card v-if="item.activityType === 'userTask'" class="box-card" shadow="hover">
                    <el-descriptions :column="5" :labelStyle="{'font-weight': 'bold'}">
                      <el-descriptions-item label="实际办理">{{ item.assigneeName || '-'}}</el-descriptions-item>
                      <el-descriptions-item label="候选办理">{{ item.candidate || '-'}}</el-descriptions-item>
                      <el-descriptions-item label="接收时间">{{ item.createTime || '-'}}</el-descriptions-item>
                      <el-descriptions-item label="办结时间">{{ item.endTime || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="耗时">{{ item.duration || '-'}}</el-descriptions-item>
                    </el-descriptions>
                    <div v-if="item.commentList && item.commentList.length > 0">
                      <div v-for="(comment, index) in item.commentList" :key="index">
                        <el-divider content-position="left">
                          <el-tag :type="approveTypeTag(comment.type)">{{ commentType(comment.type) }}</el-tag>
                          <el-tag type="info" effect="plain">{{ comment.time }}</el-tag>
                        </el-divider>
                        <span>{{ comment.fullMessage }}</span>
                      </div>
                    </div>
                  </el-card>
                  <el-card v-if="item.activityType === 'endEvent'" class="box-card" shadow="hover">
                    {{ item.createTime }} 结束流程
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-col>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="流程跟踪" name="track">
        <el-card class="box-card" shadow="never">
          <process-viewer
            :key="`designer-${loadIndex}`"
            :style="'height:' + height"
            :xml="processXml"
            :finishedInfo="finishedInfo"
            :allCommentList="historyProcNodeList"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!--退回流程-->
    <el-dialog :title="returnDialog.title" v-model="returnDialog.visible" width="40%" append-to-body>
      <el-radio-group v-model="returnTaskKey">
        <el-radio-button v-for="item in returnTaskList" :key="item.id" :label="item.id">
          {{ item.name }}
        </el-radio-button>
      </el-radio-group>
      <template #footer>
        <el-button @click="returnDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitReturn">确 定</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="userSelectDialog.title" v-model="userSelectDialog.visible" width="60%" append-to-body>
      <el-row type="flex" :gutter="20">
        <!--部门数据-->
        <el-col :span="5">
          <el-card shadow="never" style="height: 100%">
            <template #header>
              <span>部门列表</span>
            </template>
            <div>
              <el-input v-model="deptName" placeholder="请输入部门名称" prefix-icon="Search" clearable />
              <el-tree
                :data="deptOptions"
                :props="{label: 'label', children: 'children'}"
                :expand-on-click-node="false"
                :filter-node-method="filterNode"
                ref="deptTreeRef"
                default-expand-all
                @node-click="handleNodeClick"
              />
            </div>
          </el-card>
        </el-col>
        <el-col :span="18">
          <el-table
            ref="userTableRef"
            :key="userSelectType"
            height="400"
            v-loading="userLoading"
            :data="userList"
            highlight-current-row
            :row-key="getRowKeys"

            @selection-change="handleSelectionChange"
          >
            <el-table-column v-if="userSelectType === 'copy' || userSelectType === 'next' || userSelectType === 'addSign'"
             :reserve-selection="true" width="55" type="selection" />
            <el-table-column v-else width="30">
              <template #default="scope">
                <el-radio :label="scope.row.userId" v-model="currentUserId">{{''}}</el-radio>
              </template>
            </el-table-column>
            <el-table-column label="用户名称" align="center" prop="userName" />
            <el-table-column label="用户昵称" align="center" prop="nickName" />
            <el-table-column label="手机" align="center" prop="phonenumber" />
          </el-table>
          <pagination :total="userTotal" v-model:page="queryUserParams.pageNum" v-model:limit="queryUserParams.pageSize" @pagination="getList" />
        </el-col>
      </el-row>
      <template #footer>
        <el-button @click="userSelectDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitUserData">确 定</el-button>
      </template>
    </el-dialog>
    <!--加签流程-->
        <el-dialog :z-index="100" title="addSignTitle" @cancel="addSignOpen = false"
                 v-model="addSignOpen" :width="'40%'" append-to-body>
          <template #header>
            <span>{{ addSignTitle }}</span>
          </template>
          <el-form ref="addSignForm" :model="addSignForm" label-width="160px">
            <el-form-item label="加签类型" prop="addSignType" :rules="[{ required: true, message: '请选择加签类型', trigger: 'blur' }]">
              <el-radio-group v-model="addSignType" @change="changeAddSignType">
                  <el-radio :value = "0" >前加签</el-radio>
                  <el-radio :value = "1" >后加签</el-radio>
                  <el-radio :value = "2" >多实例加签</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="用户选择" prop="copyUserIds" :rules="[{ required: true, message: '请选择用户', trigger: 'blur' }]">
              <el-tag
                :key="index"
                v-for="(item, index) in addSignUser"
                closable
                :disable-transitions="false"
                @close="handleClose('next', item)">
                {{ item.nickName }}
              </el-tag>
              <el-button class="button-new-tag" type="primary" icon="el-icon-plus" size="small" circle @click="onSelectAddSignUsers" />
            </el-form-item>
          </el-form>
          <template #footer>
            <span class="dialog-footer">
              <el-button type="primary" @click="addSignOpen = false">取 消</el-button>
              <el-button type="primary" @click="addSignComplete(true)">确 定</el-button>
            </span>
          </template>
        </el-dialog>
        <!--跳转流程-->
        <el-dialog :z-index="100" :title="jumpTitle" @cancel="jumpOpen = false"
                 v-model="jumpOpen" :width="'40%'" append-to-body>
          <template #header>
            <span>跳转节点</span>
          </template>
          <el-form ref="jumpForm" :model="jumpForm" label-width="160px">
            <el-form-item label="跳转节点" prop="jumpType" :rules="[{ required: true, message: '请选择跳转节点', trigger: 'blur' }]">
              <a-table
                size="middle"
                :columns="jumpNodeColumns"
                :loading="jumpNodeLoading"
                :pagination="false"
                :dataSource="jumpNodeData"
                :rowKey="(record) => record.id"
                :rowSelection="rowSelection"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <span class="dialog-footer">
              <a-button type="primary" @click="jumpOpen = false">取 消</a-button>
              <a-button type="primary" @click="jumpComplete(true)">确 定</a-button>
            </span>
          </template>
        </el-dialog>
  </div>
</template>

<script setup name="Detail" lang="ts">
  import { detailProcess, processIscompleted } from '@/api/workflow/process/index';
  import { getProcessVariables } from '@/api/workflow/work/task';
  import { complete, delegate, transfer, rejectTask, returnList, refuseTask, userTaskList, multiInstanceAddSignTask,
         addSignTask, returnTask, jumpTask } from "@/api/workflow/work/task";
  import { deptTreeSelect } from "@/api/workflow/identity";
  import { listUser as selectUser } from "@/api/system/user";
  import { TaskForm } from "@/api/workflow/work/types";
  import { DeptVO } from "@/api/workflow/identity/types";

  import { ComponentInternalInstance, getCurrentInstance, ref, reactive, toRaw, nextTick, computed } from "vue";
  import {useRoute, useRouter} from "vue-router";
  import { getToken } from '@/utils/auth';
  import {
        useFlowable
    } from '@/views/workflow/hooks/useFlowable'
  import FormRender from '@/components/DesignerForm/FormRender.vue'
  import { getDicts } from '@/api/system/dict/data'

  const route = useRoute();
  const router = useRouter();
  const { proxy } = getCurrentInstance() as ComponentInternalInstance;
  const { getFormComponent } = useFlowable()

  const userList = ref<any[]>([]);
  const processed = ref(false);
  const taskFormOpen = ref(false)
  const addSignUser = ref<any>([]);
  const userMultipleSelection = ref([]);
  const userSelectType = ref();
  const currentUserId = ref();
  const userLoading = ref(false);
  const userTotal = ref(0);
  const loadIndex = ref('');
  const height = ref(document.documentElement.clientHeight - 205 + 'px;');
  const processXml = ref('');
  const taskFormVisible = ref(false);
  const processFormList = ref<any[]>([]);
  const taskFormData = reactive<any>({});
  const historyProcNodeList = ref<any>();
  const formVisible = ref(false);
  const finishedInfo = ref({});
  const showFormTab = ref(true);

  // 字典数据
  const dictOptions = ref<Record<string, any[]>>({});
  const dictReady = ref(false)

  // 获取字典数据
  const loadDictData = async (dictType: string) => {
    if (!dictType || dictOptions.value[dictType]) return;
    try {
      const res = await getDicts(dictType);
      if (res.code === 200) {
        const options = res.data.map((item: any) => ({
          label: item.dictLabel,
          value: item.dictValue
        }));
        dictOptions.value[dictType] = options;
        // 加载好的字典选项用于渲染
      }
    } catch (error) {
      console.error(`获取字典数据失败 ${dictType}:`, error);
    }
  };

  // 加载表单中所有需要的字典数据
  const loadAllDictData = async (formComponents: any[]) => {
    const dictTypes = new Set<string>();
    formComponents.forEach(component => {
      // 兼容 props.dictType 与 顶层 dictType 两种写法
      const dictTypeFromProps = component?.props?.dictType
      const dictTypeFromTop = component?.dictType
      if ((component?.props?.dataSource === 'dict' && dictTypeFromProps) || dictTypeFromTop) {
        dictTypes.add(dictTypeFromProps || dictTypeFromTop);
      }

      // 子表内字段也可能使用字典
      if (component?.type === 'subtable' && Array.isArray(component?.subTableColumns)) {
        component.subTableColumns.forEach((col: any) => {
          const colDictTypeFromProps = col?.props?.dictType
          const colDictTypeFromTop = col?.dictType
          if ((col?.props?.dataSource === 'dict' && colDictTypeFromProps) || colDictTypeFromTop) {
            dictTypes.add(colDictTypeFromProps || colDictTypeFromTop)
          }
        })
      }
    });

    // 需要加载的字典类型集合

    for (const dictType of dictTypes) {
      await loadDictData(dictType);
    }
  };

  const customForm = reactive ({ //自定义业务表单
    formId: '',
    title: '',
    disabled: false,
    visible: false,
    formComponent: null,
    model: {},
    /*流程数据*/
    customFormData: {},
    isNew: false,
    disableSubmit: true
  })

  const taskFormCustom = reactive({ //任务办理中的自定义业务表单
    visible: false,
    disabled: true,
    formComponent: null,
    model: {},
    customFormData: {},
    isNew: false
  })

  const deptName = ref('');
  const deptOptions = ref<DeptVO[]>([]);

  const returnTaskList = ref();
  const returnTaskKey = ref();

  const copyUser = ref<any>([]);
  const nextUser = ref<any>([]);

  const vfRenderRef = ref(null);
  const taskFormRef = ref();
  const formRenderRefs = ref<any[]>([]);
  const setFormRef = (el: any) => { if (el) formRenderRefs.value.push(el) }
  const taskFormRenderRef = ref<any>(null)
  const deptTreeRef = ref(null);
  const refCustomForm = ref(null);

  const fileDisplay = ref(false)// 是否显示上传的文件控件
  const fileList = ref<any>([]); //表单设计器上传的文件列表
  const activeName = ref(''); //获取当然tabname
  const key = ref(null);
  const userTableRef = ref<ElTableInstance>();

  const returnDialog = reactive<DialogOption>({
    visible: false,
    title: '退回流程'
  });

  const startUserForm  = ref({
    isStartUserNode: false, //第一个用户任务发起节点
    editFormType: 'OA', //第一发起人节点编辑的表单类型
  })
  const addSignOpen = ref(false) //前加签
  const addSignTitle = ref('')  //加签标题
  const addSignType = ref(0) //加签类型 不单独出来好像vue3操作有问题(vue2版本是放在addSignForm里)
  const addSignForm = ref({
    multiple: true,
    comment: "", // 意见内容
    commentFileDto: { //意见里的附件
      type: '',
      fileurl: ''
    },
    procInsId: "", // 流程实例编号
    taskId: "", // 流程任务编号
    vars: "",
    targetKey: "",
    addSignUsers: "", //委托加签人员
  })
  const jumpOpen = ref(false) //跳转
  const jumpTitle = ref('')   //跳转标题
  const jumpForm = reactive({
    multiple: true,
    comment: "", // 意见内容
    commentFileDto: { //意见里的附件
      type: '',
      fileurl: ''
    },
    procInsId: "", // 流程实例编号
    taskId: "", // 流程任务编号
    vars: "",
    targetKey: "",
    jumpNode: "", //跳转节点
    targetActId: "",
    targetActName: "",
  })
  const jumpNodeLoading = ref(false)
  const jumpNodeData = ref<any>([])
  const jumpNodeColumns = ref([
    {
      title: '节点名称',
      dataIndex: 'name'
    }
  ])

  const selectedJumpRowKeys = ref<any>([]);
  const selectedJumpRows = ref<any>([])
  let nbcioHeader:any ;
  const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
  // 查询参数
  const queryUserParams = ref({
    pageNum: 1,
    pageSize: 10,
    deptId: ''
  })

  const userSelectDialog = reactive<DialogOption>({
    visible: false,
    title: ''
  });

  const taskForm = reactive<TaskForm>({
    comment: '',
    procInsId: '',
    taskId: '',
    userId: '',
    copyUserIds: '',
    nextUserIds: '',
    vars: '',
    targetKey: '',
  });

  const rules = ref({
    comment: [{ required: true, message: '请输入审批意见', trigger: 'blur' }]
  });

  const tagType = (val: any) => {
    if (val) {
        return "success";
    } else {
        return "info";
    }
  }
  const commentType = (val: string) => {
    switch (val) {
      case '1': return '通过'
      case '2': return '退回'
      case '3': return '驳回'
      case '4': return '委派'
      case '5': return '转办'
      case '6': return '终止'
      case '7': return '撤回'
      case '8': return '拒绝'
      case '9': return '跳过'
      case '10': return '前加签'
      case '11': return '后加签'
      case '12': return '多实例加签'
      case '13': return '跳转'
      case '14': return '收回'
    }
  }
  const approveTypeTag = (val: string) => {
    switch (val) {
      case '1': return 'success'
      case '2': return 'warning'
      case '3': return 'danger'
      case '4': return 'primary'
      case '5': return 'success'
      case '6': return 'danger'
      case '7': return 'info'
    }
  }

  const initData = () => {
    taskForm.procInsId = route.params && route.params.procInsId as string;
    taskForm.taskId  = route.query && route.query.taskId as string;
    const dataId  = route.query && (route.query as any).dataId as string;
    processed.value = route.query && (route.query.processed || false) === "true";

    // 流程任务重获取变量表单
    //getProcessDetails(taskForm.procInsId, taskForm.taskId);
    //判断流程是否结束
    processIscompleted({procInsId: taskForm.procInsId}).then(res => {

      if(res.data) {
       processed.value = false;
      }
      // 获取流程变量
      if (taskForm.taskId) {
        processVariables(taskForm.taskId);
      } else {
        // 仅查看流程（没有 taskId 时），也允许加载流程详情/轨迹
        getProcessDetails(taskForm.procInsId, '', dataId || '');
        loadIndex.value = taskForm.procInsId;
        activeName.value = processed.value ? 'approval' : 'form';
      }
    });
  };
  /** 通过条件过滤节点  */
  const filterNode = (value: string, data: any) => {
    if (!value) return true
    return data.label.indexOf(value) !== -1
  }

  //点击文件列表中已上传文件进行下载
  const handleFilePreview = (file: any) => {
    location.href = file.url;
  }

  // 节点单击事件
  const handleNodeClick = (data: any) => {
    queryUserParams.value.deptId = data.id;
    getList();
  }
  /** 查询部门下拉树结构 */
  const getTreeSelect = async () => {
    const res = await deptTreeSelect();
    deptOptions.value = res.data;
  };
  /** 查询用户列表 */
  const getList = async () => {
    userLoading.value = true;
    const res = await selectUser(proxy?.addDateRange(queryUserParams.value, dateRange.value));
    userList.value = res.rows;
    userTotal.value = res.total;
    //toggleSelection(userMultipleSelection.value);
    userLoading.value = false;
  }

  /** 获取流程变量内容 */
  const processVariables = (taskId: string) => {

    if (taskId) {
      getProcessVariables(taskId).then(res => {

        if(res.code == 200) {
          if(res.data.hasOwnProperty('dataId') && res.data.dataId) {
            customForm.formId = res.data.dataId;
            // 流程任务重获取变量表单
            getProcessDetails(taskForm.procInsId, taskForm.taskId, res.data.dataId);
            loadIndex.value = taskForm.procInsId;
            if(processed.value) {
              activeName.value = "approval";
            }
            else {
              activeName.value = "form";
            }
          }
          else {
            // 流程任务重获取变量表单
            getProcessDetails(taskForm.procInsId, taskForm.taskId, "");
            loadIndex.value = taskForm.procInsId;
            if(processed.value) {
              activeName.value = "approval";
            }
            else {
              activeName.value = "form";
              // 回填数据,这里主要是处理文件列表显示,临时解决，以后应该在formdesigner里完成
              processFormList.value.forEach((item, i) => {
                if (item?.hasOwnProperty('list')) {
                  fillFormData(item.list, item)
                  // 更新表单
                  key.value = +new Date().getTime()
                }
              });
            }
          }
        }
      });
    }
  }

  const changeTab = (tab, event) => {

    if(tab.paneName.value === 'form') {

      if(customForm.formId === "") {
        // 回填数据,这里主要是处理文件列表显示,临时解决，以后应该在表单设计器里完成
        processFormList.value?.forEach((item) => {

          if (item?.hasOwnProperty('list')) {
            fillFormData(item.list, item)
            // 更新表单
            key.value = +new Date().getTime()
          }
        });
      }
    }
  }

  const fillFormData = (list, formConf) => { // for 上传文件显示

    list?.forEach((item, i) => {
      // 特殊处理el-upload，包括 回显图片
      if(formConf.formValues[item.id] != '') {
        const val = formConf.formValues[item.id];
        if (item.ele === 'el-upload') {

          if(item['list-type'] != 'text') {//图片
            fileList.value = []    //隐藏加的el-upload文件列表
            //item['file-list'] = JSON.parse(val)
            if(val != '') {
              item['file-list'] = JSON.parse(val)
            }
          }
          else {  //列表

            fileList.value = JSON.parse(val)
            item['file-list'] = [] //隐藏加的表单设计器的文件列表
          }
          // 回显图片
          fileDisplay.value = true
        }
      }

      if (Array.isArray(item.columns)) {
        fillFormData(item.columns, formConf)
      }
    })
  }

  async function getProcessDetails(procInsId: string, taskId: string, dataId: string) {
    const params = {procInsId: procInsId, taskId: taskId, dataId: dataId}
    const res = await detailProcess(params);
    const data = res.data;

    // 避免从其它任务/流程带过来的「任务办理」自定义表单状态
    taskFormCustom.visible = false;
    taskFormCustom.formComponent = null;
    taskFormCustom.model = {};
    taskFormCustom.customFormData = {};
    taskFormCustom.isNew = false;

    processXml.value = data.bpmnXml;
    processFormList.value = data.processFormList;

    // 入库单等部分业务没有可渲染的表单配置时，隐藏“表单信息”页签，只保留流转记录和流程跟踪
    showFormTab.value = !!(processFormList.value && processFormList.value.length > 0);

    // 加载流程表单中所有需要的字典数据（先清空就绪标记）
    dictReady.value = false
    if (processFormList.value && processFormList.value.length > 0) {
      for (const formItem of processFormList.value) {
        const formModel = typeof formItem?.formModel === 'string' ? JSON.parse(formItem.formModel) : (formItem?.formModel || {});
        const formComponents = formModel.formComponents || formModel.widgetList || formModel.components || formModel.fields || [];
        await loadAllDictData(formComponents);
      }
    }
    dictReady.value = true

    // Tab 2 "表单信息": 自定义业务表单初始化
    if(processFormList.value.length == 1 && processFormList.value[0]?.formValues?.hasOwnProperty('routeName')) {
      customForm.disabled = true;
      customForm.visible = true;
      customForm.formComponent = getFormComponent(processFormList.value[0].formValues.routeName).component;
      customForm.model = processFormList.value[0].formValues.formData;
      customForm.customFormData = processFormList.value[0].formValues.formData;
      if(data.startUserNode) {
        customForm.isNew = true;
        customForm.disabled = false;
      }
    }

    // Tab 1 "任务办理": 任务表单初始化
    taskFormVisible.value = data.existTaskForm;
    if (taskFormVisible.value) {
      taskFormData.value = data.taskFormData;

      // 自定义业务表单：仍填充 model 供 complete 提交 variables，不在本 Tab 渲染「填写表单」卡片
      if (taskFormData.value?.formValues?.routeName) {
        taskFormCustom.visible = true;
        taskFormCustom.disabled = true;
        taskFormCustom.formComponent = getFormComponent(taskFormData.value.formValues.routeName).component;
        taskFormCustom.model = taskFormData.value.formValues.formData;
        taskFormCustom.customFormData = taskFormData.value.formValues.formData;
        if (data.startUserNode) {
          taskFormCustom.isNew = true;
          taskFormCustom.disabled = false;
        }
      } else {
        // OA 表单：加载字典数据
        if (taskFormData.value?.formModel?.formComponents) {
          await loadAllDictData(taskFormData.value.formModel.formComponents);
        }
      }
    }
    if(data.startUserNode) {
      startUserForm.value.isStartUserNode = true;
    }
    formVisible.value = true;
    historyProcNodeList.value = data.historyProcNodeList;
    finishedInfo.value = data.flowViewer;
  }

  const onSelectCopyUsers = () => {
    userMultipleSelection.value = copyUser.value;
    onSelectUsers('添加抄送人', 'copy')
  }
  const onSelectNextUsers = () => {
    userMultipleSelection.value = nextUser;
    onSelectUsers('指定审批人', 'next')
  }
  const onSelectUsers = (title: string, type: string) => {
    userSelectType.value = type;
    userSelectDialog.title = title;
    userSelectDialog.visible = true;
    getTreeSelect();
    getList()
  }

  const onSelectAddSignUsers = () => {
    userMultipleSelection.value = addSignUser.value;
    onSelectUsers('指定加签人', 'addSign')
  }

  /** 通过任务 */
  const handleComplete = () => {

    const isExistTaskForm = taskFormCustom.visible || toRaw(taskFormRenderRef.value) !== null || formRenderRefs.value.length > 0;

    // 校验表单
    taskFormRef.value?.validate(async (valid: boolean) => {
      if (valid) {
        let payload: any = { ...toRaw(taskForm) }

        if (isExistTaskForm) {

          try {
            // 自定义业务表单：直接从 model 读取（v-model 双向绑定已更新）
            if (taskFormCustom.visible) {
              payload.variables = { formData: toRaw(taskFormCustom.model) };
            }
            // 优先检查 taskFormRenderRef（节点表单）
            else if (taskFormRenderRef.value && taskFormRenderRef.value.getFormData) {

              const dataObj = await taskFormRenderRef.value.getFormData()

              if (dataObj) {
                const mainData = dataObj?.main || {}
                const subData = dataObj?.sub || null

                const variables: any = { ...toRaw(mainData) }
                if (subData) variables.sub = toRaw(subData)

                payload.variables = variables
              }
            } else if (formRenderRefs.value && formRenderRefs.value.length > 0) {

              const dataObj = await formRenderRefs.value[0]?.getFormData?.()

              if (dataObj) {
                const mainData = dataObj?.main || {}
                const subData = dataObj?.sub || null

                const variables: any = { ...toRaw(mainData) }
                if (subData) variables.sub = toRaw(subData)

                payload.variables = variables
              } else {
                console.warn("🔍 [WARN] handleComplete - 流程表单获取到的数据为空");
              }
            } else {
              console.warn("🔍 [WARN] handleComplete - 没有可用的表单组件");
            }
          } catch (error) {
            console.error("🔍 [ERROR] handleComplete - 获取表单数据失败:", error);
          }
        } else {

        }

        // 补充 dataId（若存在）
        if (route.query && (route.query as any).dataId) {
          payload.dataId = (route.query as any).dataId
        }


        const res = await complete(payload)
        proxy?.$modal.msgSuccess(res.msg);
        goBack();
      } else {

      }
    });
  }
  /** 委派任务 */
  const handleDelegate = () => {
    taskFormRef.value?.validate(async (valid: boolean) => {
      if (valid) {
        userSelectType.value = 'delegate';
        userSelectDialog.title = '委派任务'
        userSelectDialog.visible = true;
        getTreeSelect();
      }
    });
  }
  /** 转办任务 */
  const handleTransfer = () => {
    taskFormRef.value?.validate(async (valid: boolean) => {
      if (valid) {
        userSelectType.value = 'transfer';
        userSelectDialog.title = '转办任务';
        userSelectDialog.visible = true;
        getTreeSelect();
      }
    });
  }
  /** 退回任务 */
  const handleReturn = async () => {
    // 校验表单
    taskFormRef.value.validate(async (valid: boolean) => {
      if (valid) {
        const res = await returnList(taskForm);
        returnTaskList.value = res.data;
        returnDialog.visible = true;
      }
    });
  }
  /** 驳回任务 */
  const handleReject = async () => {
    taskFormRef.value.validate(async (valid: boolean) => {
      if (valid) {
        await proxy?.$modal.confirm('驳回审批将退回到上一级审批，是否继续?');
        await rejectTask(taskForm);
        proxy?.$modal.msgSuccess("操作成功");
        goBack();
      }
    });
  }

  /** 返回页面 */
  const goBack = () => {
    // 关闭当前标签页并返回上个页面
    proxy?.$tab.closePage(route);
    router.back()
  }
  // 关闭标签
  const handleClose = (type: any, tag: any) => {

    let userObj = userMultipleSelection.value.find(item => item.userId === tag.id);
    userMultipleSelection.value.splice(userMultipleSelection.value.indexOf(userObj), 1);
    if (type === 'copy') {

      copyUser.value = userMultipleSelection.value;
      // 设置抄送人ID
      if (copyUser.value && copyUser.value.length > 0) {
        const val = copyUser.value.map(item => item.id);
        taskForm.copyUserIds = val instanceof Array ? val.join(',') : val;
      } else {
        taskForm.copyUserIds = '';
      }
    } else if (type === 'next') {
      nextUser.value = userMultipleSelection.value;
      // 设置抄送人ID
      if (nextUser.value && nextUser.value.length > 0) {
        const val = nextUser.value.map(item => item.id);
        taskForm.nextUserIds = val instanceof Array ? val.join(',') : val;
      } else {
        taskForm.nextUserIds = '';
      }
    } else if (type === 'addSign') {
      addSignUser.value = userMultipleSelection.value;
      // 设置抄送人ID
      if (nextUser.value && nextUser.value.length > 0) {
        const val = nextUser.value.map(item => item.id);
        taskForm.nextUserIds = val instanceof Array ? val.join(',') : val;
      } else {
        taskForm.nextUserIds = '';
      }
    }
  }

  function getRowKeys(row) {
    return row.userId  // 指定table id
  }

  const changeCurrentUser = (val: any) => {
    currentUserId.value = val?.userId
  }
  const handleSelectionChange = (selection) => {
    userMultipleSelection.value = selection
  }
  const toggleSelection = (selection) => {
    if (selection && selection.length > 0) {
      nextTick(()=> {
        selection.forEach(item => {
          let row = userList.value.find(k => k.userId === item.userId);

          if(row === undefined) return;
          userTableRef.value.toggleRowSelection(row);
        })
      })
    } else {
      nextTick(() => {
        userTableRef.value?.clearSelection();
      });
    }
  }
  const submitReturn = () => {
    // 校验表单
    taskFormRef.value.validate(async (valid: boolean) => {
      if (valid) {
        if (!returnTaskKey) {
          proxy?.$modal.msgError("请选择退回节点！");
        }
        taskForm.targetKey = returnTaskKey.value;
        const res = await returnTask(taskForm);
        proxy?.$modal.msgSuccess(res.msg);
        goBack()
      }
    });

  }
  const submitUserData = () => {
    let type = userSelectType.value;
    if (type === 'copy' || type === 'next'  || type === 'addSign') {

      if (!userMultipleSelection || userMultipleSelection.value.length <= 0) {
        proxy?.$modal.msgError("请选择用户");
        return false;
      }
      let userIds = userMultipleSelection.value.map(k => k.userId);

      if (type === 'copy') {
        // 设置抄送人ID信息
        copyUser.value = userMultipleSelection.value;
        taskForm.copyUserIds = userIds instanceof Array ? userIds.join(',') : userIds;
      } else if (type === 'next') {
        // 设置下一级审批人ID信息
        nextUser.value = userMultipleSelection.value;
        taskForm.nextUserIds = userIds instanceof Array ? userIds.join(',') : userIds;
      } else if (type === 'addSign') {
        // 设置加签审批人ID信息
        addSignUser.value = userMultipleSelection.value;
        taskForm.addSignUsers = userIds instanceof Array ? userIds.join(',') : userIds;
      }
      userSelectDialog.visible = false;
    } else {
      if (!taskForm.comment) {
        proxy?.$modal.msgError("请输入审批意见");
        return false;
      }
      if (!currentUserId.value) {
        proxy?.$modal.msgError("请选择用户");
        return false;
      }
      taskForm.userId = currentUserId.value;
      if (type === 'delegate') {
        delegate(taskForm).then(res => {
          proxy?.$modal.msgSuccess(res.msg);
          goBack();
        });
      }
      if (type === 'transfer') {
        transfer(taskForm).then(res => {
          proxy?.$modal.msgSuccess(res.msg);
          goBack();
        });
      }
    }
  }
  /** 拒绝任务 */
  const handleRefuse = () => {
    taskFormRef.value.validate(valid => {
      if (valid) {
        proxy?.$modal.confirm('拒绝审批单流程会终止，是否继续？').then(function() {
          return refuseTask(taskForm);
        }).then(res => {
          proxy?.$modal.msgSuccess(res.msg);
          goBack();
        });
      }
    });
  }
  /** 加签 */
  const handleAddSign = () => {
    taskFormRef.value.validate(valid => {
      if (valid) {
        addSignType.value = 0;
        addSignTitle.value = "前加签流程";
        addSignOpen.value = true;

      }
    });
  }
  const changeAddSignType = (val) => {
    addSignType.value = val;
    if(addSignType.value === 0) {
      addSignTitle.value = "前加签流程";
    }
    if(addSignType.value === 1) {
      addSignTitle.value = "后加签流程";
    }
    if(addSignType.value === 2) {
      addSignTitle.value = "多实例加签流程";
    }
  }
  /** 加签任务 */
  const addSignComplete = () => {
    addSignForm.value.addSignUsers = taskForm.addSignUsers;
    addSignForm.value.addSignType = addSignType.value
    if (!addSignForm.value.addSignUsers ) {
        proxy?.$modal.msgError("请选择用户");
        return;
    }
    // 流程信息
    addSignForm.value.taskId = route.query && route.query.taskId as string;;
    addSignForm.value.procInsId = route.params && route.params.procInsId as string;;

    //对VForm3后续加签审批的时候需要用到
    addSignForm.value.comment = taskForm.comment;


    if(addSignForm.value.addSignType === 2) {
      multiInstanceAddSignTask(addSignForm.value).then(response => {
      proxy?.$modal.msgSuccess(response.msg);
      addSignOpen.value = false;
      goBack();
      });
    }
    else {
      addSignTask(addSignForm.value).then(response => {
      proxy?.$modal.msgSuccess(response.msg);
      addSignOpen.value = false;
      goBack();
      });
    }
  }
  /** 跳转任务 */
  const handleJump = () => {
    taskFormRef.value.validate((valid: any)  => {
      if (valid) {
          jumpOpen.value = true;
          jumpTitle.value = "跳转流程";
          jumpNodeLoading.value = true
          userTaskList({ taskId: taskForm.taskId }).then((res) => {
          jumpNodeLoading.value = false
          jumpNodeData.value = res.data
         })
      }
    });
  }
  const jumpComplete = () => {
    if ( selectedJumpRows.value.length < 1 ) {
      proxy?.$modal.msgWarning('请选择跳转节点')
      return
    }
    // 流程信息
    jumpForm.taskId = route.query && route.query.taskId as string;;
    jumpForm.procInsId = route.params && route.params.procInsId as string;;
    //对formdesigner后续加签审批的时候需要用到
    jumpForm.comment = taskForm.comment;
    //目标选择的节点信息
    jumpForm.targetActId = selectedJumpRows.value[0].id;
    jumpForm.targetActName = selectedJumpRows.value[0].name;

    jumpTask(jumpForm).then(res => {
      if (res.code == 200) {
        proxy?.$modal.msgSuccess('跳转成功')
        jumpOpen.value = false;
        goBack();
      } else {
        proxy?.$modal.msgError('跳转失败：' + res.msg)
      }
    });
  }
  /**
   * 跳转节点列表选择
   */

  const rowSelection = {
    //selectedRowKeys: selectedRowKeys, //selectedRowKeys 可设置默认被选中项（就是表格一打开就被选中的项） 指定选中项的 key 数组，需要和 onChange 进行配合 string[]
    onChange: (selectedRowKeys: any, selectedRows: any) => {  // onChange 选中项发生变化时的回调
      selectedJumpRowKeys.value = selectedRowKeys;
      selectedJumpRows.value = selectedRows
    },
    type: "radio", // 单选
  };

  onBeforeMount(() => {
    const clientid = import.meta.env.VITE_APP_CLIENT_ID;
    nbcioHeader = { "Authorization":"Bearer " + getToken(),
                    "Clientid": clientid }
    initData();
  });

  const emptyFormContent = computed(() => JSON.stringify({ formConfig: {}, formComponents: [] }))

  const getFormContent = (item: any) => {
  const model = item?.formModel
  if (!model) return JSON.stringify({ formConfig: {}, formComponents: [] })
  const content = typeof model === 'string' ? model : JSON.stringify(model)
  return content
}

// 获取调整标签宽度后的表单内容（含子表数据）
const getFormContentWithWiderLabels = () => {
  if (!taskFormData.value?.formModel) return {};

  const formModel = typeof taskFormData.value.formModel === 'string'
    ? JSON.parse(taskFormData.value.formModel)
    : taskFormData.value.formModel;
  const cloned = JSON.parse(JSON.stringify(formModel || {}));

  // 不设置 labelWidth 和 labelPosition，让CSS来控制布局
  if (cloned.formConfig) {
    cloned.formConfig = { ...cloned.formConfig };
  }

  // 注入子表数据，与 getEditableFormContent 逻辑一致
  const comps = cloned.formComponents || cloned.widgetList || cloned.components || cloned.fields || [];
  const fd = taskFormData.value?.formData || {};
  const masterSubTableData: Record<string, any[]> = {};
  const subComp = Array.isArray(comps) ? comps.find((c: any) => c.type === 'subtable') : null;
  const subFieldFromModel = subComp?.field || '';
  if (Array.isArray(comps)) {
    comps.forEach((c: any) => {
      if (c.type === 'subtable' && c.field && Array.isArray(fd?.[c.field])) {
        masterSubTableData[c.field] = fd[c.field];
      }
    });
  }
  if (!Object.keys(masterSubTableData).length && fd?.sub?.data && Array.isArray(fd.sub.data) && subFieldFromModel) {
    masterSubTableData[subFieldFromModel] = fd.sub.data;
  }
  if (Object.keys(masterSubTableData).length > 0) {
    cloned.masterSubTableData = masterSubTableData;
  }

  return cloned;
};

// 可编辑展示用：仅注入子表数据，不禁用字段
const getEditableFormContent = (item: any) => {
  try {
    const rawModel = typeof item?.formModel === 'string' ? JSON.parse(item.formModel) : (item?.formModel || {})
    const cloned = JSON.parse(JSON.stringify(rawModel || {}))
    const comps = cloned.formComponents || cloned.widgetList || cloned.components || cloned.fields || []

    // 移除 formConfig 中的标签配置，让 FormRender 组件的 label-width 属性来控制
    if (cloned.formConfig) {
      cloned.formConfig = {
        ...cloned.formConfig,
        // 移除 labelWidth 和 labelPosition，让组件属性来控制
      }
    }

    const masterSubTableData: Record<string, any[]> = {}
    const subComp = Array.isArray(comps) ? comps.find((c: any) => c.type === 'subtable') : null
    const subFieldFromModel = subComp?.field || ''
    const fd = item?.formData || {}
    if (Array.isArray(comps)) {
      comps.forEach((c: any) => {
        if (c.type === 'subtable' && c.field && Array.isArray(fd?.[c.field])) {
          masterSubTableData[c.field] = fd[c.field]
        }
      })
    }
    if ((!Object.keys(masterSubTableData).length) && fd?.sub?.data && Array.isArray(fd.sub.data) && subFieldFromModel) {
      masterSubTableData[subFieldFromModel] = fd.sub.data
    }
    if (Object.keys(masterSubTableData).length > 0) {
      cloned.masterSubTableData = masterSubTableData
    }
    return JSON.stringify(cloned)
  } catch (e) {
    console.warn('getEditableFormContent 失败，回退为原模型:', e)
    return getFormContent(item)
  }
}

// 只读展示用：禁用全部字段，并将子表数据嵌入 masterSubTableData
const getReadonlyFormContent = (item: any) => {
  try {
    const rawModel = typeof item?.formModel === 'string' ? JSON.parse(item.formModel) : (item?.formModel || {})
    const cloned = JSON.parse(JSON.stringify(rawModel || {}))
    const comps = cloned.formComponents || cloned.widgetList || cloned.components || cloned.fields || []

    // 移除 formConfig 中的标签配置，让 FormRender 组件的 label-width 属性来控制
    if (cloned.formConfig) {
      cloned.formConfig = {
        ...cloned.formConfig,
        // 移除 labelWidth 和 labelPosition，让组件属性来控制
      }
    }

    // 禁用主表与子表字段
    comps.forEach((c: any) => {
      c.props = c.props || {}
      c.props.disabled = true
      if (c.type === 'subtable' && Array.isArray(c.subTableColumns)) {
        c.subTableColumns.forEach((col: any) => {
          col.props = col.props || {}
          col.props.disabled = true
        })
      }
    })
    // 收集子表数据，兼容两种数据结构
    const masterSubTableData: Record<string, any[]> = {}
    const subComp = Array.isArray(comps) ? comps.find((c: any) => c.type === 'subtable') : null
    const subFieldFromModel = subComp?.field || ''
    const fd = item?.formData || {}
    if (Array.isArray(comps)) {
      comps.forEach((c: any) => {
        if (c.type === 'subtable' && c.field && Array.isArray(fd?.[c.field])) {
          masterSubTableData[c.field] = fd[c.field]
        }
      })
    }
    // 结构为 { main, sub: { tableName, data } }
    if ((!Object.keys(masterSubTableData).length) && fd?.sub?.data && Array.isArray(fd.sub.data) && subFieldFromModel) {
      masterSubTableData[subFieldFromModel] = fd.sub.data
    }
    if (Object.keys(masterSubTableData).length > 0) {
      cloned.masterSubTableData = masterSubTableData
    }
    const result = JSON.stringify(cloned)
    return result
  } catch (e) {
    console.warn('getReadonlyFormContent 失败，回退为原模型:', e)
    return getFormContent(item)
  }
}

// 仅返回主表初始数据（过滤掉子表数组）
const getInitialMainData = (item: any) => {
  const data = item?.formData || {}
  // 兼容 main/sub 结构
  if (data && typeof data === 'object' && data.main) {
    return { ...data.main }
  }
  const main: Record<string, any> = {}
  Object.keys(data).forEach(k => {
    const v = (data as any)[k]
    if (!Array.isArray(v)) main[k] = v
  })
  return main
}

</script>

<style lang="scss" scoped>
  .clearfix:before,
  .clearfix:after {
    display: table;
    content: "";
  }
  .clearfix:after {
    clear: both
  }

  .box-card {
    width: 100%;
    margin-bottom: 20px;
  }

  .el-tag + .el-tag {
    margin-left: 10px;
  }

  .el-row {
    margin-bottom: 20px;
    &:last-child {
      margin-bottom: 0;
    }
  }
  .el-col {
    border-radius: 4px;
  }

  .button-new-tag {
    margin-left: 10px;
  }

  /* 修复表单标签和输入框的布局 */
  .form-container :deep(.el-form-item) {
    margin-bottom: 20px !important;
    display: flex !important;
    align-items: flex-start !important;
  }

  .form-container :deep(.el-form-item__label) {
    white-space: nowrap !important;
    overflow: visible !important;
    text-overflow: ellipsis !important;
    width: 140px !important;
    min-width: 140px !important;
    max-width: 140px !important;
    text-align: right !important;
    padding-right: 12px !important;
    flex-shrink: 0 !important; /* 防止标签被压缩 */
  }

  .form-container :deep(.el-form-item__content) {
    margin-left: 0 !important;
    flex: 1 !important;
    min-width: 0 !important; /* 允许内容区域收缩 */
  }
</style>
