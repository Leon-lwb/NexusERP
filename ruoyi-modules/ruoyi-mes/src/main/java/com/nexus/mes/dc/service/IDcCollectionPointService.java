package com.nexus.mes.dc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.dc.domain.bo.DcCollectionPointBo;
import com.nexus.mes.dc.domain.vo.DcCollectionPointVo;

/**
 * MES数据采集点 Service 业务层接口
 * <p>
 * 采集点类型: MANUAL(手动)/AUTO(自动,预留)
 * 数据类型: NUMERIC/TEXT/ENUM
 *
 * @author Nexus
 */
public interface IDcCollectionPointService {

    /** 状态: 启用 */
    String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 停用 */
    String STATUS_INACTIVE = "INACTIVE";

    /** 采集点类型: 手动 */
    String POINT_TYPE_MANUAL = "MANUAL";
    /** 采集点类型: 自动(预留) */
    String POINT_TYPE_AUTO = "AUTO";

    /** 数据类型: 数值 */
    String DATA_TYPE_NUMERIC = "NUMERIC";
    /** 数据类型: 文本 */
    String DATA_TYPE_TEXT = "TEXT";
    /** 数据类型: 枚举 */
    String DATA_TYPE_ENUM = "ENUM";

    /**
     * 分页查询采集点列表
     */
    TableDataInfo<DcCollectionPointVo> selectPageList(DcCollectionPointBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取采集点详情
     */
    DcCollectionPointVo selectById(Long pointId);

    /**
     * 新增采集点
     */
    DcCollectionPointVo insertByBo(DcCollectionPointBo bo);

    /**
     * 编辑采集点
     */
    DcCollectionPointVo updateByBo(DcCollectionPointBo bo);

    /**
     * 批量删除采集点
     */
    Boolean deleteByIds(Long[] ids);
}
