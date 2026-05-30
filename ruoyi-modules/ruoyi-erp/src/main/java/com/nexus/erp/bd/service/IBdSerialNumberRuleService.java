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

    /**
     * 分页查询产品序列号/条码规则列表
     *
     * @param bo 查询参数，包含规则名称、规则编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 序列号规则分页列表
     */
    TableDataInfo<BdSerialNumberRuleVo> selectPageList(BdSerialNumberRuleBo bo, PageQuery pageQuery);

    /**
     * 查询产品序列号/条码规则列表（不分页）
     *
     * @param bo 查询参数，包含规则名称、规则编码等筛选条件
     * @return 序列号规则列表
     */
    List<BdSerialNumberRuleVo> selectList(BdSerialNumberRuleBo bo);

    /**
     * 根据ID查询序列号规则
     *
     * @param id 规则ID
     * @return 序列号规则详细信息
     */
    BdSerialNumberRuleVo selectById(Long id);

    /**
     * 根据规则编码查询序列号规则
     *
     * @param ruleCode 规则编码
     * @return 序列号规则详细信息
     */
    BdSerialNumberRuleVo selectByCode(String ruleCode);

    /**
     * 新增序列号规则
     *
     * @param bo 序列号规则业务对象，包含规则编码、规则名称、编码格式等配置信息
     * @return 影响行数
     */
    int insertByBo(BdSerialNumberRuleBo bo);

    /**
     * 修改序列号规则
     *
     * @param bo 序列号规则业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdSerialNumberRuleBo bo);

    /**
     * 批量删除序列号规则
     *
     * @param ids 规则ID数组
     * @return 影响行数
     */
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
