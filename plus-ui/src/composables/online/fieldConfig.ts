export type ValidateResult = { ok: true } | { ok: false; errors: string[] };

const IDENTIFIER_RE = /^[A-Za-z_][A-Za-z0-9_]*$/;
const PATH_RE = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/;

const isIdentifier = (s: any) => typeof s === 'string' && IDENTIFIER_RE.test(s);
const isPath = (s: any) => typeof s === 'string' && PATH_RE.test(s);

const parseObject = (json: string): any => {
  if (!json || !json.trim()) return {};
  const obj = JSON.parse(json);
  if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) {
    throw new Error('必须是 JSON 对象');
  }
  return obj;
};

export const validateDictFields = (json: string): ValidateResult => {
  try {
    const obj = parseObject(json);
    const fields = obj && typeof obj.fields === 'object' && !Array.isArray(obj.fields) ? obj.fields : obj;
    const errors: string[] = [];
    Object.entries(fields).forEach(([k, v]) => {
      if (!isIdentifier(k)) errors.push(`dictFields 字段名非法: ${k}`);
      if (typeof v !== 'string') errors.push(`dictFields[${k}] 标题必须是字符串`);
    });

    // compatible extensions
    validateToggleRules(obj.onVisible, errors, 'onVisible');
    validateToggleRules(obj.onDisable, errors, 'onDisable');
    validateToggleRules(obj.onRequired, errors, 'onRequired');
    validateClearRules(obj.onClear, errors, 'onClear');
    validateSetValueRules(obj.onSetValue, errors, 'onSetValue');
    validateValidateRules(obj.onValidate, errors, 'onValidate');
    validateDictCascadeRules(obj.onDictCascade, errors, 'onDictCascade');
    validatePopupQueryRules(obj.onPopupQuery, errors, 'onPopupQuery');
    validateSetValueRules(obj.onRowAdd, errors, 'onRowAdd');
    validateValidateRules(obj.onRowValidate, errors, 'onRowValidate');

    return errors.length ? { ok: false, errors } : { ok: true };
  } catch (e: any) {
    return { ok: false, errors: [`dictFields JSON格式错误: ${e?.message || String(e)}`] };
  }
};

export const validateMapping = (json: string): ValidateResult => {
  try {
    const obj = parseObject(json);
    const mapping = obj && typeof obj.mapping === 'object' && !Array.isArray(obj.mapping) ? obj.mapping : obj;
    const errors: string[] = [];
    Object.entries(mapping).forEach(([k, v]) => {
      if (k === 'popupConfig') return;
      if (!isIdentifier(k)) errors.push(`mapping source 字段名非法: ${k}`);
      if (typeof v !== 'string' || !isIdentifier(v)) errors.push(`mapping target 字段名非法: ${String(v)}`);
    });

    if (obj?.popupConfig != null) {
      const cfg = obj.popupConfig;
      if (typeof cfg !== 'object' || Array.isArray(cfg)) {
        errors.push('popupConfig 必须是对象');
      } else {
        if (cfg.displayMode != null && !['label', 'value', 'sub'].includes(cfg.displayMode)) {
          errors.push(`popupConfig.displayMode 非法: ${String(cfg.displayMode)}`);
        }
        if (cfg.multiple != null && typeof cfg.multiple !== 'boolean') {
          errors.push('popupConfig.multiple 必须是 boolean');
        }
        if (cfg.keyField != null && cfg.keyField !== '' && !isIdentifier(cfg.keyField)) {
          errors.push(`popupConfig.keyField 非法: ${String(cfg.keyField)}`);
        }
        if (cfg.displayField != null && cfg.displayField !== '' && !isIdentifier(cfg.displayField)) {
          errors.push(`popupConfig.displayField 非法: ${String(cfg.displayField)}`);
        }
      }
    }

    // compatible extensions
    validateToggleRules(obj.onVisible, errors, 'onVisible');
    validateToggleRules(obj.onDisable, errors, 'onDisable');
    validateToggleRules(obj.onRequired, errors, 'onRequired');
    validateClearRules(obj.onClear, errors, 'onClear');
    validateSetValueRules(obj.onSetValue, errors, 'onSetValue');
    validateValidateRules(obj.onValidate, errors, 'onValidate');
    validateDictCascadeRules(obj.onDictCascade, errors, 'onDictCascade');
    validatePopupQueryRules(obj.onPopupQuery, errors, 'onPopupQuery');
    validateSetValueRules(obj.onRowAdd, errors, 'onRowAdd');
    validateValidateRules(obj.onRowValidate, errors, 'onRowValidate');

    return errors.length ? { ok: false, errors } : { ok: true };
  } catch (e: any) {
    return { ok: false, errors: [`mapping JSON格式错误: ${e?.message || String(e)}`] };
  }
};

const validateTargetFields = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (typeof v === 'string') {
    if (v && !isIdentifier(v)) errors.push(`${path} 非法字段名: ${v}`);
    return;
  }
  if (Array.isArray(v)) {
    v.forEach((it) => {
      if (typeof it !== 'string' || !isIdentifier(it)) errors.push(`${path} 数组元素必须是合法字段名字符串`);
    });
    return;
  }
  errors.push(`${path} 必须是 string 或 string[]`);
};

const validateWhenClause = (when: any, errors: string[], path: string) => {
  if (when == null) return;
  if (typeof when !== 'object' || Array.isArray(when)) {
    errors.push(`${path} ?????`);
    return;
  }

  const validateCond = (c: any, cpath: string) => {
    if (c == null || typeof c !== 'object' || Array.isArray(c)) {
      errors.push(`${cpath} ?????`);
      return;
    }
    if (!isPath(c.field)) errors.push(`${cpath}.field ??: ${String(c.field)}`);
    const ops = ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'in', 'notIn', 'empty', 'notEmpty', 'contains'];
    if (!ops.includes(c.op)) errors.push(`${cpath}.op ??: ${String(c.op)}`);
    if ((c.op === 'in' || c.op === 'notIn') && c.value != null && !Array.isArray(c.value)) {
      errors.push(`${cpath}.value ??????in/notIn?`);
    }
  };

  if (when.all != null) {
    if (!Array.isArray(when.all)) errors.push(`${path}.all ?????`);
    else when.all.forEach((c: any, i: number) => validateCond(c, `${path}.all[${i}]`));
  }
  if (when.any != null) {
    if (!Array.isArray(when.any)) errors.push(`${path}.any ?????`);
    else when.any.forEach((c: any, i: number) => validateCond(c, `${path}.any[${i}]`));
  }
};

const validateToggleRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    validateTargetFields(r.targets, errors, `${p}.targets`);
    if (typeof r.value !== 'boolean') errors.push(`${p}.value ??? boolean`);
    if (r.elseValue != null && typeof r.elseValue !== 'boolean') errors.push(`${p}.elseValue ??? boolean`);
    if (r.clearOnFalse != null && typeof r.clearOnFalse !== 'boolean') errors.push(`${p}.clearOnFalse ??? boolean`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

const validateClearRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    validateTargetFields(r.targets, errors, `${p}.targets`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

const validateSetValueRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    validateTargetFields(r.targets, errors, `${p}.targets`);
    if (!['const', 'copy', 'formula'].includes(r.mode)) errors.push(`${p}.mode ??: ${String(r.mode)}`);
    if (r.mode === 'copy' && r.from != null && !isPath(r.from)) errors.push(`${p}.from ??: ${String(r.from)}`);
    if (r.mode === 'formula' && r.formula != null && typeof r.formula !== 'string') errors.push(`${p}.formula ??? string`);
    if (r.overwrite != null && typeof r.overwrite !== 'boolean') errors.push(`${p}.overwrite ??? boolean`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

const validateValidateRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    if (!['regex', 'range', 'compare', 'in', 'notIn'].includes(r.type)) errors.push(`${p}.type ??: ${String(r.type)}`);
    if (typeof r.message !== 'string') errors.push(`${p}.message ??? string`);
    if (r.field != null && !isPath(r.field)) errors.push(`${p}.field ??: ${String(r.field)}`);
    if (r.fields != null && (!Array.isArray(r.fields) || r.fields.some((x: any) => !isPath(x)))) errors.push(`${p}.fields ??? string[]`);
    if (r.params != null && (typeof r.params !== 'object' || Array.isArray(r.params))) errors.push(`${p}.params ?????`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

const validateDictCascadeRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    validateTargetFields(r.targets, errors, `${p}.targets`);
    if (!isPath(r.from)) errors.push(`${p}.from ??: ${String(r.from)}`);
    if (r.optionMatchField != null && !isIdentifier(r.optionMatchField)) errors.push(`${p}.optionMatchField ??: ${String(r.optionMatchField)}`);
    if (r.emptyMode != null && !['none', 'all'].includes(r.emptyMode)) errors.push(`${p}.emptyMode ??: ${String(r.emptyMode)}`);
    if (r.clearOnChange != null && typeof r.clearOnChange !== 'boolean') errors.push(`${p}.clearOnChange ??? boolean`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

const validatePopupQueryRules = (v: any, errors: string[], path: string) => {
  if (v == null) return;
  if (!Array.isArray(v)) {
    errors.push(`${path} ?????`);
    return;
  }
  v.forEach((r: any, i: number) => {
    const p = `${path}[${i}]`;
    if (r == null || typeof r !== 'object' || Array.isArray(r)) {
      errors.push(`${p} ?????`);
      return;
    }
    validateWhenClause(r.when, errors, `${p}.when`);
    validateTargetFields(r.targets, errors, `${p}.targets`);

    if (r.params != null) {
      if (Array.isArray(r.params)) {
        r.params.forEach((pp: any, j: number) => {
          const ppPath = `${p}.params[${j}]`;
          if (pp == null || typeof pp !== 'object' || Array.isArray(pp)) {
            errors.push(`${ppPath} ?????`);
            return;
          }
          if (!isIdentifier(pp.key)) errors.push(`${ppPath}.key ??: ${String(pp.key)}`);
          if (!['const', 'copy', 'formula'].includes(pp.mode)) errors.push(`${ppPath}.mode ??: ${String(pp.mode)}`);
          if (pp.mode === 'copy' && pp.from != null && !isPath(pp.from)) errors.push(`${ppPath}.from ??: ${String(pp.from)}`);
          if (pp.mode === 'formula' && pp.formula != null && typeof pp.formula !== 'string') errors.push(`${ppPath}.formula ??? string`);
        });
      } else if (typeof r.params !== 'object') {
        errors.push(`${p}.params ????????`);
      }
    }

    if (r.merge != null && typeof r.merge !== 'boolean') errors.push(`${p}.merge ??? boolean`);
    if (r.priority != null && typeof r.priority !== 'number') errors.push(`${p}.priority ??? number`);
  });
};

export const validateOnChange = (json: string): ValidateResult => {
  try {
    const obj = parseObject(json);
    const errors: string[] = [];

    const code = obj.onCodeGenerate;
    if (code != null) {
      if (typeof code !== 'object' || Array.isArray(code)) errors.push('onCodeGenerate 必须是对象');
      if (code?.autoGenerateRule != null && typeof code.autoGenerateRule !== 'string') errors.push('onCodeGenerate.autoGenerateRule 必须是字符串');
      if (code?.generateParams != null && (typeof code.generateParams !== 'object' || Array.isArray(code.generateParams)))
        errors.push('onCodeGenerate.generateParams 必须是对象');
    }

    const main = obj.onMainChange;
    if (main != null) {
      if (typeof main !== 'object' || Array.isArray(main)) errors.push('onMainChange 必须是对象');
      if (main?.formula != null && typeof main.formula !== 'string') errors.push('onMainChange.formula 必须是字符串');
      validateTargetFields(main?.targetFields, errors, 'onMainChange.targetFields');
      if (main?.triggerCondition != null && typeof main.triggerCondition !== 'string') errors.push('onMainChange.triggerCondition 必须是字符串');
    }

    const sub = obj.onSubChange;
    if (sub != null) {
      if (typeof sub !== 'object' || Array.isArray(sub)) errors.push('onSubChange 必须是对象');
      if (sub?.formula != null && typeof sub.formula !== 'string') errors.push('onSubChange.formula 必须是字符串');
      validateTargetFields(sub?.targetFields, errors, 'onSubChange.targetFields');
      if (sub?.triggerCondition != null && typeof sub.triggerCondition !== 'string') errors.push('onSubChange.triggerCondition 必须是字符串');
    }

    const trig = obj.onSubTrigger;
    if (trig != null) {
      if (typeof trig !== 'object' || Array.isArray(trig)) errors.push('onSubTrigger 必须是对象');
      if (trig?.triggerType != null && !['aggregate', 'sum', 'count', 'average', 'max', 'min'].includes(trig.triggerType)) {
        errors.push(`onSubTrigger.triggerType 非法: ${String(trig.triggerType)}`);
      }
      if (trig?.formula != null && typeof trig.formula !== 'string') errors.push('onSubTrigger.formula 必须是字符串');
      validateTargetFields(trig?.targetFields, errors, 'onSubTrigger.targetFields');
      if (trig?.triggerCondition != null && typeof trig.triggerCondition !== 'string') errors.push('onSubTrigger.triggerCondition 必须是字符串');
    }

    // compatible extensions
    validateToggleRules(obj.onVisible, errors, 'onVisible');
    validateToggleRules(obj.onDisable, errors, 'onDisable');
    validateToggleRules(obj.onRequired, errors, 'onRequired');
    validateClearRules(obj.onClear, errors, 'onClear');
    validateSetValueRules(obj.onSetValue, errors, 'onSetValue');
    validateValidateRules(obj.onValidate, errors, 'onValidate');
    validateDictCascadeRules(obj.onDictCascade, errors, 'onDictCascade');
    validatePopupQueryRules(obj.onPopupQuery, errors, 'onPopupQuery');
    validateSetValueRules(obj.onRowAdd, errors, 'onRowAdd');
    validateValidateRules(obj.onRowValidate, errors, 'onRowValidate');

    return errors.length ? { ok: false, errors } : { ok: true };
  } catch (e: any) {
    return { ok: false, errors: [`onChange JSON格式错误: ${e?.message || String(e)}`] };
  }
};
