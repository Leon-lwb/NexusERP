/** 本期已经验证的模板，不能把任意库或脚本作为模型参数。 */
export const TEMPLATE_ID = 'temperature-humidity-demo';
/** 将模板白名单参数转为编辑器数值；单位为毫米，仅允许双层演示板。 */
export function validateParameters(value: Record<string, unknown>) {
  for (const key of Object.keys(value))
    if (!['boardWidthMm', 'boardHeightMm', 'layers', 'optionalModules'].includes(key)) throw new Error('未知模板参数');
  if (value.optionalModules !== undefined && (!Array.isArray(value.optionalModules) || value.optionalModules.length))
    throw new Error('本模板没有可选模块');
  const width = Number(value.boardWidthMm),
    height = Number(value.boardHeightMm);
  if (!Number.isFinite(width) || width < 40 || width > 100 || !Number.isFinite(height) || height < 30 || height > 100)
    throw new Error('板框尺寸超出范围');
  if (value.layers !== 2) throw new Error('本模板只支持双层板');
  return { boardWidthMm: width, boardHeightMm: height, layers: 2 };
}
