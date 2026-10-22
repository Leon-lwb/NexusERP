package com.nexus.mes.flash.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.bo.TestItemBo;
import com.nexus.mes.flash.domain.vo.TestItemVo;

import java.util.List;

/**
 * MES测试项标准 Service 接口
 * <p>按物料/固件范围配置测试标准(限值/目标/ENUM合格值/必测), 供测试记录开单时快照引用。</p>
 *
 * @author Nexus
 */
public interface ITestItemService {

    /** 业务服务名 */
    String SERVICE_NAME = "mesTestItemService";

    /** 数据类型-数值 */
    String DATA_TYPE_NUMERIC = "NUMERIC";
    /** 数据类型-文本 */
    String DATA_TYPE_TEXT = "TEXT";
    /** 数据类型-枚举 */
    String DATA_TYPE_ENUM = "ENUM";

    /**
     * 分页查询测试项标准(条件: 编码/名称/物料/固件/类型/是否必测)
     */
    TableDataInfo<TestItemVo> selectPageList(TestItemBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询测试项详情
     */
    TestItemVo selectById(Long testItemId);

    /**
     * 新增测试项(同物料+同固件+同编码唯一, 限值/合格值校验)
     */
    Boolean insertByBo(TestItemBo bo);

    /**
     * 修改测试项
     */
    Boolean updateByBo(TestItemBo bo);

    /**
     * 批量删除测试项(被测试记录引用时禁止删除)
     */
    Boolean deleteByIds(Long[] testItemIds);

    /** 按物料+固件范围查询可用测试项(开单快照/前端下拉) */
    List<TestItemVo> selectByMaterial(Long materialId, Long firmwareId);
}