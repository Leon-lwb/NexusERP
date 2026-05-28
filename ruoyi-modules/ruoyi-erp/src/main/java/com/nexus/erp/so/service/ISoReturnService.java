package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoReturnBo;
import com.nexus.erp.so.domain.vo.SoReturnVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

public interface ISoReturnService extends WfCallBackServiceI {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_APPROVING = "APPROVING";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_RECEIVED = "RECEIVED";
    String STATUS_CLOSED = "CLOSED";

    TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery);

    List<SoReturnVo> selectList(SoReturnBo bo);

    SoReturnVo selectById(Long id);

    int insertByBo(SoReturnBo bo);

    int updateByBo(SoReturnBo bo);

    int deleteByIds(Long[] ids);

    void confirm(Long returnId);

    /**
     * 执行退货（退货入库）
     */
    void execute(Long returnId);

    /**
     * 发起审批流程
     */
    boolean submit(String returnIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String returnIdStr);
}
