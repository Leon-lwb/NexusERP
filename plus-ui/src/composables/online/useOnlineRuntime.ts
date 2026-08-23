import { dayjs } from 'element-plus';

type AnyObj = Record<string, any>;

export type OnCodeGenerateConfig = {
  autoGenerateRule?: string;
  generateParams?: {
    format?: string;
    [k: string]: any;
  };
};

export type OnSubChangeConfig = {
  formula?: string;
  targetFields?: string[] | string;
  triggerCondition?: string;
};

export type OnMainChangeConfig = {
  formula?: string;
  targetFields?: string[] | string;
  triggerCondition?: string;
};

export type WhenClause = {
  all?: Array<{
    field: string;
    op: 'eq' | 'ne' | 'gt' | 'ge' | 'lt' | 'le' | 'in' | 'notIn' | 'empty' | 'notEmpty' | 'contains';
    value?: any;
  }>;
  any?: Array<{
    field: string;
    op: 'eq' | 'ne' | 'gt' | 'ge' | 'lt' | 'le' | 'in' | 'notIn' | 'empty' | 'notEmpty' | 'contains';
    value?: any;
  }>;
};

export type ToggleRule = {
  when?: WhenClause;
  targets: string[] | string;
  value: boolean;
  elseValue?: boolean;
  clearOnFalse?: boolean;
  priority?: number;
};

export type DictCascadeRule = {
  when?: WhenClause;
  targets: string[] | string;
  /** source field, e.g. "province_id" */
  from: string;
  /** option field name to match source value, default: "parentId" */
  optionMatchField?: string;
  /** when source is empty: "none" => [], "all" => keep all */
  emptyMode?: 'none' | 'all';
  /** clear target value if it is not in filtered options */
  clearOnChange?: boolean;
  priority?: number;
};

export type PopupQueryParam = {
  key: string;
  mode: 'const' | 'copy' | 'formula';
  value?: any;
  from?: string;
  formula?: string;
};

export type PopupQueryRule = {
  when?: WhenClause;
  targets: string[] | string;
  /** either a plain object, or a param-list (recommended) */
  params: Record<string, any> | PopupQueryParam[];
  /** merge into existing params of same target (default true) */
  merge?: boolean;
  priority?: number;
};

export type ClearRule = {
  when?: WhenClause;
  targets: string[] | string;
  priority?: number;
};

export type SetValueRule = {
  when?: WhenClause;
  targets: string[] | string;
  mode: 'const' | 'copy' | 'formula';
  value?: any;
  from?: string;
  formula?: string;
  overwrite?: boolean;
  priority?: number;
};

export type ValidateRule = {
  when?: WhenClause;
  type: 'regex' | 'range' | 'compare' | 'in' | 'notIn';
  field?: string;
  fields?: string[];
  message: string;
  params?: Record<string, any>;
  priority?: number;
};

export type UiState = {
  visible: Record<string, boolean>;
  disabled: Record<string, boolean>;
  required: Record<string, boolean>;
};

export type OnSubTriggerConfig = {
  triggerType?: 'aggregate' | 'sum' | 'count' | 'average' | 'max' | 'min';
  formula?: string;
  targetFields?: string[] | string;
};

export type OnChangeConfig = {
  onCodeGenerate?: OnCodeGenerateConfig;
  onMainChange?: OnMainChangeConfig;
  onSubChange?: OnSubChangeConfig;
  onSubTrigger?: OnSubTriggerConfig;
  // Extended (compatible) rules
  onVisible?: ToggleRule[];
  onDisable?: ToggleRule[];
  onRequired?: ToggleRule[];
  onClear?: ClearRule[];
  onSetValue?: SetValueRule[];
  onValidate?: ValidateRule[];
  onDictCascade?: DictCascadeRule[];
  onPopupQuery?: PopupQueryRule[];
  // Sub-row rules (for sub table runtime)
  onRowAdd?: SetValueRule[];
  onRowValidate?: ValidateRule[];
  [k: string]: any;
};

export const safeParseJSON = <T = AnyObj>(input: any, fallback: T = {} as T): T => {
  if (input == null) return fallback;
  if (typeof input === 'object') return input as T;
  if (typeof input !== 'string') return fallback;
  try {
    return JSON.parse(input.replace(/[""]/g, '"').replace(/'/g, '"').replace(/\\/g, '\\\\')) as T;
  } catch {
    return fallback;
  }
};

const isEmpty = (v: any) => v === null || v === undefined || v === '';

const getHighResTimestamp = () => Date.now().toString();

export const generateBusinessNo = (config: OnCodeGenerateConfig): string => {
  const autoGenerateRule = config?.autoGenerateRule || '';
  const generateParams = config?.generateParams || {};

  if (!autoGenerateRule) return '';

  let result = autoGenerateRule;
  if (result.includes('{timestamp}')) {
    const timestamp = generateParams.format ? dayjs().format(generateParams.format) : getHighResTimestamp();
    result = result.replace(/{timestamp}/g, timestamp);
  }
  return result;
};

/**
 * Auto-fill fields configured with onCodeGenerate when empty.
 */
export const initAutoGenerateForForm = (fields: any[], form: AnyObj) => {
  if (!Array.isArray(fields) || !form) return;
  fields.forEach((f: any) => {
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    const genCfg = cfg?.onCodeGenerate;
    if (!genCfg?.autoGenerateRule) return;
    const key = f?.columnName;
    if (!key) return;
    if (isEmpty(form[key])) form[key] = generateBusinessNo(genCfg);
  });
};

/**
 * Merge component-emitted commonDataInfo into target form/row.
 * - NmInput emits { [columnName]: value }
 * - NmPopup emits mapped payload (may include both camelCase + snake_case keys)
 */
export const applyCommonDataInfo = (nm: any, target: AnyObj) => {
  if (!target || !nm) return;
  const info = nm?.commonDataInfo;
  if (info && typeof info === 'object') {
    Object.assign(target, info);
    return;
  }
  // Some components emit primitive commonDataInfo; treat it as the current field value.
  if (nm?.columnName && info !== undefined) {
    target[nm.columnName] = info;
  }
};

const normalizeTargetFields = (targetFields: any): string[] => {
  if (Array.isArray(targetFields)) return targetFields.filter(Boolean);
  if (typeof targetFields === 'string') {
    // allow comma-separated string
    return targetFields
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }
  return [];
};

const normalizeTargets = (targets: any): string[] => normalizeTargetFields(targets);

const toNumber = (v: any) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};

const resolveFieldValue = (ctx: AnyObj, field: string) => {
  if (!field) return undefined;
  // allow main.xxx / sub.xxx like paths
  if (field.includes('.')) {
    const parts = field.split('.');
    let cur: any = ctx;
    for (const p of parts) {
      if (cur == null) return undefined;
      cur = cur[p];
    }
    return cur;
  }
  return ctx?.[field];
};

const evalWhen = (when: WhenClause | undefined, ctx: AnyObj): boolean => {
  if (!when) return true;
  const evalCond = (c: any): boolean => {
    const left = resolveFieldValue(ctx, c.field);
    const op = c.op;
    const right = c.value;
    switch (op) {
      case 'empty':
        return isEmpty(left);
      case 'notEmpty':
        return !isEmpty(left);
      case 'eq':
        return String(left ?? '') === String(right ?? '');
      case 'ne':
        return String(left ?? '') !== String(right ?? '');
      case 'gt':
        return toNumber(left) > toNumber(right);
      case 'ge':
        return toNumber(left) >= toNumber(right);
      case 'lt':
        return toNumber(left) < toNumber(right);
      case 'le':
        return toNumber(left) <= toNumber(right);
      case 'in':
        return Array.isArray(right) ? right.map(String).includes(String(left ?? '')) : false;
      case 'notIn':
        return Array.isArray(right) ? !right.map(String).includes(String(left ?? '')) : true;
      case 'contains':
        return String(left ?? '').includes(String(right ?? ''));
      default:
        return false;
    }
  };
  if (Array.isArray(when.all) && when.all.length) {
    if (!when.all.every((c) => evalCond(c))) return false;
  }
  if (Array.isArray(when.any) && when.any.length) {
    if (!when.any.some((c) => evalCond(c))) return false;
  }
  return true;
};

const sortByPriority = <T extends { priority?: number }>(arr: T[]) =>
  arr
    .map((v, idx) => ({ v, idx }))
    .sort((a, b) => {
      const pa = a.v.priority ?? 0;
      const pb = b.v.priority ?? 0;
      if (pa !== pb) return pa - pb; // smaller first, bigger wins (later)
      return a.idx - b.idx;
    })
    .map((it) => it.v);

/**
 * Evaluate a formula containing placeholders like {{price}} using values from ctx.
 * To reduce injection risk, we replace placeholders with numbers first and then only allow
 * numeric operators in the final expression.
 */
const evalNumericFormula = (formula: string, ctx: AnyObj): number => {
  if (!formula) return 0;
  const expr = String(formula).replace(/\{\{(\w+)\}\}/g, (_, key) => {
    return String(toNumber(ctx?.[key]));
  });
  // only allow numbers/operators/whitespace after replacement
  if (!/^[0-9+\-*/().\s]+$/.test(expr)) return 0;
  try {
    const fn = new Function(`return (${expr})`);
    const res = fn();
    return toNumber(res);
  } catch {
    return 0;
  }
};

const evalCondition = (condition: string, ctx: AnyObj): boolean => {
  if (!condition) return true;
  const expr = String(condition).replace(/\{\{(\w+)\}\}/g, (_, key) => {
    return String(toNumber(ctx?.[key]));
  });
  if (!/^[0-9+\-*/().\s><=!&|]+$/.test(expr)) return false;
  try {
    const fn = new Function(`return (${expr})`);
    return Boolean(fn());
  } catch {
    return false;
  }
};

/**
 * Apply onMainChange rule when a main field changes.
 */
export const applyOnMainChange = (field: any, mainForm: AnyObj, newValue?: any) => {
  if (!field?.onChange || !mainForm) return;
  const cfg = safeParseJSON<OnChangeConfig>(field.onChange, {} as any);
  const mainCfg = cfg?.onMainChange;
  if (!mainCfg?.formula) return;

  const ctx = { ...mainForm, newValue: newValue ?? mainForm?.[field?.columnName] };
  if (mainCfg.triggerCondition && !evalCondition(mainCfg.triggerCondition, ctx)) return;

  const targets = normalizeTargetFields(mainCfg.targetFields);
  if (!targets.length) return;

  const value = evalNumericFormula(String(mainCfg.formula || ''), ctx);
  targets.forEach((t) => {
    mainForm[t] = value;
  });
};

/**
 * Apply extended clear/setValue rules for main/sub scope when a field changes.
 */
export const applyOnClear = (field: any, ctx: AnyObj, setValue: (k: string, v: any) => void) => {
  const cfg = safeParseJSON<OnChangeConfig>(field?.onChange, {} as any);
  const rules = Array.isArray(cfg.onClear) ? sortByPriority(cfg.onClear) : [];
  rules.forEach((r) => {
    if (!r) return;
    if (!evalWhen(r.when, ctx)) return;
    normalizeTargets(r.targets).forEach((t) => setValue(t, null));
  });
};

export const applyOnSetValue = (field: any, ctx: AnyObj, setValue: (k: string, v: any) => void) => {
  const cfg = safeParseJSON<OnChangeConfig>(field?.onChange, {} as any);
  const rules = Array.isArray(cfg.onSetValue) ? sortByPriority(cfg.onSetValue) : [];
  rules.forEach((r) => {
    if (!r) return;
    if (!evalWhen(r.when, ctx)) return;
    const overwrite = r.overwrite !== false;
    normalizeTargets(r.targets).forEach((t) => {
      const cur = ctx?.[t];
      if (!overwrite && !isEmpty(cur)) return;
      let next: any = null;
      if (r.mode === 'const') next = r.value;
      else if (r.mode === 'copy') next = r.from ? resolveFieldValue(ctx, r.from) : null;
      else if (r.mode === 'formula') next = evalNumericFormula(String(r.formula || ''), ctx);
      setValue(t, next);
    });
  });
};

/**
 * Apply sub-row defaults when adding a new row.
 * Rule source: each sub field's `onChange.onRowAdd` (compatible extension).
 */
export const applyOnRowAdd = (subFields: any[], row: AnyObj, mainForm: AnyObj, workflow?: AnyObj) => {
  if (!Array.isArray(subFields) || !row) return;
  subFields.forEach((f: any) => {
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    const rules = Array.isArray(cfg.onRowAdd) ? sortByPriority(cfg.onRowAdd) : [];
    rules.forEach((r) => {
      if (!r) return;
      const ctx = { ...(mainForm || {}), ...(row || {}), workflow: workflow || {} };
      if (!evalWhen(r.when, ctx)) return;
      const overwrite = r.overwrite !== false;
      const targets = normalizeTargets((r as any).targets ?? f?.columnName);
      targets.forEach((t) => {
        const cur = row?.[t];
        if (!overwrite && !isEmpty(cur)) return;
        let next: any = null;
        if (r.mode === 'const') next = r.value;
        else if (r.mode === 'copy') next = r.from ? resolveFieldValue(ctx, r.from) : null;
        else if (r.mode === 'formula') next = evalNumericFormula(String(r.formula || ''), ctx);
        row[t] = next;
      });
    });
  });
};

/**
 * Compute UI state (visible/disabled/required) from extended rules.
 * Last matching rule wins (by priority then array order).
 */
export const computeUiState = (fields: any[], ctx: AnyObj): UiState => {
  const state: UiState = { visible: {}, disabled: {}, required: {} };
  if (!Array.isArray(fields)) return state;

  // default values
  fields.forEach((f: any) => {
    const k = f?.columnName;
    if (!k) return;
    state.visible[k] = true;
    state.disabled[k] = false;
    state.required[k] = f?.isRequired === '1';
  });

  const applyToggles = (rules: ToggleRule[] | undefined, target: Record<string, boolean>) => {
    const arr = Array.isArray(rules) ? sortByPriority(rules) : [];
    arr.forEach((r) => {
      if (!r) return;
      const hit = evalWhen(r.when, ctx);
      // elseValue omitted => keep previous (no-op)
      const shouldApply = hit || r.elseValue !== undefined;
      if (!shouldApply) return;
      const val = hit ? r.value : r.elseValue;
      const targets = normalizeTargets(r.targets);
      targets.forEach((t) => {
        if (typeof val === 'boolean') target[t] = val;
      });
    });
  };

  // apply per-field configs (rules may target other fields)
  fields.forEach((f: any) => {
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    applyToggles(cfg.onVisible, state.visible);
    applyToggles(cfg.onDisable, state.disabled);
    applyToggles(cfg.onRequired, state.required);
  });

  return state;
};

export type UiEffects = {
  /** fields to clear because of `onVisible[].clearOnFalse` */
  clearFields: string[];
};

/**
 * Like computeUiState, but also returns "effects" (e.g. clear hidden fields).
 * Rule behavior:
 * - last matching rule wins (by priority then array order)
 * - if a rule sets visible=false with clearOnFalse=true, we suggest clearing targets
 */
export const computeUiStateWithEffects = (fields: any[], ctx: AnyObj): { ui: UiState; effects: UiEffects } => {
  const ui = computeUiState(fields, ctx);
  const wantClear: Record<string, boolean> = {};

  const applyVisibleEffects = (rules: ToggleRule[] | undefined) => {
    const arr = Array.isArray(rules) ? sortByPriority(rules) : [];
    arr.forEach((r) => {
      if (!r) return;
      const hit = evalWhen(r.when, ctx);
      if (!hit) return;
      const targets = normalizeTargets(r.targets);
      targets.forEach((t) => {
        if (r.value === false && r.clearOnFalse) wantClear[t] = true;
        if (r.value === true) wantClear[t] = false;
      });
    });
  };

  fields.forEach((f: any) => {
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    applyVisibleEffects(cfg.onVisible);
  });

  const clearFields = Object.keys(wantClear).filter((k) => wantClear[k] && ui.visible[k] === false);
  return { ui, effects: { clearFields } };
};

export type ValidateError = { field: string; message: string };

export const validateBySchema = (fields: any[], ctx: AnyObj, ui: UiState): ValidateError[] => {
  const errors: ValidateError[] = [];
  const visible = ui?.visible || {};
  const disabled = ui?.disabled || {};
  const required = ui?.required || {};

  const checkRequired = (fieldName: string, label: string) => {
    if (visible[fieldName] === false) return;
    if (disabled[fieldName] === true) return;
    if (required[fieldName] !== true) return;
    const v = ctx?.[fieldName];
    if (isEmpty(v)) errors.push({ field: fieldName, message: `${label}不能为空` });
  };

  fields.forEach((f: any) => {
    const name = f?.columnName;
    const label = f?.columnComment || name;
    if (!name) return;
    checkRequired(name, label);

    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    // onRowValidate is a compatible alias for "row-scope validations" (main ignores it naturally)
    const rawRules: any[] = [
      ...(Array.isArray(cfg.onValidate) ? cfg.onValidate : []),
      ...(Array.isArray(cfg.onRowValidate) ? cfg.onRowValidate : [])
    ];
    const rules = rawRules.length ? sortByPriority(rawRules as any) : [];
    rules.forEach((r) => {
      if (!r) return;
      if (!evalWhen(r.when, ctx)) return;
      const msg = r.message || `${label}校验失败`;
      const type = r.type;
      const field = r.field || name;
      const v = ctx?.[field];
      if (type === 'regex') {
        const pattern = String(r.params?.pattern || '');
        if (!pattern) return;
        try {
          const re = new RegExp(pattern);
          if (!re.test(String(v ?? ''))) errors.push({ field, message: msg });
        } catch {
          // ignore bad regex at runtime; backend should block it
        }
      } else if (type === 'range') {
        const min = r.params?.min;
        const max = r.params?.max;
        const n = toNumber(v);
        if (min != null && n < Number(min)) errors.push({ field, message: msg });
        if (max != null && n > Number(max)) errors.push({ field, message: msg });
      } else if (type === 'in' || type === 'notIn') {
        const list = Array.isArray(r.params?.list) ? r.params.list.map(String) : [];
        const hit = list.includes(String(v ?? ''));
        if (type === 'in' && !hit) errors.push({ field, message: msg });
        if (type === 'notIn' && hit) errors.push({ field, message: msg });
      } else if (type === 'compare') {
        const leftField = String(r.params?.left || field);
        const rightField = String(r.params?.right || '');
        const op = String(r.params?.op || 'eq');
        const leftVal = resolveFieldValue(ctx, leftField);
        const rightVal = resolveFieldValue(ctx, rightField);
        let ok = true;
        if (op === 'eq') ok = String(leftVal ?? '') === String(rightVal ?? '');
        else if (op === 'ne') ok = String(leftVal ?? '') !== String(rightVal ?? '');
        else if (op === 'gt') ok = toNumber(leftVal) > toNumber(rightVal);
        else if (op === 'ge') ok = toNumber(leftVal) >= toNumber(rightVal);
        else if (op === 'lt') ok = toNumber(leftVal) < toNumber(rightVal);
        else if (op === 'le') ok = toNumber(leftVal) <= toNumber(rightVal);
        if (!ok) errors.push({ field: leftField, message: msg });
      }
    });
  });

  return errors;
};

export type RuntimeLinkageState = {
  dictOptions: Record<string, any[]>;
  popupParams: Record<string, Record<string, any>>;
  clearFields: string[];
};

/**
 * Compute "runtime linkage state" for a form scope:
 * - dict cascade: returns per-target filtered options list (for select/radio)
 * - popup query: returns per-target query params (for NmPopup list API)
 *
 * Notes:
 * - currently designed for one scope (main OR one sub-row)
 * - rules live on any field, targets refer to field.columnName in the same scope
 */
export const computeRuntimeLinkageState = (fields: any[], ctx: AnyObj): RuntimeLinkageState => {
  const state: RuntimeLinkageState = { dictOptions: {}, popupParams: {}, clearFields: [] };
  if (!Array.isArray(fields)) return state;

  const fieldByName = new Map<string, any>();
  fields.forEach((f: any) => {
    if (f?.columnName) fieldByName.set(String(f.columnName), f);
  });

  const applyDictCascade = (rules: DictCascadeRule[] | undefined) => {
    const arr = Array.isArray(rules) ? sortByPriority(rules) : [];
    arr.forEach((r) => {
      if (!r) return;
      if (!evalWhen(r.when, ctx)) return;
      const srcVal = resolveFieldValue(ctx, r.from);
      const matchField = r.optionMatchField || 'parentId';
      const emptyMode = r.emptyMode || 'none';
      normalizeTargets(r.targets).forEach((t) => {
        const targetField = fieldByName.get(t);
        const base = Array.isArray(targetField?.commonDictCode) ? targetField.commonDictCode : [];
        let out: any[] = base;
        if (isEmpty(srcVal)) {
          out = emptyMode === 'all' ? base : [];
        } else {
          out = base.filter((opt: any) => String(opt?.[matchField] ?? '') === String(srcVal));
        }
        state.dictOptions[t] = out;
        if (r.clearOnChange) {
          const allowed = new Set(out.map((it: any) => String(it?.id ?? it?.value ?? '')));
          const cur = resolveFieldValue(ctx, t);
          if (!isEmpty(cur) && !allowed.has(String(cur))) state.clearFields.push(t);
        }
      });
    });
  };

  const evalPopupParams = (params: Record<string, any> | PopupQueryParam[]) => {
    if (Array.isArray(params)) {
      const out: Record<string, any> = {};
      params.forEach((p) => {
        if (!p?.key) return;
        if (p.mode === 'const') out[p.key] = p.value;
        else if (p.mode === 'copy') out[p.key] = p.from ? resolveFieldValue(ctx, p.from) : null;
        else if (p.mode === 'formula') out[p.key] = evalNumericFormula(String(p.formula || ''), ctx);
      });
      return out;
    }
    return params && typeof params === 'object' ? params : {};
  };

  const applyPopupQuery = (rules: PopupQueryRule[] | undefined) => {
    const arr = Array.isArray(rules) ? sortByPriority(rules) : [];
    arr.forEach((r) => {
      if (!r) return;
      if (!evalWhen(r.when, ctx)) return;
      const merge = r.merge !== false;
      const p = evalPopupParams(r.params);
      normalizeTargets(r.targets).forEach((t) => {
        state.popupParams[t] = merge ? { ...(state.popupParams[t] || {}), ...p } : { ...p };
      });
    });
  };

  fields.forEach((f: any) => {
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    applyDictCascade(cfg.onDictCascade);
    applyPopupQuery(cfg.onPopupQuery);
  });

  // de-dup clearFields (preserve order)
  const seen = new Set<string>();
  state.clearFields = state.clearFields.filter((k) => (seen.has(k) ? false : (seen.add(k), true)));
  return state;
};

/**
 * Apply onSubChange rule to a single sub row when a field changes.
 */
export const applyOnSubChange = (field: any, row: AnyObj, mainForm: AnyObj) => {
  if (!field?.onChange || !row) return;
  const cfg = safeParseJSON<OnChangeConfig>(field.onChange, {} as any);
  const subCfg = cfg?.onSubChange;
  if (!subCfg?.formula) return;

  if (subCfg.triggerCondition && !evalCondition(subCfg.triggerCondition, { ...row, ...mainForm })) return;

  const targets = normalizeTargetFields(subCfg.targetFields);
  if (!targets.length) return;

  const value = evalNumericFormula(subCfg.formula, { ...row, ...mainForm });
  targets.forEach((t) => {
    row[t] = value;
  });
};

/**
 * Apply onSubTrigger rules defined on main fields (aggregate from sub rows to main form).
 * Convention: the rule lives on the target main field's onChange config.
 */
export const applyOnSubTrigger = (mainFields: any[], subRows: AnyObj[], mainForm: AnyObj) => {
  if (!Array.isArray(mainFields) || !Array.isArray(subRows) || !mainForm) return;

  mainFields.forEach((f: any) => {
    const defaultTarget = f?.columnName;
    if (!defaultTarget) return;
    const cfg = safeParseJSON<OnChangeConfig>(f?.onChange, {} as any);
    const trig = cfg?.onSubTrigger;
    if (!trig?.formula) return;

    const type = trig.triggerType || 'aggregate';
    const formula = String(trig.formula || '');

    const nums = subRows.map((r) => evalNumericFormula(formula, r));

    let out = 0;
    switch (type) {
      case 'count':
        out = subRows.length;
        break;
      case 'max':
        out = nums.length ? Math.max(...nums) : 0;
        break;
      case 'min':
        out = nums.length ? Math.min(...nums) : 0;
        break;
      case 'average':
        out = nums.length ? nums.reduce((a, b) => a + b, 0) / nums.length : 0;
        break;
      case 'sum':
      case 'aggregate':
      default:
        out = nums.reduce((a, b) => a + b, 0);
        break;
    }

    const targets = normalizeTargetFields(trig.targetFields);
    const effectiveTargets = targets.length ? targets : [defaultTarget];
    effectiveTargets.forEach((t) => {
      mainForm[t] = Number.isFinite(out) ? Number(out.toFixed(2)) : 0;
    });
  });
};

/**
 * High-level helpers for "preview == OnlineTest" behavior.
 * Keep execution order consistent:
 * 1) onClear -> 2) onSetValue -> 3) formula blocks (onMainChange/onSubChange)
 */
export const handleMainToMain = (changedField: any, newValue: any, mainForm: AnyObj, workflowCtx: AnyObj = {}) => {
  if (!changedField || !mainForm) return;
  const ctx0 = { ...(mainForm || {}), workflow: workflowCtx };
  applyOnClear(changedField, ctx0, (k, v) => {
    (mainForm as any)[k] = v;
  });
  applyOnSetValue(changedField, { ...(mainForm || {}), workflow: workflowCtx }, (k, v) => {
    (mainForm as any)[k] = v;
  });
  applyOnMainChange(changedField, mainForm, newValue);
};

export const handleSubToSub = (changedField: any, _newValue: any, row: AnyObj, mainForm: AnyObj, workflowCtx: AnyObj = {}) => {
  if (!changedField || !row) return;
  const ctx0 = { ...(mainForm || {}), ...(row || {}), workflow: workflowCtx };
  applyOnClear(changedField, ctx0, (k, v) => {
    (row as any)[k] = v;
  });
  applyOnSetValue(changedField, { ...(mainForm || {}), ...(row || {}), workflow: workflowCtx }, (k, v) => {
    (row as any)[k] = v;
  });
  applyOnSubChange(changedField, row, mainForm);
};

export const useOnlineRuntime = () => {
  return {
    safeParseJSON,
    generateBusinessNo,
    initAutoGenerateForForm,
    applyCommonDataInfo,
    applyOnMainChange,
    applyOnClear,
    applyOnSetValue,
    handleMainToMain,
    handleSubToSub,
    applyOnRowAdd,
    computeUiState,
    computeUiStateWithEffects,
    validateBySchema,
    computeRuntimeLinkageState,
    applyOnSubChange,
    applyOnSubTrigger
  };
};
