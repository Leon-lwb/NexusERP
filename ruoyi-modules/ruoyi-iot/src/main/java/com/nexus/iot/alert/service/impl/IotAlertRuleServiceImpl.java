package com.nexus.iot.alert.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertCondition;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.domain.bo.IotAlertRuleBo;
import com.nexus.iot.alert.domain.vo.IotAlertRuleVo;
import com.nexus.iot.alert.mapper.IotAlertRuleMapper;
import com.nexus.iot.alert.service.IIotAlertRuleService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * IoT告警规则Service业务层处理（IOT-ALERT-02）
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotAlertRuleServiceImpl implements IIotAlertRuleService {

    /** 设备范围具体度（最高） */
    private static final int SPECIFICITY_DEVICE = 3;
    /** 产品范围具体度 */
    private static final int SPECIFICITY_PRODUCT = 2;
    /** 全局范围具体度（最低） */
    private static final int SPECIFICITY_GLOBAL = 1;

    private final IotAlertRuleMapper baseMapper;

    /**
     * 分页查询告警规则。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 规则分页结果
     */
    @Override
    public TableDataInfo<IotAlertRuleVo> selectPageList(IotAlertRuleBo bo, PageQuery pageQuery) {
        Page<IotAlertRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询告警规则列表（不分页）。
     *
     * @param bo 查询条件
     * @return 规则列表
     */
    @Override
    public List<IotAlertRuleVo> selectList(IotAlertRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则VO，不存在返回 null
     */
    @Override
    public IotAlertRuleVo selectById(Long ruleId) {
        return baseMapper.selectVoById(ruleId);
    }

    /**
     * 新增规则。
     * <p>
     * 先按唯一键 (tenant_id, product_id, device_id, property_code) 做可读性校验，
     * 再兜底捕获并发下的 {@link DuplicateKeyException}，保证不会出现"同一范围同属性两条规则"。
     *
     * @param bo 规则业务对象
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertByBo(IotAlertRuleBo bo) {
        normalize(bo);
        assertScopeUnique(bo, null);
        IotAlertRule entity = MapstructUtils.convert(bo, IotAlertRule.class);
        try {
            return baseMapper.insert(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一租户下该产品/设备范围与属性编码的规则已存在");
        }
    }

    /**
     * 修改规则。
     *
     * @param bo 规则业务对象
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateByBo(IotAlertRuleBo bo) {
        if (bo.getRuleId() == null) {
            throw new ServiceException("规则ID不能为空");
        }
        if (baseMapper.selectById(bo.getRuleId()) == null) {
            throw new ServiceException("规则不存在");
        }
        normalize(bo);
        assertScopeUnique(bo, bo.getRuleId());
        IotAlertRule entity = MapstructUtils.convert(bo, IotAlertRule.class);
        try {
            return baseMapper.updateById(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一租户下该产品/设备范围与属性编码的规则已存在");
        }
    }

    /**
     * 批量删除规则（物理删除）。
     *
     * @param ids 规则ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteBatchIds(List.of(ids)) > 0;
    }

    /**
     * 启用/停用规则（CAS 条件更新）。
     *
     * @param ruleId  规则ID
     * @param enabled 1启用 0停用
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeEnabled(Long ruleId, String enabled) {
        if (ruleId == null) {
            throw new ServiceException("规则ID不能为空");
        }
        if (!IotAlertConstants.ENABLED_YES.equals(enabled) && !IotAlertConstants.ENABLED_NO.equals(enabled)) {
            throw new ServiceException("启用状态仅支持 1(启用)/0(停用)");
        }
        int rows = baseMapper.update(null, Wrappers.<IotAlertRule>lambdaUpdate()
            .eq(IotAlertRule::getRuleId, ruleId)
            .set(IotAlertRule::getEnabled, enabled));
        if (rows == 0) {
            throw new ServiceException("规则不存在或状态未变更");
        }
        return true;
    }

    /**
     * 消费端使用：按 设备 &gt; 产品 &gt; 全局 的优先级返回命中的启用规则。
     * <p>
     * 规则表是配置表（行数有限），故按 (租户, 属性, 启用) 一次取回后在内存中过滤排序，
     * 避免为 OR 组合写复杂 SQL；显式带 tenant_id 条件，不依赖租户插件（消费线程无登录上下文）。
     *
     * @param tenantId     租户ID
     * @param deviceId     设备ID
     * @param productId    产品ID
     * @param propertyCode 属性编码
     * @return 命中的启用规则（具体度降序）
     */
    @Override
    public List<IotAlertRule> selectEnabledRules(String tenantId, Long deviceId, Long productId, String propertyCode) {
        if (StringUtils.isBlank(propertyCode)) {
            return List.of();
        }
        return TenantHelper.dynamic(tenantId, () -> {
            LambdaQueryWrapper<IotAlertRule> lqw = Wrappers.lambdaQuery();
            lqw.eq(IotAlertRule::getEnabled, IotAlertConstants.ENABLED_YES);
            lqw.eq(IotAlertRule::getPropertyCode, propertyCode);
            lqw.eq(StringUtils.isNotBlank(tenantId), IotAlertRule::getTenantId, tenantId);
            List<IotAlertRule> candidates = baseMapper.selectList(lqw);
            List<IotAlertRule> matched = new ArrayList<>();
            for (IotAlertRule rule : candidates) {
                if (specificity(rule, deviceId, productId) > 0) {
                    matched.add(rule);
                }
            }
            matched.sort(Comparator.comparingInt((IotAlertRule r) -> -specificity(r, deviceId, productId)));
            return matched;
        });
    }

    /**
     * 计算规则对给定设备/产品的具体度：0=不适用，3=设备级，2=产品级，1=全局。
     *
     * @param rule      规则
     * @param deviceId  设备ID
     * @param productId 产品ID
     * @return 具体度
     */
    private int specificity(IotAlertRule rule, Long deviceId, Long productId) {
        Long ruleDeviceId = rule.getDeviceId();
        if (ruleDeviceId != null && ruleDeviceId != 0L) {
            return ruleDeviceId.equals(deviceId) ? SPECIFICITY_DEVICE : 0;
        }
        Long ruleProductId = rule.getProductId();
        if (ruleProductId != null && ruleProductId != 0L) {
            return ruleProductId.equals(productId) ? SPECIFICITY_PRODUCT : 0;
        }
        return SPECIFICITY_GLOBAL;
    }

    /**
     * 归一化入参：不限范围用 0 表达（唯一键对 NULL 不生效，必须避免 NULL）。
     *
     * @param bo 规则业务对象
     */
    private void normalize(IotAlertRuleBo bo) {
        if (bo.getProductId() == null) {
            bo.setProductId(0L);
        }
        if (bo.getDeviceId() == null) {
            bo.setDeviceId(0L);
        }
        if (bo.getDurationSec() == null) {
            bo.setDurationSec(0);
        }
        if (bo.getDebounceSec() == null) {
            bo.setDebounceSec(60);
        }
        if (StringUtils.isBlank(bo.getAlertType())) {
            bo.setAlertType("OTHER");
        }
        if (StringUtils.isBlank(bo.getAlertLevel())) {
            bo.setAlertLevel("MEDIUM");
        }
        if (StringUtils.isBlank(bo.getNotifyChannels())) {
            bo.setNotifyChannels("SSE,MES_ANDON");
        }
        if (StringUtils.isBlank(bo.getEnabled())) {
            bo.setEnabled(IotAlertConstants.ENABLED_YES);
        }
        bo.setOperator(IotAlertCondition.normalizeOperator(bo.getOperator()));
    }

    /**
     * 校验 (租户, 产品, 设备, 属性) 唯一，排除自身。
     *
     * @param bo         规则业务对象
     * @param excludeId  排除的规则ID（修改场景）
     */
    private void assertScopeUnique(IotAlertRuleBo bo, Long excludeId) {
        LambdaQueryWrapper<IotAlertRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(IotAlertRule::getPropertyCode, bo.getPropertyCode());
        lqw.eq(IotAlertRule::getProductId, bo.getProductId());
        lqw.eq(IotAlertRule::getDeviceId, bo.getDeviceId());
        lqw.ne(excludeId != null, IotAlertRule::getRuleId, excludeId);
        Long count = baseMapper.selectCount(lqw);
        if (count != null && count > 0) {
            throw new ServiceException("同一租户下该产品/设备范围与属性编码的规则已存在");
        }
    }

    private LambdaQueryWrapper<IotAlertRule> buildQueryWrapper(IotAlertRuleBo bo) {
        LambdaQueryWrapper<IotAlertRule> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getRuleName()), IotAlertRule::getRuleName, bo.getRuleName());
        lqw.eq(StringUtils.isNotBlank(bo.getPropertyCode()), IotAlertRule::getPropertyCode, bo.getPropertyCode());
        lqw.eq(bo.getProductId() != null, IotAlertRule::getProductId, bo.getProductId());
        lqw.eq(bo.getDeviceId() != null, IotAlertRule::getDeviceId, bo.getDeviceId());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertType()), IotAlertRule::getAlertType, bo.getAlertType());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertLevel()), IotAlertRule::getAlertLevel, bo.getAlertLevel());
        lqw.eq(StringUtils.isNotBlank(bo.getEnabled()), IotAlertRule::getEnabled, bo.getEnabled());
        lqw.orderByDesc(IotAlertRule::getRuleId);
        return lqw;
    }

}
