<template>
  <div>
    <template v-for="(item, index) in options">
      <template v-if="isValueMatch(item.value)">
        <span
          v-if="(String(item.elTagType) === 'default' || String(item.elTagType) === '') && (item.elTagClass === '' || item.elTagClass == null)"
          :key="item.value"
          :index="index"
          :class="item.elTagClass"
        >
          {{ item.label + ' ' }}
        </span>
        <el-tag
          v-else
          :key="item.value + ''"
          :disable-transitions="true"
          :index="index"
          :type="
            item.elTagType === 'primary' ||
            item.elTagType === 'success' ||
            item.elTagType === 'info' ||
            item.elTagType === 'warning' ||
            item.elTagType === 'danger'
              ? item.elTagType
              : 'primary'
          "
          :class="item.elTagClass"
        >
          {{ item.label + ' ' }}
        </el-tag>
      </template>
    </template>
    <template v-if="unmatch && showValue">
      {{ unmatchArray }}
    </template>
  </div>
</template>

<script setup lang="ts">
interface Props {
  options: Array<DictDataOption>;
  /** 缺失或空状态保持原有空白显示，不代表已检验或正常；编号字符串不转为数值。 */
  value?: number | string | Array<number | string> | null;
  showValue?: boolean;
  separator?: string;
}
const props = withDefaults(defineProps<Props>(), {
  showValue: true,
  separator: ','
});

const values = computed(() => {
  if (props.value === '' || props.value === null || typeof props.value === 'undefined') return [];
  if (typeof props.value === 'number' || typeof props.value === 'boolean') return [props.value];
  return Array.isArray(props.value) ? props.value.map((item) => '' + item) : String(props.value).split(props.separator);
});

const unmatch = computed(() => {
  if (props.options?.length == 0 || props.value === '' || props.value === null || typeof props.value === 'undefined') return false;
  /** 传入值为非数组 */
  let unmatch = false; /** 判断是否存在未匹配项。 */
  values.value.forEach((item) => {
    if (!props.options.some((v) => v.value == item)) {
      unmatch = true; /** 发现未匹配项时设置标志。 */
    }
  });
  return unmatch; /** 返回未匹配标志。 */
});

const unmatchArray = computed(() => {
  /** 记录未匹配的项 */
  const itemUnmatchArray: Array<string | number> = [];
  if (props.value !== '' && props.value !== null && typeof props.value !== 'undefined') {
    values.value.forEach((item) => {
      if (!props.options.some((v) => v.value === item)) {
        itemUnmatchArray.push(item);
      }
    });
  }
  /** 缺少字典值时不显示。 */
  return handleArray(itemUnmatchArray);
});

const handleArray = (array: Array<string | number>) => {
  if (array.length === 0) return '';
  return array.reduce((pre, cur) => {
    return pre + ' ' + cur;
  });
};

const isValueMatch = (itemValue: any) => {
  return values.value.some((val) => val == itemValue);
};
</script>

<style lang="scss" scoped>
.el-tag + .el-tag {
  margin-left: 10px;
}
</style>
