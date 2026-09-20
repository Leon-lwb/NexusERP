package com.nexus.iot.forward.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.forward.domain.IotForwardLog;
import com.nexus.iot.forward.domain.IotForwardRule;
import com.nexus.iot.forward.domain.bo.IotForwardRuleBo;

import java.util.List;

/**
 * IoT数据转发规则Service接口（§4#6）。
 *
 * @author nexus
 */
public interface IIotForwardRuleService {

    /**
     * 查询转发规则列表。
     *
     * @param ruleName 规则名称（模糊，可空）
     * @param enabled  启用状态（可空）
     * @return 规则列表
     */
    List<IotForwardRule> selectList(String ruleName, String enabled);

    /**
     * 查询规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则实体，不存在返回 null
     */
    IotForwardRule selectById(Long ruleId);

    /**
     * 新增规则（URL 需通过 SSRF 校验，否则拒绝保存）。
     *
     * @param bo 规则业务对象
     * @return 影响行数
     */
    int insertByBo(IotForwardRuleBo bo);

    /**
     * 修改规则。
     *
     * @param bo 规则业务对象
     * @return 影响行数
     */
    int updateByBo(IotForwardRuleBo bo);

    /**
     * 批量删除规则（投递留痕保留，便于事后审计）。
     *
     * @param ids 规则ID数组
     * @return 删除影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 分页查询投递留痕。
     *
     * @param ruleId    规则ID（可空）
     * @param deviceId  设备ID（可空）
     * @param status    投递状态（可空）
     * @param pageQuery 分页参数
     * @return 留痕分页结果
     */
    TableDataInfo<IotForwardLog> selectLogPage(Long ruleId, Long deviceId, String status, PageQuery pageQuery);
}
