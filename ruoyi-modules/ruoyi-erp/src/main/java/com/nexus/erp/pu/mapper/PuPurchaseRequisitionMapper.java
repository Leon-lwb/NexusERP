package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购申请 Mapper 接口
 * <p>
 * 提供采购申请主表的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author ruoyi
 */
@Mapper
public interface PuPurchaseRequisitionMapper extends BaseMapperPlus<PuPurchaseRequisition, PuPurchaseRequisitionVo>, BaseMapper<PuPurchaseRequisition> {
}
