package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购合同「正文签署状态」批量补充器（P2b-2 采购合同接入，复用 {@code SoContractSignStatusFiller} 同一模式）。
 *
 * <p><b>为什么要单独一个 Bean：</b>{@code es_sign_document} 只存在于 ry-vue 库（主数据源 master），
 * 而采购合同业务表在 ry-erp 库（{@code PuContractServiceImpl} 是类级 {@code @DS("erp")}）。
 * {@code @DS} 是线程级上下文栈，同一个类里查两张不同库的表只能手工 push/poll；
 * 这里用类级 {@code @DS("master")} 把「切到 master 查签章表」收敛成一个 Bean 方法。</p>
 *
 * <p><b>无 N+1：</b>把整页的 {@code contractId} 收成一个集合，一次 {@code biz_id IN (...)} 查询，
 * 再按 {@code bizId → sign_status} 回填。</p>
 *
 * <p><b>只取当前生效版本：</b>同一 {@code (租户, bizType, bizId)} 下 {@code is_current='1'} 最多一行，
 * 换版时旧行置 '0'；列表要回答的是「这份合同现在的正文签没签」，历史版本的签署状态不该覆盖它，
 * 故查询带上 {@code is_current='1'}，map 天然 1:1。无正文的行保持 {@code null}（前端显示占位符）。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@DS("master")
public class PuContractSignStatusFiller {

    /** {@code is_current}：当前生效版本 */
    private static final String IS_CURRENT_YES = "1";

    /** 待签文档正文 Mapper（ruoyi-common-sign，表在主数据源 master 库） */
    private final EsSignDocumentMapper signDocumentMapper;

    /**
     * 为采购合同列表批量补充正文签署状态（{@code es_sign_document.sign_status}）。
     *
     * <p>整页只查一次库；{@code records} 为空、{@code bizType} 为空或整页没有 ID 时不查库。</p>
     *
     * @param records 合同视图对象列表（原地回填 {@code docSignStatus}）
     * @param bizType 业务类型（采购合同固定 {@code SignBizType.PU_CONTRACT}）
     */
    public void fillDocSignStatus(List<PuContractVo> records, String bizType) {
        if (records == null || records.isEmpty() || StringUtils.isBlank(bizType)) {
            return;
        }
        Set<Long> bizIds = new LinkedHashSet<>();
        for (PuContractVo vo : records) {
            if (vo.getContractId() != null) {
                bizIds.add(vo.getContractId());
            }
        }
        if (bizIds.isEmpty()) {
            return;
        }
        // 只取回填需要的两列；租户条件由多租户插件自动追加，del_flag 由 @TableLogic 自动追加
        List<EsSignDocument> docs = signDocumentMapper.selectList(Wrappers.<EsSignDocument>lambdaQuery()
            .select(EsSignDocument::getBizId, EsSignDocument::getSignStatus)
            .eq(EsSignDocument::getBizType, bizType)
            .in(EsSignDocument::getBizId, bizIds)
            .eq(EsSignDocument::getIsCurrent, IS_CURRENT_YES));
        Map<Long, String> signStatusMap = new HashMap<>(docs.size());
        for (EsSignDocument doc : docs) {
            signStatusMap.put(doc.getBizId(), doc.getSignStatus());
        }
        for (PuContractVo vo : records) {
            // 无正文 → 不设置，保持 null，前端列表列显示占位符而不是误导性的「未签」
            vo.setDocSignStatus(signStatusMap.get(vo.getContractId()));
        }
    }
}
