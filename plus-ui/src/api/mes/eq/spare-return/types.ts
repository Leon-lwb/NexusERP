/**
 * MES 备件退库单（MES-EQ-04 前端契约冻结：端点/字段/权限码不可改名）
 *
 * @author Nexus
 */

import type { EqSpareDecimal } from '@/api/mes/eq/spare-part';

/** 备件退库单明细行视图对象 */
export interface SpareReturnLineVo {
  /** 明细行ID */
  lineId?: string;
  /** 退库单ID */
  returnId?: string;
  /** 备件ID */
  spareId?: string;
  /** 备件编码 */
  spareCode?: string;
  /** 备件名称 */
  spareName?: string;
  /** 退库数量 */
  returnQty?: EqSpareDecimal;
  /** 单价(可选, 最多四位小数, 用于 ERP 计价) */
  unitCost?: EqSpareDecimal;
  /** 备注 */
  remark?: string;
}

/** 备件退库单视图对象 */
export interface SpareReturnVo {
  /** 退库单ID */
  returnId?: string;
  /** 退库单号 */
  returnNo?: string;
  /** ERP 仓库ID(可选；提供时须与每行备件映射一致，后端权威校验) */
  warehouseId?: string;
  /** 退库日期 */
  returnDate?: string;
  /** 退库类型: REPAIR_RETURN(维修退库) / EXCESS_RETURN(余料退库) / OTHER */
  returnType?: string;
  /** 状态: DRAFT / CONFIRMED / CANCELLED */
  status?: string;
  /** 合计数量(后端 BigDecimal 可能返回字符串) */
  totalQty?: number | string | null;
  /** 备注 */
  remark?: string;
  /** 确认人 */
  confirmBy?: string;
  /** 确认时间 */
  confirmTime?: string;
  /** 创建时间 */
  createTime?: string;
  /** 明细行 */
  lines?: SpareReturnLineVo[];
}

/** 备件退库单查询条件 */
export interface SpareReturnQuery extends PageQuery {
  /** 退库单号 */
  returnNo?: string;
  /** 状态 */
  status?: string;
  /** 退库类型 */
  returnType?: string;
  /** 起始日期 */
  beginTime?: string;
  /** 结束日期 */
  endTime?: string;
}

/** 数量输入使用数值；成本为只读展示快照，不提交客户端改价，业务 ID 保持字符串。 */
export interface SpareReturnLineForm extends Omit<SpareReturnLineVo, 'returnQty' | 'unitCost'> {
  returnQty?: number;
  unitCost?: number | string;
}

/** 备件退库单表单 */
export interface SpareReturnForm {
  returnId?: string;
  returnNo?: string;
  /** 新增可留空按明细映射；编辑已有单头仓库时不可清空，须与明细映射一致 */
  warehouseId?: string;
  returnDate?: string;
  returnType?: string;
  status?: string;
  remark?: string;
  lines?: SpareReturnLineForm[];
}
