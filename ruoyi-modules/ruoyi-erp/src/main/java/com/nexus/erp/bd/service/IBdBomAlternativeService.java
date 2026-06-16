package com.nexus.erp.bd.service;

import com.nexus.erp.bd.domain.bo.BdBomAlternativeBo;
import com.nexus.erp.bd.domain.vo.BdBomAlternativeVo;

import java.util.List;

/**
 * BOM替代料Service接口
 *
 * @author nexus
 */
public interface IBdBomAlternativeService {

    /**
     * 查询某BOM明细行的替代料列表
     */
    List<BdBomAlternativeVo> listByDetailId(Long detailId);

    /**
     * 查询某BOM的所有替代料
     */
    List<BdBomAlternativeVo> listByBomId(Long bomId);

    /**
     * 批量保存替代料（先删后插）
     */
    int saveBatch(Long detailId, Long bomId, List<BdBomAlternativeBo> list);

    /**
     * 删除替代料
     */
    int deleteByIds(Long[] ids);

    /**
     * 获取可用替代料（考虑生效/失效日期）
     */
    List<BdBomAlternativeVo> getAvailableAlternatives(Long detailId);
}
