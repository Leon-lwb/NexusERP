package com.nexus.mes.sop.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.sop.domain.bo.SopProcessBindingBo;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;
import com.nexus.mes.sop.domain.vo.SopProcessBindingVo;

import java.util.List;
import java.util.Map;

/**
 * 工序SOP绑定 Service 业务层接口
 * <p>
 * 绑定到 bd_process.process_id (工序主数据, 跨工艺路线复用)。
 * is_default=1 时同工序其他绑定排他置0 (CAS)。
 * 提供 P0-3 / 工位终端的契约方法。
 * </p>
 *
 * @author Nexus
 */
public interface ISopProcessBindingService {

    /**
     * 分页查询绑定列表
     *
     * @param bo         绑定查询条件
     * @param pageQuery  分页参数
     * @return 分页列表
     */
    TableDataInfo<SopProcessBindingVo> selectPageList(SopProcessBindingBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取绑定详情
     *
     * @param bindingId 绑定ID
     * @return 绑定详情
     */
    SopProcessBindingVo selectById(Long bindingId);

    /**
     * 新增绑定(is_default=1 时同工序排他置0; 前端选择器带入 process_code/name/category 冗余)
     *
     * @param bo 绑定业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(SopProcessBindingBo bo);

    /**
     * 批量删除绑定
     *
     * @param ids 绑定ID集合
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 契约: 查询工序绑定的SOP列表(is_default 优先 + sort_order)
     *
     * @param processId 工序ID
     * @return 绑定列表(默认优先+排序)
     */
    List<SopProcessBindingVo> selectByProcess(Long processId);

    /**
     * 契约: 获取工序的默认SOP(is_default=1, 含文件签名URL);无默认则返回 sort_order 首条
     * <p>供 P0-3 / 查看器调用</p>
     *
     * @param processId 工序ID
     * @return 默认SOP文档(含文件签名URL), 无绑定返回 null
     */
    SopDocumentVo getSopForProcess(Long processId);

    /**
     * 契约: 按流转卡工序记录查SOP(经 ProFlowCardStep.processId, MES库内不跨库)
     *
     * @param stepRecordId 流转卡工序记录ID
     * @return SOP文档
     */
    SopDocumentVo getSopForStep(Long stepRecordId);

    /**
     * 契约: 按物料查全部工序SOP(工单开工预加载)
     * <p>物料 -> ERP 当前工艺路线 -> 路线步骤 -> 各 processId -> 绑定SOP。
     * 返回 Map<processId, SopDocumentVo>(仅含已绑定SOP的工序)</p>
     *
     * @param materialId 物料ID
     * @return 工序ID到SOP文档的映射
     */
    Map<Long, SopDocumentVo> getSopForMaterial(Long materialId);
}
