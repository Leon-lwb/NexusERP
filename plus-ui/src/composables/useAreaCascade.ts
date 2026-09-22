/**
 * 省 / 市 / 区县 / 街道 四级联动 Composable
 * <p>
 * 抽取自 ERP 客户 / 供应商 / 仓库三个页面中重复的行政区划级联逻辑，
 * 统一处理选项加载、下级清空、地址拼接与编辑态逐级回显。
 *
 * @author nexus
 */
import { ref, type Ref } from 'vue';
import { getProvinces, getNextLevel, type AreaVO } from '@/api/system/area';

/** 地址表单中与行政区划相关的字段 */
export interface AreaAddressForm {
  provinceAreaId?: string | number | null;
  cityAreaId?: string | number | null;
  districtAreaId?: string | number | null;
  streetId?: string | number | null;
  address?: string;
  [key: string]: any;
}

/** 需要加载子级选项的目标层级 */
type Level = 'city' | 'district' | 'street';

/** 发生变更的层级（用于清空其下级的选项与选中值） */
type ChangeLevel = 'province' | 'city' | 'district';

/** useAreaCascade 的可选项 */
export interface UseAreaCascadeOptions {
  /**
   * 拼接结果写入表单的哪个字段，默认 `address`。
   * 仓库页 wm/warehouse 用的是 `location`（WarehouseVO.location），
   * 若沿用默认值会导致该页地址不再回填。
   */
  addressField?: string;
}

/**
 * 省 / 市 / 区县 / 街道 四级联动。
 *
 * 关键点：后端 provinceAreaId/cityAreaId/districtAreaId 是 Long（JSON 数字），
 * 而 streetId 是 Java String（JSON 字符串）。因此一律用 String(id) 比较，
 * 避免 `number === string` 恒为 false 导致街道名退化成数字代码。
 *
 * @param form 表单 ref，需含 provinceAreaId / cityAreaId / districtAreaId / streetId 及目标地址字段
 * @param options 可选项，见 {@link UseAreaCascadeOptions}
 * @returns    各级选项与级联事件处理方法
 */
export function useAreaCascade(form: Ref<AreaAddressForm>, options: UseAreaCascadeOptions = {}) {
  const addressField = options.addressField ?? 'address';
  const provinceOptions = ref<AreaVO[]>([]);
  const cityOptions = ref<AreaVO[]>([]);
  const districtOptions = ref<AreaVO[]>([]);
  const streetOptions = ref<AreaVO[]>([]);

  /** 统一按字符串比较 ID，规避后端 Long/String 混用 */
  const sameId = (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b);

  /** 在选项列表中按 ID 取名称，未命中返回空串 */
  const nameOf = (list: AreaVO[], id: unknown) => list.find((option) => sameId(option.areaId, id))?.areaName ?? '';

  /** 加载省份选项 */
  async function loadProvinces() {
    provinceOptions.value = (await getProvinces()).data ?? [];
  }

  /** 加载指定层级的子级选项 */
  async function loadChildren(level: Level, parentId: string | number) {
    const list = (await getNextLevel(parentId)).data ?? [];
    if (level === 'city') cityOptions.value = list;
    else if (level === 'district') districtOptions.value = list;
    else streetOptions.value = list;
  }

  /** 清空指定层级以下的选项与选中值 */
  function resetBelow(level: ChangeLevel) {
    if (level === 'province') {
      form.value.cityAreaId = null;
      cityOptions.value = [];
    }
    if (level === 'province' || level === 'city') {
      form.value.districtAreaId = null;
      districtOptions.value = [];
    }
    form.value.streetId = null;
    streetOptions.value = [];
  }

  /** 按已选层级拼接地址（未选中的层级自动跳过） */
  function updateAddress() {
    const parts = [
      nameOf(provinceOptions.value, form.value.provinceAreaId),
      nameOf(cityOptions.value, form.value.cityAreaId),
      nameOf(districtOptions.value, form.value.districtAreaId),
      nameOf(streetOptions.value, form.value.streetId)
    ].filter((part) => part !== '');
    form.value[addressField] = parts.join('');
  }

  /** 省份变更：清空市/区县/街道并加载城市选项 */
  async function onProvinceChange(value: string | number | null) {
    resetBelow('province');
    // 子级加载失败也必须回填地址，否则会残留上一次的拼接结果
    try {
      if (value) await loadChildren('city', value);
    } finally {
      updateAddress();
    }
  }

  /** 城市变更：清空区县/街道并加载区县选项 */
  async function onCityChange(value: string | number | null) {
    resetBelow('city');
    try {
      if (value) await loadChildren('district', value);
    } finally {
      updateAddress();
    }
  }

  /** 区县变更：清空街道并加载街道选项 */
  async function onDistrictChange(value: string | number | null) {
    resetBelow('district');
    try {
      if (value) await loadChildren('street', value);
    } finally {
      updateAddress();
    }
  }

  /** 街道变更：仅重算地址 */
  function onStreetChange() {
    updateAddress();
  }

  /** 编辑/查看态逐级回显 */
  async function loadAddressCascade() {
    await loadProvinces();
    if (!form.value.provinceAreaId) return;
    await loadChildren('city', form.value.provinceAreaId);
    if (!form.value.cityAreaId) return;
    await loadChildren('district', form.value.cityAreaId);
    if (!form.value.districtAreaId) return;
    await loadChildren('street', form.value.districtAreaId);

    // 街道可能因数据缺失而不在下拉中，补一个显式占位项，避免显示成裸数字代码
    const streetId = form.value.streetId;
    const districtId = form.value.districtAreaId;
    if (streetId && !streetOptions.value.some((option) => sameId(option.areaId, streetId))) {
      streetOptions.value.push({
        areaId: streetId,
        areaName: `已选街道（${streetId}）`,
        parentAreaId: districtId
      });
    }
  }

  return {
    provinceOptions,
    cityOptions,
    districtOptions,
    streetOptions,
    loadProvinces,
    loadAddressCascade,
    onProvinceChange,
    onCityChange,
    onDistrictChange,
    onStreetChange,
    updateAddress
  };
}
