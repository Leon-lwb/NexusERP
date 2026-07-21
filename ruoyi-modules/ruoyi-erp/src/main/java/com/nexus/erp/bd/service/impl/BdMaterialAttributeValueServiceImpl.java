package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.bd.domain.BdMaterialAttributeValue;
import com.nexus.erp.bd.domain.bo.BdMaterialAttributeValueBo;
import com.nexus.erp.bd.domain.vo.BdCategoryAttributeVo;
import com.nexus.erp.bd.domain.vo.BdMaterialAttributeValueVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdMaterialAttributeValueMapper;
import com.nexus.erp.bd.service.IBdCategoryAttributeService;
import com.nexus.erp.bd.service.IBdMaterialAttributeValueService;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料扩展属性值Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialAttributeValueServiceImpl implements IBdMaterialAttributeValueService {

    private final BdMaterialAttributeValueMapper baseMapper;
    private final IBdMaterialService bdMaterialService;
    private final IBdCategoryAttributeService bdCategoryAttributeService;
    /**
     * 查询物料的物料属性值列表
     *
     * @param materialId 物料ID
     * @return 结果列表
     */

    @Override
    public List<BdMaterialAttributeValueVo> listByMaterial(Long materialId) {
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdMaterialAttributeValue.class)
                .eq(BdMaterialAttributeValue::getMaterialId, materialId)
        );
    }

    /**
     * 替换语义保存：按物料删除既有值后插入提交值。
     * 必填属性缺值则抛业务异常；data_type 以分类模板为准（覆盖前端传值）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveByMaterial(Long materialId, List<BdMaterialAttributeValueBo> values) {
        BdMaterialVo material = bdMaterialService.selectById(materialId);
        if (material == null) {
            throw new ServiceException("物料不存在");
        }
        List<BdCategoryAttributeVo> attrs = bdCategoryAttributeService.selectListByCategory(material.getCategoryId());
        Map<Long, BdCategoryAttributeVo> attrMap = new HashMap<>();
        for (BdCategoryAttributeVo a : attrs) {
            attrMap.put(a.getAttrId(), a);
        }
        // 必填校验
        for (BdCategoryAttributeVo attr : attrs) {
            if (Long.valueOf(1L).equals(attr.getRequiredFlag())) {
                BdMaterialAttributeValueBo v = findValue(values, attr.getAttrId());
                if (isEmptyValue(v, attr.getDataType())) {
                    throw new ServiceException("属性'" + attr.getAttrName() + "'为必填，请填写值");
                }
            }
        }
        // 替换：删除既有，插入提交值
        baseMapper.delete(new LambdaQueryWrapper<BdMaterialAttributeValue>()
            .eq(BdMaterialAttributeValue::getMaterialId, materialId));
        int rows = 0;
        if (values == null) {
            return rows;
        }
        for (BdMaterialAttributeValueBo v : values) {
            BdCategoryAttributeVo attr = attrMap.get(v.getAttrId());
            if (attr == null) {
                // 属性不属于该物料分类，跳过（防止脏数据）
                continue;
            }
            BdMaterialAttributeValue entity = MapstructUtils.convert(v, BdMaterialAttributeValue.class);
            entity.setValueId(IdGeneratorUtil.nextLongId());
            entity.setMaterialId(materialId);
            entity.setAttrId(attr.getAttrId());
            entity.setDataType(attr.getDataType());
            rows += baseMapper.insert(entity);
        }
        return rows;
    }
    /**
     * 删除物料的全部物料属性值
     *
     * @param materialId 物料ID
     * @return 操作结果/影响行数
     */

    @Override
    public int deleteByMaterial(Long materialId) {
        return baseMapper.delete(new LambdaQueryWrapper<BdMaterialAttributeValue>()
            .eq(BdMaterialAttributeValue::getMaterialId, materialId));
    }

    private BdMaterialAttributeValueBo findValue(List<BdMaterialAttributeValueBo> values, Long attrId) {
        if (values == null) {
            return null;
        }
        for (BdMaterialAttributeValueBo v : values) {
            if (attrId != null && attrId.equals(v.getAttrId())) {
                return v;
            }
        }
        return null;
    }

    /**
     * 按 data_type 判断值是否为空
     */
    private boolean isEmptyValue(BdMaterialAttributeValueBo v, String dataType) {
        if (v == null) {
            return true;
        }
        if ("NUMBER".equals(dataType)) {
            return v.getNumericValue() == null;
        }
        if ("DATE".equals(dataType)) {
            return v.getDateValue() == null;
        }
        if ("BOOLEAN".equals(dataType)) {
            return v.getBoolValue() == null;
        }
        // STRING / ENUM
        return StringUtils.isBlank(v.getTextValue());
    }
}
