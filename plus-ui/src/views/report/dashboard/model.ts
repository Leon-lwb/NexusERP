import type { DashboardWidget, DashboardView } from '@/api/report/dashboard';
import type { TypedValue } from '@/types/report';
/** 图表每点使用服务器坐标，原始值专用于精确tooltip。 */
export interface ChartData {
  /**
   * 当前契约的points配置或状态
   */
  points: {
    /**
     * 用户可见标签
     */
    label: string; /**
     * 当前契约的category配置或状态
     */
    category: TypedValue; /**
     * 当前契约的exact配置或状态
     */
    exact: TypedValue; /**
     * 本次固定快照的服务端钻取坐标
     */
    coordinate: number;
  }[];
  /**
   * 输出小数位数
   */
  scale: string;
}
/** 可见页面且处于查看模式时才允许自动刷新。 */
export function canRefresh(visible: boolean, paused: boolean, editing: boolean, id: string): boolean {
  return visible && !paused && !editing && !!id;
}
/** 响应式布局固定十二列，移动端统一单列并保留指定顺序。 */
export function widgetLayout(widget: DashboardWidget | DashboardView) {
  return { gridColumn: 'span ' + widget.layout.width, gridRow: 'span ' + widget.layout.height, '--mobile-order': widget.layout.mobileOrder };
}
/** ECharts只绘制服务器提供的归一化坐标，不在浏览器相加或计算业务百分比。 */
export function chartOption(type: string, data: ChartData) {
  const points = data.points;
  const tooltip = {
    trigger: 'item',
    renderMode: 'richText',
    formatter: (value: {
      /**
       * 当前契约的dataIndex配置或状态
       */
      dataIndex: number;
    }) => {
      const point = points[value.dataIndex];
      return point ? point.label + '：' + (point.exact.value ?? '（空）') : '';
    }
  };
  if (type === 'PIE')
    return {
      tooltip,
      series: [
        { type: 'pie', radius: ['30%', '65%'], data: points.map((point) => ({ name: point.label, value: point.coordinate })), label: { show: false } }
      ]
    };
  return {
    tooltip,
    xAxis: { type: 'category', data: points.map((point) => point.label) },
    yAxis: { type: 'value', min: points.some((point) => point.coordinate < 0) ? -1 : 0, max: 1, axisLabel: { show: false }, name: '相对坐标' },
    series: [{ type: type === 'LINE' ? 'line' : 'bar', data: points.map((point) => point.coordinate) }],
    grid: { left: 32, right: 16, top: 32, bottom: 40 }
  };
}
