import type { EngineeringManifest, EngineeringTask } from '@/api/ai/engineering';

export interface EngineeringTaskOutcome {
  title: string;
  description: string;
  type: 'success' | 'warning' | 'info' | 'error';
  focus?: 'drc' | 'files';
}

/** 结论仅来自任务终态和已确认清单，完整产物的 DRC 违规不解释为文件导出失败。 */
export function engineeringTaskOutcome(
  task: EngineeringTask,
  manifest?: Pick<EngineeringManifest, 'artifacts' | 'missingTypes' | 'drc'>
): EngineeringTaskOutcome {
  if (task.state === 'FAILED') return { title: '任务执行失败', description: task.errorMessage || '请核对设备状态及任务记录后重试。', type: 'error' };
  if (task.state === 'CANCELED') return { title: '任务已取消', description: '已保存的工程文件与产物仍可在本次任务中核对。', type: 'info' };
  if (task.state === 'EXPIRED')
    return { title: '任务已过期', description: task.errorMessage || '请检查设备是否在线，再发起新的任务。', type: 'warning' };
  if (task.state === 'PARTIAL_SUCCESS') {
    const missing = manifest?.missingTypes ?? task.missingTypes ?? [];
    if (missing.length)
      return {
        title: '部分产物待补齐',
        description: `还有 ${missing.length} 类必需产物尚未归档，已保存的文件仍可查看与下载。`,
        type: 'warning',
        focus: 'files'
      };
    if (manifest && manifest.artifacts.length > 0 && manifest.drc?.status === 'VIOLATIONS') {
      const report = manifest.drc;
      const counts =
        typeof report.errorCount === 'number'
          ? `检查发现 ${report.errorCount} 个错误${typeof report.warningCount === 'number' ? `、${report.warningCount} 个警告` : ''}`
          : '检查报告存在设计规则违规';
      return {
        title: '草稿已归档，DRC 需要处理',
        description: `已保存 ${manifest.artifacts.length} 个产物。${counts}，请查看报告并修正设计。`,
        type: 'warning',
        focus: 'drc'
      };
    }
    if (manifest && manifest.artifacts.length > 0 && ['UNAVAILABLE', 'NOT_RUN'].includes(manifest.drc?.status || ''))
      return {
        title: '产物已归档，DRC 尚未确认',
        description: '请核对检查报告；未获得有效检查结果前不能判定设计规则通过。',
        type: 'warning',
        focus: 'drc'
      };
    return { title: '任务部分完成，请核对结果', description: task.errorMessage || '请查看当前已保存的文件和任务记录。', type: 'warning' };
  }
  if (task.state === 'SUCCEEDED') {
    if (task.action === 'OPEN_PROJECT')
      return { title: '本地工程已打开', description: '可在配对设备的嘉立创编辑器中继续查看与编辑。', type: 'success' };
    return {
      title: '任务已完成',
      description: manifest?.artifacts.length
        ? `已保存 ${manifest.artifacts.length} 个产物，可继续查看预览或下载工程文件。`
        : '执行结果已保存，请以实际检查结果和已确认产物为准。',
      type: 'success'
    };
  }
  return {
    title: task.state === 'RUNNING' ? '设备正在执行任务' : '等待设备执行',
    description: '页面会自动更新任务状态，完成后可查看检查结果或工程产物。',
    type: 'info'
  };
}
