package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购申请 Mapper 接口
 * <p>
 * 提供采购申请主表的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author nexus
 */
@Mapper
public interface PuPurchaseRequisitionMapper extends BaseMapperPlus<PuPurchaseRequisition, PuPurchaseRequisitionVo>, BaseMapper<PuPurchaseRequisition> {

    /** 唯一键冲突后识别本租户已补偿的永久占用键；绕过 @TableLogic 的常规过滤。 */
    @Select("SELECT requisition_id FROM pu_purchase_requisition WHERE tenant_id = #{tenantId} "
        + "AND decision_idempotency_key = #{key} AND del_flag = '1' FOR UPDATE")
    Long findCompensatedDecisionId(@Param("tenantId") String tenantId, @Param("key") String key);
}
