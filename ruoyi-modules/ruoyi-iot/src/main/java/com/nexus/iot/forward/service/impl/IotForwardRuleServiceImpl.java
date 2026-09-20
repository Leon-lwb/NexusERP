package com.nexus.iot.forward.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import io.github.linpeilie.Converter;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.forward.IotForwardUrlValidator;
import com.nexus.iot.forward.domain.IotForwardLog;
import com.nexus.iot.forward.domain.IotForwardRule;
import com.nexus.iot.forward.domain.bo.IotForwardRuleBo;
import com.nexus.iot.forward.mapper.IotForwardLogMapper;
import com.nexus.iot.forward.mapper.IotForwardRuleMapper;
import com.nexus.iot.forward.service.IIotForwardRuleService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IoT数据转发规则Service业务层处理。
 * <p>
 * SSRF 防护在**保存时**与**投递时**双重校验：保存时挡住明显的非法配置（避免误配置内网地址），
 * 投递时再解析一次（防 DNS rebinding：登记时合法域名被解析到内网地址）。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotForwardRuleServiceImpl implements IIotForwardRuleService {

    private final IotForwardRuleMapper baseMapper;
    private final IotForwardLogMapper logMapper;
    /**
     * mapstruct-plus 转换器（注入而非静态 {@code MapstructUtils}：后者静态初始化依赖 Spring 容器，单测无法打桩）。
     */
    private final Converter converter;

    /**
     * 查询转发规则列表。
     *
     * @param ruleName 规则名称（模糊，可空）
     * @param enabled  启用状态（可空）
     * @return 规则列表
     */
    @Override
    public List<IotForwardRule> selectList(String ruleName, String enabled) {
        LambdaQueryWrapper<IotForwardRule> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(ruleName), IotForwardRule::getRuleName, ruleName);
        lqw.eq(StringUtils.isNotBlank(enabled), IotForwardRule::getEnabled, enabled);
        lqw.orderByAsc(IotForwardRule::getRuleId);
        return baseMapper.selectList(lqw);
    }

    /**
     * 查询规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则实体，不存在返回 null
     */
    @Override
    public IotForwardRule selectById(Long ruleId) {
        return ruleId == null ? null : baseMapper.selectById(ruleId);
    }

    /**
     * 新增规则。
     *
     * @param bo 规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotForwardRuleBo bo) {
        IotForwardRule entity = converter.convert(bo, IotForwardRule.class);
        if (entity == null) {
            throw new ServiceException("转发规则数据转换失败");
        }
        validateAndNormalize(entity);
        try {
            return baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一范围(产品+设备)下已存在同名规则：" + bo.getRuleName());
        }
    }

    /**
     * 修改规则。
     *
     * @param bo 规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotForwardRuleBo bo) {
        if (bo.getRuleId() == null) {
            throw new ServiceException("规则ID不能为空");
        }
        if (baseMapper.selectById(bo.getRuleId()) == null) {
            throw new ServiceException("转发规则不存在：" + bo.getRuleId());
        }
        IotForwardRule entity = converter.convert(bo, IotForwardRule.class);
        validateAndNormalize(entity);
        try {
            return baseMapper.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一范围(产品+设备)下已存在同名规则：" + bo.getRuleName());
        }
    }

    /**
     * 批量删除规则（留痕保留）。
     *
     * @param ids 规则ID数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 分页查询投递留痕。
     *
     * @param ruleId    规则ID（可空）
     * @param deviceId  设备ID（可空）
     * @param status    投递状态（可空）
     * @param pageQuery 分页参数
     * @return 留痕分页结果
     */
    @Override
    public TableDataInfo<IotForwardLog> selectLogPage(Long ruleId, Long deviceId, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<IotForwardLog> lqw = Wrappers.lambdaQuery();
        lqw.eq(ruleId != null, IotForwardLog::getRuleId, ruleId);
        lqw.eq(deviceId != null, IotForwardLog::getDeviceId, deviceId);
        lqw.eq(StringUtils.isNotBlank(status), IotForwardLog::getStatus, status);
        lqw.orderByDesc(IotForwardLog::getLogId);
        Page<IotForwardLog> page = logMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 校验并补全默认值：URL 必须通过 SSRF 校验；范围/方法/超时给默认值。
     */
    private void validateAndNormalize(IotForwardRule entity) {
        String sanitized = IotForwardUrlValidator.sanitize(entity.getTargetUrl());
        if (sanitized == null) {
            throw new ServiceException("Webhook URL 未通过 SSRF 校验：仅允许 http/https 且不得指向内网/保留地址");
        }
        entity.setTargetUrl(sanitized);
        entity.setProductId(entity.getProductId() == null ? 0L : entity.getProductId());
        entity.setDeviceId(entity.getDeviceId() == null ? 0L : entity.getDeviceId());
        entity.setTargetType(StringUtils.isBlank(entity.getTargetType()) ? "HTTP" : entity.getTargetType());
        entity.setHttpMethod(StringUtils.isBlank(entity.getHttpMethod()) ? "POST" : entity.getHttpMethod());
        entity.setTimeoutMs(entity.getTimeoutMs() == null ? 5000 : entity.getTimeoutMs());
        entity.setMaxRetry(entity.getMaxRetry() == null ? 3 : entity.getMaxRetry());
        entity.setEnabled(StringUtils.isBlank(entity.getEnabled()) ? "1" : entity.getEnabled());
    }
}
