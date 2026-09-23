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
              <!--
                懒加载：全量树有 5112 个节点（≈1.18MB），el-tree 不做虚拟滚动，一次性灌进去要建 5000+ 个
                TreeNode 并渲染成 DOM，弹窗会卡住好几秒。这里改成按需逐级拉取（根只有"顶级节点"一个，
                展开一级才发一次 ~6KB 的请求），并用 disabled 阻止选中自己或自己的下级（防环）。
              -->
              <el-tree-select
                v-model="form.parentAreaId"
                lazy
                :load="loadParentOptions"
                :cache-data="parentCacheData"
                :props="areaTreeProps"
                value-key="areaId"
                placeholder="选择上级地区"
                check-strictly
                @node-click="onParentNodeClick"
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
import { ref, reactive, toRefs, onMounted, getCurrentInstance } from 'vue';
import { listArea, getSubtreeAreaIds, getArea, delArea, addArea, updateArea, AreaVO, AreaQuery, AreaForm } from '@/api/system/area';

const { proxy } = getCurrentInstance();
const { sys_area_level } = toRefs<any>(proxy.useDict('sys_area_level'));

/**
 * el-tree-select 字段映射。
 * 声明为常量对象而不是模板内联字面量：Element Plus 的 TreeOptionProps 未声明 value 字段，
 * 内联字面量会触发"对象字面量的多余属性检查"，此前只能靠 `as any` 绕过。
 * <p>{@code isLeaf} / {@code disabled} 未在此声明，走 el-tree-select 的默认字段名（同名）。</p>
 */
const areaTreeProps = { value: 'areaId', label: 'areaName', children: 'children' };

/**
 * el-tree-select 懒加载节点：在 AreaVO 之上补两个树组件专用字段。
 * <p>{@code isLeaf} 决定是否显示展开箭头，{@code disabled} 用于防环。</p>
 */
type AreaTreeNode = AreaVO & { isLeaf?: boolean; disabled?: boolean };

const areaList = ref<AreaVO[]>([]);
const open = ref(false);
const loading = ref(false);
const showSearch = ref(true);
const title = ref('');
/** 表格重挂载标记：懒加载模式下 Element Plus 会缓存已展开节点的子数据，列表刷新后必须重挂载表格，否则改名/新增/删除不会反映到已展开的节点上 */
const tableKey = ref(0);
const queryRef = ref<ElFormInstance>();
const areaRef = ref<ElFormInstance>();
/**
 * 上级下拉的回显数据（el-tree-select 的 cache-data）。
 * 懒加载模式下树里只有"展开过"的节点，选中值所在节点不在树里时，组件拿不到 label 就只能显示数字代码，
 * 必须显式把已选上级喂进去。
 */
const parentCacheData = ref<AreaVO[]>([]);
/**
 * 防环禁用集合：待编辑节点整棵子树的 areaId（字符串形式，避免 number/string 比较踩坑）。
 * 懒加载时命中该集合的节点会被置为 disabled，用户就选不到自己或自己的下级。
 */
const excludedAreaIds = ref<Set<string>>(new Set());

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
      // 只做宽松的数字校验，不锁定 6~9 位：
      //   - 存量数据含 3 位异常码（如 102 经济技术开发区）
      //   - sys_area_level 的 5 级「社区/村」用 12 位码（如 330102001051）
      //   - 后端 SysDicAreaBo 对 areaId 只有 @NotNull，此处若更严会把真实数据锁死无法保存
      { pattern: /^\d{1,12}$/, message: '地区代码应为 1~12 位数字', trigger: 'blur' }
    ],
    sort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
    layered: [{ required: true, message: '层级不能为空', trigger: 'change' }],
    // 与后端 SysDicAreaBo 的 @Pattern 保持一致：允许空串（存量数据中有 lat/lng 为空串的行）
    lat: [{ pattern: /^$|^-?\d{1,3}(\.\d+)?$/, message: '纬度格式不正确', trigger: 'blur' }],
    lng: [{ pattern: /^$|^-?\d{1,3}(\.\d+)?$/, message: '经度格式不正确', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/**
 * 给表格行打展开标记。
 * <p>优先用后端回填的 {@code hasChildren}（扁平/懒加载路径下是查库得到的真实值）；后端未给时
 * 才回退到"层级 < 5"的启发式——库里 5112 行只有 470 行真有下级，纯靠猜会多出 4640 个假箭头。</p>
 */
function withHasChildren(rows: AreaVO[]): AreaVO[] {
  return rows
    .filter((item) => item !== null && item !== undefined)
    .map((item) => ({
      ...item,
      hasChildren: item.hasChildren ?? (item.layered ?? 0) < 5
    }));
}

function getList() {
  loading.value = true;
  const query = queryParams.value;
  // 首屏只需要第一级（34 个省级）：无参调用会让后端返回整棵 5112 节点树（≈1.18MB、服务端 153ms），
  // 而 Element Plus 懒加载表只渲染根节点，其余 5078 个节点白下载、白解析、白占内存。
  // 显式带上 parentAreaId/layered 后后端走 idx_parent_area_id 返回扁平列表（≈6.8KB、~3ms）。
  // 只有按名称/层级筛选时才需要整树。
  const params: AreaQuery = query.areaName || query.layered ? query : { parentAreaId: 0, layered: 1 };
  listArea(params)
    .then((response) => {
      areaList.value = response.data && Array.isArray(response.data) ? withHasChildren(response.data) : [];
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
      resolve(withHasChildren(response.data ?? []));
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
 * 把接口返回的一级行政区划转成 el-tree-select 的懒加载节点。
 * <p>{@code isLeaf} 用后端回填的 {@code hasChildren}（缺失时才回退到层级启发式）：懒加载树靠它决定
 * 是否渲染展开箭头，标错会出现"点开是空"或"有下级却展不开"。</p>
 * <p>{@code disabled} 用防环集合：编辑时该节点整棵子树都不可选，避免把上级设成自己或自己的下级。</p>
 */
function toParentNodes(rows: AreaVO[], fallbackLayered: number): AreaTreeNode[] {
  return rows
    .filter((item) => item !== null && item !== undefined)
    .map((item) => {
      const layered = Number(item.layered ?? fallbackLayered);
      return {
        ...item,
        layered,
        isLeaf: !(item.hasChildren ?? layered < 5),
        disabled: excludedAreaIds.value.has(String(item.areaId))
      };
    });
}

/**
 * "上级地区"下拉的懒加载回调。
 * <p>根只给一个"顶级节点"（areaId=0），展开后才逐级按 parentAreaId 拉取——旧实现一次性把 5112 个
 * 节点灌进 el-tree-select，光建 TreeNode + 渲染 DOM 就要好几秒。</p>
 * <p>顶级节点必须可表示：它是把存量省级行（parent_area_id 为悬空码）修正为 parentAreaId=0 的唯一入口。</p>
 */
function loadParentOptions(node: { level: number; data: AreaTreeNode }, resolve: (rows: AreaTreeNode[]) => void) {
  if (node.level === 0) {
    resolve([{ areaId: 0, areaName: '顶级节点', layered: 0, hasChildren: true, isLeaf: false } as AreaTreeNode]);
    return;
  }
  const parent = node.data;
  const layered = Number(parent.layered ?? 0) + 1;
  listArea({ parentAreaId: parent.areaId, layered })
    .then((response) => resolve(toParentNodes(response.data ?? [], layered)))
    .catch(() => {
      proxy.$modal.msgError('获取上级地区列表失败');
      resolve([]);
    });
}

/**
 * 选中上级节点后推导层级 = 上级层级 + 1（后端强校验该不变量）。
 * <p><b>为什么监听 node-click 而不是 watch(parentAreaId)</b>：懒加载下树里只有展开过的节点，
 * 旧实现那套"在整棵树里找父节点再 +1"的做法已经拿不到数据；而 node-click 直接把被点击节点的
 * {@code layered} 交给我们，最准也最省事。</p>
 * <p>点 disabled 节点不会改变选中值（el-tree-select 内部会拦），此时不能改层级，否则会与
 * parentAreaId 对不上。</p>
 */
function onParentNodeClick(data: AreaTreeNode) {
  if (data.disabled) {
    return;
  }
  form.value.layered = Number(data.layered ?? 0) + 1;
}

/** 每次打开弹窗前重置懒加载下拉的状态（禁用集合与回显数据都只属于当前这次编辑） */
function resetParentPicker() {
  parentCacheData.value = [];
  excludedAreaIds.value = new Set();
}

/** 新增：不传 row 即新增顶级节点（parentAreaId=0、layered=1） */
function handleAdd(row?: AreaVO) {
  reset();
  resetParentPicker();
  form.value.parentAreaId = row?.areaId ?? 0;
  open.value = true;
  title.value = '添加行政区划';
  // 新增的节点还没有子树，无需禁用任何节点。
  // 层级直接由被点击行的层级推导（懒加载下树里还没加载过该节点）：顶级节点固定 1，
  // 其余 = 上级层级 + 1 —— 后端强校验该不变量。
  const parentLayered = row?.layered;
  form.value.layered = parentLayered === undefined || parentLayered === null ? 1 : Number(parentLayered) + 1;
}

function handleUpdate(row: AreaVO) {
  reset();
  resetParentPicker();
  title.value = '修改行政区划';
  // 防环：取该节点整棵子树的 areaId，懒加载时把这些节点置为 disabled（旧实现是返回"排除子树后的整棵树"，
  // 为了防环每次都要传 1.18MB；现在只传一串 id，且每次打开弹窗只调一次）
  getSubtreeAreaIds(row.areaId)
    .then((response) => {
      excludedAreaIds.value = new Set((response.data ?? []).map((id) => String(id)));
    })
    .catch(() => {
      // 取不到禁用集合时不阻断编辑，交由后端 validate() 兜底（会明确报"上级不能是自己或自己的下级"）
      proxy.$modal.msgError('获取可选的上级地区失败，提交时仍会校验上下级关系');
    });
  // 详情按 pkid（主键）查；防环用的是 areaId（行政区划代码），两个 ID 用途不同
  getArea(row.pkid).then((response) => {
    form.value = response.data;
    if (form.value.layered !== undefined && form.value.layered !== null) {
      form.value.layered = Number(form.value.layered);
    }
    open.value = true;
    // 懒加载模式下树里没有"已选上级"这个节点，必须用 cache-data 提供名称，否则只显示数字代码
    loadParentCacheData(form.value.parentAreaId);
  });
}

/** 拉取已选上级的名称，供 el-tree-select 在懒加载模式下回显 */
function loadParentCacheData(parentAreaId?: string | number) {
  if (parentAreaId === undefined || parentAreaId === null) {
    return;
  }
  if (Number(parentAreaId) === 0) {
    parentCacheData.value = [{ areaId: 0, areaName: '顶级节点', layered: 0 } as AreaVO];
    return;
  }
  listArea({ areaId: parentAreaId })
    .then((response) => {
      const parent = (response.data ?? []).find((item) => String(item.areaId) === String(parentAreaId));
      if (parent) {
        parentCacheData.value = [parent];
      }
    })
    .catch(() => {
      // 回显失败只影响显示名称，不影响提交；保持数字代码可见即可
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
