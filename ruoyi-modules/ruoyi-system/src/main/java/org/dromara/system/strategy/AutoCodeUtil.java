package org.dromara.system.strategy;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import org.dromara.system.domain.SysAutoCodePart;
import org.dromara.system.domain.SysAutoCodeResult;
import org.dromara.system.domain.SysAutoCodeRule;
import org.dromara.system.domain.enums.PartTypeEnum;
import org.dromara.system.service.ISysAutoCodePartService;
import org.dromara.system.service.ISysAutoCodeResultService;
import org.dromara.system.service.ISysAutoCodeRuleService;
import org.dromara.common.core.utils.StringUtils;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自动编码工具类
 *
 * @author wanghuan
 */
@Service
public class AutoCodeUtil {
    public static ThreadLocal<Boolean> threadLocal = new ThreadLocal<>();

    @Autowired
    private ISysAutoCodeRuleService iAutoCodeRuleService;

    @Autowired
    private ISysAutoCodePartService iAutoCodePartService;

    @Autowired
    private ISysAutoCodeResultService iAutoCodeResultService;

    @Autowired
    private PartTypeHandler partTypeHandler;

    private String lastSerialNo;

    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String genSerialCode(String ruleCode, String inputCharacter) {

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
        parts.forEach(codePart -> {
            codePart.setInputCharacter(inputCharacter);
            String partStr = partTypeHandler.choiceExecute(codePart);

            if (StringUtils.equals(codePart.getPartType(), PartTypeEnum.PART_TYPE_SERIALNO.getCode())) {
                lastSerialNo = partStr;
            }
            buff.append(partStr);
        });

        Assert.notBlank(buff.toString(), "规则：[{}]生成的编码为空！", ruleCode);

        String autoCode = paddingStr(rule, buff);

        saveAutoCodeResult(rule, autoCode, inputCharacter);
        return autoCode;
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

    private void saveAutoCodeResult(SysAutoCodeRule rule, String autoCode, String inputChar) {
        Boolean flag = threadLocal.get();
        if (flag != null && flag) {
            SysAutoCodeResult rs = new SysAutoCodeResult();
            rs.setRuleId(rule.getRuleId());
            rs.setGenDate(DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss"));
            rs.setLastResult(autoCode);
            rs.setGenIndex(1);
            rs.setLastSerialNo(Integer.parseInt(lastSerialNo));
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
                rs.setLastSerialNo(Integer.parseInt(lastSerialNo));
                rs.setLastInputChar(inputChar);
                iAutoCodeResultService.saveAutoCodeResult(rs);
            } else {
                rs = results.get(0);
                rs.setLastResult(autoCode);
                rs.setGenDate(DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss"));
                rs.setGenIndex(rs.getGenIndex() + 1);
                rs.setLastSerialNo(Integer.parseInt(lastSerialNo));
                rs.setLastInputChar(inputChar);
                iAutoCodeResultService.updateAutoCodeResult(rs);
            }
        }
    }
}
