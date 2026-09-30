import { decisionRiskLabel } from './decisionViewModel';

/** 空页不生成饼图扇区，避免 ECharts 将 0/0/0/0 显示成均分风险。 */
export function decisionRiskSeriesData(rows: ReadonlyArray<{ riskLevel?: string | null }>): Array<{ name: string; value: number }> {
  if (rows.length === 0) return [];
  return (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((level) => ({
    name: decisionRiskLabel(level),
    value: rows.filter((row) => row.riskLevel === level).length
  }));
}
