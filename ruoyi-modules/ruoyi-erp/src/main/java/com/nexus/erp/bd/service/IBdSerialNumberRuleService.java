package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdSerialNumberRuleBo;
import com.nexus.erp.bd.domain.vo.BdSerialNumberRuleVo;

import java.util.List;

/**
 * 产品序列号/条码规则 Service 接口
 * <p>
 * 与 {@code SysAutoCodeUtil} 的分工：
 * <ul>
 *   <li>{@code SysAutoCodeUtil} — 对接 {@code sys_auto_code_rule}，负责生产单据编码
 *       （采购单号/工单号/收货单号等），灵活的组合式零件架构</li>
 *   <li>本 Service — 对接 {@code bd_serial_number_rule}，负责产品级唯一标识
 *       （产品序列号/条码/批次号），内置校验位计算（MOD10/MOD11）</li>
 * </ul>
 *
 * @author nexus
 */
public interface IBdSerialNumberRuleService {
    TableDataInfo<BdSerialNumberRuleVo> selectPageList(BdSerialNumberRuleBo bo, PageQuery pageQuery);
    List<BdSerialNumberRuleVo> selectList(BdSerialNumberRuleBo bo);
    BdSerialNumberRuleVo selectById(Long id);
    BdSerialNumberRuleVo selectByCode(String ruleCode);
    int insertByBo(BdSerialNumberRuleBo bo);
    int updateByBo(BdSerialNumberRuleBo bo);
    int deleteByIds(Long[] ids);

    /**
     * 根据规则生成下一个产品序列号/条码/批次号
     * <p>
     * 生成公式: prefix + dateFormat + seqNumber + checkDigit
     * 含校验位（MOD10/MOD11）是区别于 {@code SysAutoCodeUtil.genSerialCode()} 的关键能力。
     *
     * @param ruleCode 规则编码
     * @return 生成的序列号
     */
    String generateSerialNumber(String ruleCode);
}
