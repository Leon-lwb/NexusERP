<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="地区名称" prop="areaName">
        <el-input v-model="queryParams.areaName" placeholder="请输入地区名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="层级" prop="layered">
        <el-select v-model="queryParams.layered" placeholder="全部层级" clearable style="width: 160px">
          <el-option v-for="dict in sys_area_level" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:area:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:area:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
    </el-row>

    <el-table
      :key="tableKey"
      v-loading="loading"
      :data="areaList"
      row-key="areaId"
      lazy
      :load="loadChildren"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="areaName" label="行政区划名称" width="260"></el-table-column>
      <el-table-column prop="areaId" label="行政区划代码" width="200"></el-table-column>
      <el-table-column prop="layered" label="层级" width="100">
        <template #default="scope">
          <dict-tag :options="sys_area_level" :value="scope.row.layered" />
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="100"></el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="200">
        <template #default="scope">
          <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="300" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:area:edit']">修改</el-button>
          <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['system:area:add']">新增</el-button>
          <el-button
            v-if="scope.row.parentAreaId !== 0"
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:area:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="open" :title="title" width="640px" append-to-body>
      <el-form ref="areaRef" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="上级地区" prop="parentAreaId">
              <el-tree-select
                v-model="form.parentAreaId"
                :data="areaOptions"
                :props="areaTreeProps"
                value-key="areaId"
                placeholder="选择上级地区"
                check-strictly
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入地区名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区代码" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入地区代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="sort">
              <el-input-number v-model="form.sort" controls-position="right" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层级" prop="layered">
              <el-select v-model="form.layered" placeholder="请选择层级" clearable>
                <el-option v-for="dict in sys_area_level" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="lat">
              <el-input v-model="form.lat" placeholder="请输入纬度" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" prop="lng">
              <el-input v-model="form.lng" placeholder="请输入经度" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入地址" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Area">
import { ref, reactive, toRefs, watch, onMounted, getCurrentInstance } from 'vue';
import { listArea, listAreaExcludeChild, getArea, delArea, addArea, updateArea, AreaVO, AreaQuery, AreaForm } from '@/api/system/area';

const { proxy } = getCurrentInstance();
const { sys_area_level } = toRefs<any>(proxy.useDict('sys_area_level'));

/**
 * el-tree-select 字段映射。
 * 声明为常量对象而不是模板内联字面量：Element Plus 的 TreeOptionProps 未声明 value 字段，
 * 内联字面量会触发"对象字面量的多余属性检查"，此前只能靠 `as any` 绕过。
 */
const areaTreeProps = { value: 'areaId', label: 'areaName', children: 'children' };

const areaList = ref<AreaVO[]>([]);
const open = ref(false);
const loading = ref(false);
const showSearch = ref(true);
const title = ref('');
const areaOptions = ref<AreaVO[]>([]);
/** 表格重挂载标记：懒加载模式下 Element Plus 会缓存已展开节点的子数据，列表刷新后必须重挂载表格，否则改名/新增/删除不会反映到已展开的节点上 */
const tableKey = ref(0);
const queryRef = ref<ElFormInstance>();
const areaRef = ref<ElFormInstance>();

const data = reactive({
  form: {} as AreaForm,
  queryParams: {
    areaName: undefined,
    // 不能给 1：那样页面只显示省级且搜不到市/区（后端按 layered 精确过滤）
    layered: undefined
  } as AreaQuery,
  rules: {
    parentAreaId: [{ required: true, message: '上级地区不能为空', trigger: 'change' }],
    areaName: [{ required: true, message: '地区名称不能为空', trigger: 'blur' }],
    areaId: [
      { required: true, message: '地区代码不能为空', trigger: 'blur' },
      { pattern: /^\d{6,9}$/, message: '地区代码应为 6~9 位数字', trigger: 'blur' }
    ],
    sort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
    layered: [{ required: true, message: '层级不能为空', trigger: 'change' }],
    // 与后端 SysDicAreaBo 的 @Pattern 保持一致：允许空串（存量数据中有 lat/lng 为空串的行）
    lat: [{ pattern: /^$|^-?\d{1,3}(\.\d+)?$/, message: '纬度格式不正确', trigger: 'blur' }],
    lng: [{ pattern: /^$|^-?\d{1,3}(\.\d+)?$/, message: '经度格式不正确', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listArea(queryParams.value)
    .then((response) => {
      areaList.value = [];
      if (response.data && Array.isArray(response.data)) {
        const filteredData = response.data.filter((item) => item !== null && item !== undefined);
        // 懒加载模式下 Element Plus 只按 hasChildren 决定是否渲染展开箭头；统一按"层级 < 5"打标
        // （与 sys_area_level 字典一致，1~5），子节点交给 loadChildren 逐级拉取。
        areaList.value = filteredData.map((item) => ({
          ...item,
          hasChildren: item.layered < 5
        }));
      }
      loading.value = false;
      // 重挂载表格以丢弃懒加载缓存，保证增删改后已展开节点显示最新数据（旧实现是重置 expandedRowKeys 收起全部）
      tableKey.value += 1;
    })
    .catch(() => {
      proxy.$modal.msgError('获取数据失败');
      loading.value = false;
    });
}

/** el-table 懒加载：展开某行时拉取其直接下级（带 parentAreaId 时后端返回扁平列表，不再嵌套 children） */
function loadChildren(row: AreaVO, _treeNode: unknown, resolve: (rows: AreaVO[]) => void) {
  listArea({ parentAreaId: row.areaId, layered: (row.layered ?? 0) + 1 })
    .then((response) => {
      const rows = (response.data ?? []).map((item) => ({
        ...item,
        hasChildren: (item.layered ?? 0) < 5
      }));
      resolve(rows);
    })
    .catch(() => {
      proxy.$modal.msgError('加载下级失败');
      resolve([]);
    });
}

function handleQuery() {
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  queryParams.value = { areaName: undefined, layered: undefined };
  getList();
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    pkid: undefined,
    areaId: undefined,
    parentAreaId: undefined,
    areaName: undefined,
    lat: undefined,
    lng: undefined,
    layered: undefined,
    sort: 0,
    address: undefined
  };
  proxy.resetForm('areaRef');
}

/**
 * 构建"上级地区"下拉数据。
 * <p>不传参时调 listArea()（无任何条件 → 后端返回完整树，可挂到市/区/街道下）；
 * 传 areaId 时调 listAreaExcludeChild(areaId) 排除该节点及其整棵子树，避免把上级选成自己或自己的下级。</p>
 * <p><b>必须传 areaId（行政区划代码，如 330100），不能传 pkid</b>：后端按 area_id 匹配，
 * 传 pkid 会匹配不到 → 静默返回全树 → 防环失效；详情接口 getArea 才是按 pkid 查。</p>
 */
function buildAreaOptions(excludeAreaId?: string | number): Promise<void> {
  const request = excludeAreaId !== undefined && excludeAreaId !== null ? listAreaExcludeChild(excludeAreaId) : listArea();
  return request
    .then((response) => {
      const rows = (response.data ?? []).filter((item) => item !== null && item !== undefined);
      // 顶级节点（areaId=0）必须可表示：存量 34 个省级行的 parent_area_id=156（节点不存在），
      // 只有选中"顶级节点"才能把它们修正为合法的 parentAreaId=0 + layered=1。
      areaOptions.value = [{ areaId: 0, areaName: '顶级节点', children: rows }];
    })
    .catch(() => {
      areaOptions.value = [{ areaId: 0, areaName: '顶级节点', children: [] }];
      proxy.$modal.msgError('获取上级地区列表失败');
    });
}

/** 在上级地区树中按 areaId 查找节点（areaId 可能是数字或字符串，统一按字符串比较） */
function findInTree(nodes: AreaVO[], areaId?: string | number): AreaVO | undefined {
  for (const node of nodes) {
    if (String(node.areaId) === String(areaId)) return node;
    const hit = node.children ? findInTree(node.children, areaId) : undefined;
    if (hit) return hit;
  }
  return undefined;
}

/** 按当前选中的上级推导层级：顶级节点固定为 1，其余为上级层级 + 1（后端强校验该不变量） */
function syncLayeredFromParent() {
  const parentAreaId = form.value.parentAreaId;
  if (parentAreaId === 0 || parentAreaId === null || parentAreaId === undefined) {
    form.value.layered = 1;
    return;
  }
  const parent = findInTree(areaOptions.value, parentAreaId);
  if (parent?.layered !== undefined && parent?.layered !== null) {
    form.value.layered = Number(parent.layered) + 1;
  }
}

// 选中上级后自动带出层级 = 上级层级 + 1
watch(() => form.value.parentAreaId, syncLayeredFromParent);

/** 新增：不传 row 即新增顶级节点（parentAreaId=0、layered=1） */
async function handleAdd(row?: AreaVO) {
  reset();
  form.value.parentAreaId = row?.areaId ?? 0;
  open.value = true;
  title.value = '添加行政区划';
  // 上级下拉是异步加载的：首次触发 watch 时 areaOptions 还是空的，加载完成后再推导一次层级
  await buildAreaOptions();
  syncLayeredFromParent();
}

function handleUpdate(row: AreaVO) {
  reset();
  // 排除自身及子树用 areaId（行政区划代码），详情用 pkid（主键）——两个 ID 用途不同
  buildAreaOptions(row.areaId);
  getArea(row.pkid).then((response) => {
    form.value = response.data;
    if (form.value.layered !== undefined && form.value.layered !== null) {
      form.value.layered = Number(form.value.layered);
    }
    open.value = true;
    title.value = '修改行政区划';
  });
}

function submitForm() {
  areaRef.value?.validate((valid) => {
    if (valid) {
      if (form.value.pkid !== undefined && form.value.pkid !== null) {
        updateArea(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addArea(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row: AreaVO) {
  proxy.$modal
    .confirm('是否确认删除名称为"' + row.areaName + '"的数据项?')
    .then(function () {
      return delArea(row.pkid);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download('system/area/export', { ...queryParams.value }, `行政区划_${new Date().getTime()}.xlsx`);
}

onMounted(() => {
  getList();
});
</script>
