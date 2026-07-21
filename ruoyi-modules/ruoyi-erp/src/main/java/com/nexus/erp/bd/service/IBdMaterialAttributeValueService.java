package com.nexus.erp.bd.service;

import com.nexus.erp.bd.domain.bo.BdMaterialAttributeValueBo;
import com.nexus.erp.bd.domain.vo.BdMaterialAttributeValueVo;

import java.util.List;

/**
 * 物料扩展属性值Service接口
 *
 * @author nexus
 */
public interface IBdMaterialAttributeValueService {

    /**
     * 按物料查询属性值列表
     */
    List<BdMaterialAttributeValueVo> listByMaterial(Long materialId);

    /**
     * 按物料整体保存属性值（替换语义：删除既有后插入提交值，含必填校验）。
     *
     * @param materialId 物料ID
     * @param values     属性值列表（attrId + 分类型值）
     * @return 插入行数
     */
    int saveByMaterial(Long materialId, List<BdMaterialAttributeValueBo> values);

    /**
     * 按物料删除全部属性值
     */
    int deleteByMaterial(Long materialId);
}
