package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdSerialNumberRule;
import com.nexus.erp.bd.domain.bo.BdSerialNumberRuleBo;
import com.nexus.erp.bd.domain.vo.BdSerialNumberRuleVo;
import com.nexus.erp.bd.mapper.BdSerialNumberRuleMapper;
import com.nexus.erp.bd.service.IBdSerialNumberRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 产品序列号/条码规则 Service 实现
 * <p>
 * 定位：产品级唯一标识生成（产品序列号/条码/批次号），含校验位。
 * 生产单据编码请使用 {@code com.nexus.system.strategy.AutoCodeUtil.genSerialCode()}。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdSerialNumberRuleServiceImpl implements IBdSerialNumberRuleService {

    private final BdSerialNumberRuleMapper baseMapper;

    /**
     * 分页查询序列号规则列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @param pageQuery 分页参数，包含页码、每页大小及排序信息
     * @return 分页结果
     */
    @Override
    public TableDataInfo<BdSerialNumberRuleVo> selectPageList(
            BdSerialNumberRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdSerialNumberRule> lqw = buildQueryWrapper(bo);
        Page<BdSerialNumberRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询序列号规则列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 结果列表
     */
    @Override
    public List<BdSerialNumberRuleVo> selectList(BdSerialNumberRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询序列号规则详情
     *
     * @param id 主键ID
     * @return 结果
     */
    @Override
    public BdSerialNumberRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据规则编码查询序列号规则
     *
     * @param ruleCode 规则编码
     * @return 结果
     */
    @Override
    public BdSerialNumberRuleVo selectByCode(String ruleCode) {
        LambdaQueryWrapper<BdSerialNumberRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(BdSerialNumberRule::getRuleCode, ruleCode);
        return baseMapper.selectVoOne(lqw);
    }

    /**
     * 新增序列号规则
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdSerialNumberRuleBo bo) {
        BdSerialNumberRule entity = MapstructUtils.convert(bo, BdSerialNumberRule.class);
        validateRule(entity);
        entity.setRuleVersion(1L);
        entity.setCurrentSeq(0);
        entity.setAttr1(null);
        return baseMapper.insert(entity);
    }

    /**
     * 修改序列号规则
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdSerialNumberRuleBo bo) {
        BdSerialNumberRule current = baseMapper.selectById(bo.getRuleId());
        if (current == null) {
            throw new ServiceException("编号规则不存在");
        }
        long version = current.getRuleVersion() == null ? 1L : current.getRuleVersion();
        if (bo.getRuleVersion() == null || bo.getRuleVersion() != version) {
            throw new ServiceException("编号规则已变更，请刷新后操作");
        }
        BdSerialNumberRule entity = MapstructUtils.convert(bo, BdSerialNumberRule.class);
        validateRule(entity);
        entity.setRuleVersion(Math.addExact(version, 1L));
        entity.setCurrentSeq(null);
        entity.setAttr1(null);
        int changed =
                baseMapper.update(
                        entity,
                        Wrappers.<BdSerialNumberRule>lambdaUpdate()
                                .eq(BdSerialNumberRule::getRuleId, bo.getRuleId())
                                .eq(BdSerialNumberRule::getRuleVersion, version));
        if (changed != 1) {
            throw new ServiceException("编号规则已被并发修改，请刷新后操作");
        }
        return changed;
    }

    /**
     * 批量删除序列号规则
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据规则生成下一个序列号
     * <p>
     * 生成逻辑:
     * <ol>
     *   <li>根据ruleCode查询规则配置</li>
     *   <li>拼接: prefix + dateSegment + seqNumber + checkDigit</li>
     *   <li>原子递增currentSeq并回写数据库</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateSerialNumber(String ruleCode) {
        LambdaQueryWrapper<BdSerialNumberRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(BdSerialNumberRule::getRuleCode, ruleCode);
        BdSerialNumberRule rule = baseMapper.selectOne(lqw);
        if (rule == null) {
            throw new ServiceException("序列号规则不存在: {}", ruleCode);
        }
        if ("PRODUCT_SN".equals(rule.getRuleType())) {
            throw new ServiceException("正式SN须通过带命令号的身份注册服务生成");
        }
        if (!"ACTIVE".equals(rule.getStatus())) {
            throw new ServiceException("序列号规则未启用: {}", ruleCode);
        }

        // 构建日期段
        String dateSegment = "";
        if (StringUtils.isNotBlank(rule.getDateFormat())) {
            dateSegment = LocalDate.now().format(DateTimeFormatter.ofPattern(rule.getDateFormat()));
        }

        // 处理流水号重置周期：用 DB 条件更新原子递增（current_seq = current_seq + 1 WHERE 周期段未变，
        // 周期段变化时重置为 1），替代 synchronized + 读改写（跨实例失效且事务提交前锁已释放）
        int currentSeq;
        String cycle = rule.getSeqCycle();
        if (StringUtils.isBlank(cycle) || "NONE".equals(cycle)) {
            LambdaUpdateWrapper<BdSerialNumberRule> uw = Wrappers.lambdaUpdate();
            uw.eq(BdSerialNumberRule::getRuleCode, ruleCode);
            uw.setSql("current_seq = COALESCE(current_seq, 0) + 1");
            baseMapper.update(uw);
            BdSerialNumberRule updated =
                    baseMapper.selectOne(
                            Wrappers.<BdSerialNumberRule>lambdaQuery()
                                    .eq(BdSerialNumberRule::getRuleCode, ruleCode));
            currentSeq = updated.getCurrentSeq() != null ? updated.getCurrentSeq() : 1;
        } else {
            // 周期段（DAY/MONTH/YEAR）变化时重置：条件更新只在周期段匹配时递增，否则置 1
            String cycleSegment = buildCycleDatePart(cycle);
            LambdaUpdateWrapper<BdSerialNumberRule> uw = Wrappers.lambdaUpdate();
            uw.eq(BdSerialNumberRule::getRuleCode, ruleCode);
            uw.setSql(
                    "current_seq = CASE WHEN attr1 = '"
                            + cycleSegment
                            + "' THEN COALESCE(current_seq, 0) + 1 ELSE 1 END");
            uw.set(BdSerialNumberRule::getAttr1, cycleSegment);
            baseMapper.update(uw);
            BdSerialNumberRule updated =
                    baseMapper.selectOne(
                            Wrappers.<BdSerialNumberRule>lambdaQuery()
                                    .eq(BdSerialNumberRule::getRuleCode, ruleCode));
            currentSeq = updated.getCurrentSeq() != null ? updated.getCurrentSeq() : 1;
        }

        // 获取流水号长度(默认4位)
        int seqLen = rule.getSeqLength() != null ? rule.getSeqLength() : 4;
        String seqPart = String.format("%0" + seqLen + "d", currentSeq);

        // 构建前缀
        String prefix = StringUtils.isNotBlank(rule.getPrefix()) ? rule.getPrefix() : "";

        // 计算校验位
        String baseCode = prefix + dateSegment + seqPart;
        String checkDigit = computeCheckDigit(baseCode, rule.getCheckDigitRule());

        return baseCode + checkDigit;
    }

    private void validateRule(BdSerialNumberRule rule) {
        if (!java.util.Set.of("PRODUCT_SN", "BARCODE", "BATCH_NO").contains(rule.getRuleType())) {
            throw new ServiceException("编号规则类型无效");
        }
        if (rule.getSeqLength() == null || rule.getSeqLength() < 1 || rule.getSeqLength() > 18) {
            throw new ServiceException("流水号长度须为1至18");
        }
        new com.nexus.erp.wm.trace.ErpSerialIdentityService.Rule(
                rule.getPrefix(),
                rule.getDateFormat(),
                rule.getSeqLength(),
                rule.getSeqCycle(),
                rule.getCheckDigitRule(),
                1);
    }

    private String buildCycleDatePart(String cycle) {
        return switch (cycle) {
            case "DAY" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "MONTH" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEAR" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
            default -> "";
        };
    }

    private String computeCheckDigit(String baseCode, String rule) {
        if (StringUtils.isBlank(rule) || "NONE".equals(rule)) {
            return "";
        }
        int sum = 0;
        for (int i = 0; i < baseCode.length(); i++) {
            char ch = baseCode.charAt(i);
            if (Character.isDigit(ch)) {
                sum += (ch - '0') * ((i % 2 == 0) ? 3 : 1);
            }
        }
        if ("MOD10".equals(rule)) {
            int check = (10 - (sum % 10)) % 10;
            return String.valueOf(check);
        }
        if ("MOD11".equals(rule)) {
            int check = (11 - (sum % 11)) % 11;
            return check == 10 ? "X" : String.valueOf(check);
        }
        return "";
    }

    private LambdaQueryWrapper<BdSerialNumberRule> buildQueryWrapper(BdSerialNumberRuleBo bo) {
        LambdaQueryWrapper<BdSerialNumberRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(
                StringUtils.isNotBlank(bo.getRuleCode()),
                BdSerialNumberRule::getRuleCode,
                bo.getRuleCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getRuleName()),
                BdSerialNumberRule::getRuleName,
                bo.getRuleName());
        lqw.eq(
                StringUtils.isNotBlank(bo.getStatus()),
                BdSerialNumberRule::getStatus,
                bo.getStatus());
        lqw.orderByAsc(BdSerialNumberRule::getRuleCode);
        return lqw;
    }
}
