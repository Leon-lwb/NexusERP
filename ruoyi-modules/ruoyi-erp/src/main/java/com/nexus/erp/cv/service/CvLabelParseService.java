package com.nexus.erp.cv.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.cv.domain.DetectDtos.OcrItem;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物料标签 OCR 解析服务
 * <p>将 detectProduct 的 OCR 文本按可配置规则提取 料号/批次/SN。标签格式待定期间,
 * 内置一套宽松通用规则(覆盖现有编码规则:物料编码、B{yyyyMMdd}…批次、SN 开头序列号),
 * 样例标签照片到位后(M2 验收)只需调整 {@link #PATTERNS} 即可,不改业务链路。</p>
 * <p>料号识别后与 {@link BdMaterial} 主数据交叉校验(material_code 精确匹配,
 * 命中率即 M2 验收口径),校验不通过仅在 result 中标记 mismatch 提示,不阻断返回。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("erp")
public class CvLabelParseService {

    private final BdMaterialMapper bdMaterialMapper;

    /**
     * 标签字段规则(可配置点)
     * <p>key: 字段名;value: 正则。匹配顺序 = 文本行优先级:
     * 优先"键值对"形态(如「料号: MC-301A-03」),退化兜底纯编码形态。</p>
     */
    private static final Map<String, List<Pattern>> PATTERNS = buildPatterns();

    /**
     * 批次数值归一化:提取 barcode 行中的批次号
     * <p>「批次号」与「批次条码」可能同行(如 "批次 B20260715"),行文本再拆两个子规则</p>
     */
    private static Map<String, List<Pattern>> buildPatterns() {
        Map<String, List<Pattern>> map = new LinkedHashMap<>();
        // 料号: 键值对优先;兜底宽松匹配(3-30 位大写字母/数字/连接符,避免与批次/SN 混淆)
        map.put("materialCode", List.of(
            Pattern.compile("(?:料号|物料编码|物料号|PN|Material)\\s*[：:]?\\s*([A-Za-z0-9][A-Za-z0-9\\-]{2,29})"),
            Pattern.compile("(?:^|\\s)([A-Z]{1,6}[0-9]{3,10}[A-Z0-9\\-]{0,12})(?=\\s|$)")
        ));
        // 批次: 以 B/P 开头 + 8 位日期 + 流水(符合 BATCH_PURCHASE/BATCH_PRODUCTION 规则);同时兼容纯数字批次(如 20260715001)
        map.put("batchCode", List.of(
            Pattern.compile("(?:批次|批号|Lot)\\s*[：:]?\\s*([A-Za-z]?\\d{8}\\d{1,6})"),
            Pattern.compile("(?:^|\\s)([BP]\\d{8}\\d{1,6})(?=\\s|$)"),
            Pattern.compile("(?:批次|批号)\\s*[：:]?\\s*(\\d{6,})")
        ));
        // 序列号: SN 前缀(SN_PURCHASE 规则)或 物料尾号+年月+流水
        map.put("snCode", List.of(
            Pattern.compile("(?:SN|序列号|Serial)\\s*[：:]?\\s*(SN?[A-Za-z0-9\\-]{5,})"),
            Pattern.compile("(?:^|\\s)(SN[A-Za-z0-9]{6,})(?=\\s|$)")
        ));
        return map;
    }

    /**
     * 解析 OCR 结果,提取标签字段并交叉校验物料主数据
     *
     * @param ocrResults 检测接口返回的 OCR 文本项;为空返回空结果
     * @return 解析结果 Map:
     *         - matched(boolean)    是否命中物料主数据
     *         - matchedMaterial(可选) 命中的物料对象
     *         - materialCode/batchCode/snCode(可选) 提取的字段
     *         - rawTexts(List)      原始 OCR 文本(调试用)
     */
    public Map<String, Object> parse(List<OcrItem> ocrResults) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(ocrResults)) {
            result.put("matched", false);
            result.put("rawTexts", List.of());
            return result;
        }
        List<String> rawTexts = new ArrayList<>();
        for (OcrItem item : ocrResults) {
            if (item != null && StrUtil.isNotBlank(item.getText())) {
                rawTexts.add(item.getText());
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, List<Pattern>> entry : PATTERNS.entrySet()) {
            fields.put(entry.getKey(), extract(rawTexts, entry.getValue()));
        }
        result.put("rawTexts", rawTexts);
        fields.forEach(result::put);
        // 料号交叉校验主数据
        String materialCode = fields.get("materialCode");
        BdMaterial material = null;
        if (StrUtil.isNotBlank(materialCode)) {
            material = bdMaterialMapper.selectOne(
                Wrappers.<BdMaterial>lambdaQuery()
                    .eq(BdMaterial::getMaterialCode, materialCode.trim())
                    .last("LIMIT 1")
            );
        }
        result.put("matchedMaterial", material);
        result.put("matched", material != null);
        return result;
    }

    /**
     * 按规则顺序提取字段值(取首个命中;同一字段多条规则按序尝试)
     *
     * @param texts     OCR 行文本
     * @param patterns  该字段的正则序列
     */
    private String extract(List<String> texts, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            for (String text : texts) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String value = matcher.group(1) != null ? matcher.group(1) : matcher.group();
                    value = value.replaceAll("[\\s:：]", "").trim();
                    if (StrUtil.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
