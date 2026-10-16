import type { EngineeringParameters, EngineeringTemplate } from '@/api/ai/engineering';

export interface EngineeringDraftForm {
  boardWidthMm: string;
  boardHeightMm: string;
  layers?: number;
  optionalModules: string[];
}
/** 只比较输入和契约边界，不将工程尺寸转成浮点数。 */
function compareDecimal(left: string, right: string | number): number {
  const parse = (value: string) => {
    if (!/^-?\d+(\.\d+)?$/.test(value) || value.length > 128) throw new Error('模板尺寸约束无效，请联系管理员核对');
    const negative = value.startsWith('-');
    const [integer, fraction = ''] = (negative ? value.slice(1) : value).split('.');
    return { integer, fraction, sign: negative ? -1n : 1n };
  };
  const a = parse(left);
  const b = parse(String(right));
  const precision = Math.max(a.fraction.length, b.fraction.length);
  const first = a.sign * BigInt(a.integer + a.fraction.padEnd(precision, '0'));
  const second = b.sign * BigInt(b.integer + b.fraction.padEnd(precision, '0'));
  return first < second ? -1 : first > second ? 1 : 0;
}
/** 模板枚举收窄全局协议允许层数，不扩大服务端白名单。 */
export function templateLayerOptions(template?: EngineeringTemplate): number[] {
  const rule = template?.parameters?.layers;
  const values = rule?.enumValues?.length ? rule.enumValues : [1, 2, 4];
  return [...new Set(values)].filter(
    (value) =>
      [1, 2, 4].includes(value) &&
      (rule?.min == null || compareDecimal(String(value), rule.min) >= 0) &&
      (rule?.max == null || compareDecimal(String(value), rule.max) <= 0)
  );
}
/** 默认值只来自选定模板，切换模板清空上次可选模块。 */
export function templateDraftDefaults(template?: EngineeringTemplate): EngineeringDraftForm {
  const text = (key: string): string => {
    const value = template?.parameters?.[key]?.defaultValue;
    return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  };
  const layers = template?.parameters?.layers?.defaultValue;
  return {
    boardWidthMm: text('boardWidthMm'),
    boardHeightMm: text('boardHeightMm'),
    layers: typeof layers === 'number' && templateLayerOptions(template).includes(layers) ? layers : undefined,
    optionalModules: []
  };
}
/** 用户确认前验证模板边界，尺寸字符串及其精度原样提交。 */
export function validateDraftParameters(template: EngineeringTemplate, form: EngineeringDraftForm): EngineeringParameters {
  for (const [key, label] of [
    ['boardWidthMm', '板宽'],
    ['boardHeightMm', '板高']
  ] as const) {
    const value = form[key];
    if (!/^\d{1,4}(\.\d{1,4})?$/.test(value)) throw new Error(`请明确填写${label}，使用最多四位小数的十进制文本`);
    if (compareDecimal(value, '0') <= 0 || compareDecimal(value, '1000') > 0) throw new Error(`${label}必须大于 0 且不超过 1000 mm`);
    const rule = template.parameters?.[key];
    if (rule?.min != null && compareDecimal(value, rule.min) < 0) throw new Error(`${label}不能小于模板下限 ${rule.min} mm`);
    if (rule?.max != null && compareDecimal(value, rule.max) > 0) throw new Error(`${label}不能大于模板上限 ${rule.max} mm`);
  }
  if (!form.layers || !templateLayerOptions(template).includes(form.layers)) throw new Error('板层不在当前模板允许范围内，请重新选择');
  if (form.optionalModules.some((module) => !template.optionalModules?.includes(module))) throw new Error('可选模块不在当前模板允许范围内');
  return {
    boardWidthMm: form.boardWidthMm,
    boardHeightMm: form.boardHeightMm,
    layers: form.layers,
    ...(form.optionalModules.length ? { optionalModules: [...form.optionalModules] } : {})
  };
}
