package com.nexus.system.strategy;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.SysAutoCodeResult;
import com.nexus.system.domain.SysAutoCodeRule;
import com.nexus.system.domain.enums.PartTypeEnum;
import com.nexus.system.service.ISysAutoCodePartService;
import com.nexus.system.service.ISysAutoCodeResultService;
import com.nexus.system.service.ISysAutoCodeRuleService;
import com.nexus.common.core.utils.StringUtils;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自动编码工具类
 * <p>按编码规则（sys_auto_code_rule）及其组成（流水号、日期、固定字符、传入字符）生成业务单据编码，
 * 支持落库生成与仅预览两种模式。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Service
public class AutoCodeUtil {
    /** 编码生成期间标记是否需在 result 表落库（true 表示首次生成需新增记录）。 */
    public static ThreadLocal<Boolean> threadLocal = new ThreadLocal<>();

    /** 编码规则业务服务，提供按规则编码查询规则定义的能力。 */
    @Autowired
    private ISysAutoCodeRuleService iAutoCodeRuleService;

    /** 编码规则组成业务服务，提供查询规则下各组成片段的能力。 */
    @Autowired
    private ISysAutoCodePartService iAutoCodePartService;

    /** 编码生成结果业务服务，提供结果记录的查询与保存/更新能力。 */
    @Autowired
    private ISysAutoCodeResultService iAutoCodeResultService;

    /** 编码规则片段类型处理器，按组成类型路由到对应策略生成片段。 */
    @Autowired
    private PartTypeHandler partTypeHandler;

    /**
     * 内部计算上下文：承载 computeCode 的计算结果。
     */
    private static class ComputeContext {
        final SysAutoCodeRule rule;
        final String autoCode;
        final String serialNo;

        ComputeContext(SysAutoCodeRule rule, String autoCode, String serialNo) {
            this.rule = rule;
            this.autoCode = autoCode;
            this.serialNo = serialNo;
        }
    }

    /**
     * 预览编码（不落库，不消耗流水号）。
     * <p>
     * 仅计算并返回下一个编码，<b>不会</b>写入 sys_auto_code_result 表。
     * 适用于前端 el-switch 开关切换时展示编码预览。
     * </p>
     *
     * @param ruleCode       编码规则编码
     * @param inputCharacter 传入字符（可选）
     * @return 预览的编码字符串
     */
    @DS("master")
    public String previewSerialCode(String ruleCode, String inputCharacter) {
        try {
            ComputeContext ctx = computeSerialCode(ruleCode, inputCharacter);
            return ctx.autoCode;
        } finally {
            threadLocal.remove();
        }
    }

    /**
     * 共享的编码计算逻辑（不包含数据库持久化）。
     * <p>
     * 查询规则和组成、通过策略模式生成各片段、拼接并补齐，
     * 返回包含 rule/autoCode/serialNo 的计算上下文。
     * </p>
     */
    private ComputeContext computeSerialCode(String ruleCode, String inputCharacter) {

        SysAutoCodeRule rule = iAutoCodeRuleService.getOne(ruleCode);
        Assert.notNull(rule, "未获取到指定类型:[{}]的业务编码生成规则", ruleCode);

        SysAutoCodePart partParam = new SysAutoCodePart();
        partParam.setRuleId(rule.getRuleId());
        List<SysAutoCodePart> parts = iAutoCodePartService.listPart(partParam);
        List<SysAutoCodePart> collect = parts.stream()
            .filter(part -> PartTypeEnum.PART_TYPE_SERIALNO.getCode().equals(part.getPartType()))
            .collect(Collectors.toList());

        Assert.isTrue(collect.size() < 2, "编码规则[{}]流水号方式的组成只能存在一个", ruleCode);

        StringBuilder buff = new StringBuilder();
        final String[] serialNoHolder = new String[1];
        parts.forEach(codePart -> {
            codePart.setInputCharacter(inputCharacter);
            String partStr = partTypeHandler.choiceExecute(codePart);

            if (StringUtils.equals(codePart.getPartType(), PartTypeEnum.PART_TYPE_SERIALNO.getCode())) {
                serialNoHolder[0] = partStr;
            }
            buff.append(partStr);
        });

        Assert.notBlank(buff.toString(), "规则：[{}]生成的编码为空！", ruleCode);

        String autoCode = paddingStr(rule, buff);

        return new ComputeContext(rule, autoCode, serialNoHolder[0]);
    }

    /**
     * 生成编码并落库（消耗流水号）。
     * <p>在独立事务中计算编码、持久化更新 sys_auto_code_result 表后返回完整编码。
     * 由后端业务 Service 保存单据或前端提交表单前调用。</p>
     * <p>并发控制: 原为 JVM 内 synchronized, 多实例部署下同规则流水号读-改-写会重号(C4);
     * 现改为按规则编码的分布式锁(lock4j 切面优先于事务拦截器, 锁覆盖整个 REQUIRES_NEW 事务直至提交)。</p>
     *
     * @param ruleCode       编码规则编码
     * @param inputCharacter 传入字符（可选）
     * @return 生成的完整编码字符串
     */
    @Lock4j(keys = {"#ruleCode"}, expire = 60000, acquireTimeout = 10000)
    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String genSerialCode(String ruleCode, String inputCharacter) {
        try {
            ComputeContext ctx = computeSerialCode(ruleCode, inputCharacter);
            saveAutoCodeResult(ctx.rule, ctx.autoCode, inputCharacter, ctx.serialNo);
            return ctx.autoCode;
        } finally {
            threadLocal.remove();
        }
    }

    private String paddingStr(SysAutoCodeRule rule, StringBuilder sb) {
        String isPadding = rule.getIsPadded();
        if ("Y".equals(isPadding)) {
            int maxLength = rule.getMaxLength();
            String paddingChar = rule.getPaddedChar();
            StringBuilder resultStr = new StringBuilder();
            long length = maxLength - sb.length();
            Assert.isTrue(maxLength > sb.length(), "生成的编码[{}]已经超出规则中配置的最大长度：[{}]", sb.toString(), maxLength);

            if ("L".equals(rule.getPaddedMethod())) {
                for (; length > 0; length--) {
                    resultStr.append(paddingChar);
                }
                resultStr.append(sb);
            } else {
                resultStr.append(sb);
                for (; length > 0; length--) {
                    resultStr.append(paddingChar);
                }
            }
            return resultStr.toString();
        }
        return sb.toString();
    }

    private void saveAutoCodeResult(SysAutoCodeRule rule, String autoCode, String inputChar, String serialNo) {
        Boolean flag = threadLocal.get();
        if (flag != null && flag) {
            SysAutoCodeResult rs = new SysAutoCodeResult();
            rs.setRuleId(rule.getRuleId());
            rs.setGenDate(DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss"));
            rs.setLastResult(autoCode);
            rs.setGenIndex(1);
            rs.setLastSerialNo(Integer.parseInt(serialNo));
            rs.setLastInputChar(inputChar);
            iAutoCodeResultService.saveAutoCodeResult(rs);
        } else {
            SysAutoCodeResult bo = new SysAutoCodeResult();
            bo.setRuleId(rule.getRuleId());
            bo.setLastInputChar(inputChar);
            List<SysAutoCodeResult> results = iAutoCodeResultService.list(bo);

            SysAutoCodeResult rs;
            if (results.isEmpty()) {
                rs = new SysAutoCodeResult();
                rs.setRuleId(rule.getRuleId());
                rs.setGenDate(DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss"));
                rs.setLastResult(autoCode);
                rs.setGenIndex(1);
                rs.setLastSerialNo(Integer.parseInt(serialNo));
                rs.setLastInputChar(inputChar);
                iAutoCodeResultService.saveAutoCodeResult(rs);
            } else {
                rs = results.get(0);
                rs.setLastResult(autoCode);
                rs.setGenDate(DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss"));
                rs.setGenIndex(rs.getGenIndex() + 1);
                rs.setLastSerialNo(Integer.parseInt(serialNo));
                rs.setLastInputChar(inputChar);
                iAutoCodeResultService.updateAutoCodeResult(rs);
            }
        }
    }
}
