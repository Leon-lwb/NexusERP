package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.domain.vo.WmSnVo;

import java.util.List;

/**
 * 序列号管理Service接口
 *
 * @author nexus
 * @date 2026-06-02
 */
public interface IWmSnService {

    /**
     * 分页查询序列号列表
     *
     * @param bo        序列号业务对象
     * @param pageQuery 分页参数
     * @return 序列号分页集合
     */
    TableDataInfo<WmSnVo> selectPageList(WmSnBo bo, PageQuery pageQuery);

    /**
     * 查询序列号列表
     *
     * @param bo 序列号业务对象
     * @return 序列号集合
     */
    List<WmSnVo> selectList(WmSnBo bo);

    /**
     * 查询序列号
     *
     * @param id 序列号主键
     * @return 序列号
     */
    WmSnVo selectById(Long id);

    /**
     * 通过统一注册新增正式身份，必须有commandId且初始状态为REGISTERED
     *
     * @param bo 序列号业务对象
     * @return 结果
     */
    int insertByBo(WmSnBo bo);

    /**
     * 仅修改备注等非事实元数据
     *
     * @param bo 序列号业务对象
     * @return 结果
     */
    int updateByBo(WmSnBo bo);

    /**
     * 已停用：正式号码不可删除或回收，须通过业务单据处理
     *
     * @param ids 需要删除的序列号主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据物料和状态查询序列号列表
     *
     * @param materialId 物料ID
     * @param status     状态
     * @return 序列号集合
     */
    List<WmSnVo> selectByMaterialAndStatus(Long materialId, String status);

    /**
     * 绑定序列号到库存记录
     *
     * @param snId        序列号ID
     * @param stockId     库存记录ID
     * @param warehouseId 仓库ID
     * @param locationId  库位ID
     */
    void bindStock(Long snId, Long stockId, Long warehouseId, Long locationId);

    /**
     * 解绑序列号与库存记录
     *
     * @param snId 序列号ID
     */
    void unbindStock(Long snId);

    /**
     * 无命令旧签名已停用，请使用带commandId的BO重载
     *
     * @param materialId    物料ID
     * @param quantity      生成数量
     * @param batchId       关联批次ID（可空）
     * @param ruleId        生成规则ID
     * @param sourceDocId   来源单据ID
     * @param sourceDocType 来源单据类型
     * @param sourceDocNo   来源单据编号
     */
    @Deprecated
    void batchGenerate(Long materialId, Integer quantity, Long batchId, Long ruleId, Long sourceDocId, String sourceDocType, String sourceDocNo);

    /** 按显式commandId调用ERP注册服务预留完整号段，不直接产生库存。 */
    void batchGenerate(WmSnBo bo);

    /**
     * 变更序列号状态
     *
     * @param snId   序列号ID
     * @param status 目标状态
     */
    void changeStatus(Long snId, String status);
}
