package com.nexus.iot.alert.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.domain.bo.IotAlertRuleBo;
import com.nexus.iot.alert.domain.vo.IotAlertRuleVo;

import java.util.List;

/**
 * IoT告警规则Service接口（IOT-ALERT-02）
 *
 * @author nexus
 */
public interface IIotAlertRuleService {

    /**
     * 分页查询告警规则。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 规则分页结果
     */
    TableDataInfo<IotAlertRuleVo> selectPageList(IotAlertRuleBo bo, PageQuery pageQuery);

    /**
     * 查询告警规则列表（不分页）。
     *
     * @param bo 查询条件
     * @return 规则列表
     */
    List<IotAlertRuleVo> selectList(IotAlertRuleBo bo);

    /**
     * 查询规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则VO
     */
    IotAlertRuleVo selectById(Long ruleId);

    /**
     * 新增规则（唯一键冲突时抛业务异常）。
     *
     * @param bo 规则业务对象
     * @return 是否成功
     */
    boolean insertByBo(IotAlertRuleBo bo);

    /**
     * 修改规则（唯一键冲突时抛业务异常）。
     *
     * @param bo 规则业务对象
     * @return 是否成功
     */
    boolean updateByBo(IotAlertRuleBo bo);

    /**
     * 批量删除规则（物理删除，规则为配置项；已产生的告警记录保留阈值快照，不受影响）。
     *
     * @param ids 规则ID数组
     * @return 是否成功
     */
    boolean deleteByIds(Long[] ids);

    /**
     * 启用/停用规则（条件更新 CAS）。
     *
     * @param ruleId  规则ID
     * @param enabled 1启用 0停用
     * @return 是否成功
     */
    boolean changeEnabled(Long ruleId, String enabled);

    /**
     * 消费端使用：按"设备范围 &gt; 产品范围 &gt; 全局"的优先级返回该属性命中的启用规则。
     * <p>
     * 已按具体度降序排序，调用方取第一条命中的规则即可；库内无匹配规则时由调用方回落 YAML 默认。
     * 该方法运行在无登录上下文的消费线程上，内部用 {@code TenantHelper.dynamic} 传播租户。
     *
     * @param tenantId     租户ID
     * @param deviceId     设备ID
     * @param productId    产品ID（可为空）
     * @param propertyCode 属性编码
     * @return 命中的启用规则（按具体度降序）
     */
    List<IotAlertRule> selectEnabledRules(String tenantId, Long deviceId, Long productId, String propertyCode);

}
