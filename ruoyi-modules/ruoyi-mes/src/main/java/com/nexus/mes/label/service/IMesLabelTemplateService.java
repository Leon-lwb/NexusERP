package com.nexus.mes.label.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.domain.vo.MesLabelTemplateVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 标签打印模板 Service 接口
 *
 * @author Nexus
 */
public interface IMesLabelTemplateService {

    /** 分页查询 */
    TableDataInfo<MesLabelTemplateVo> selectPageList(MesLabelTemplateBo bo, PageQuery pageQuery);

    /** 详情 */
    MesLabelTemplateVo selectById(Long templateId);

    /** 列表查询 */
    List<MesLabelTemplateVo> selectList(MesLabelTemplateBo bo);

    /** 新增 */
    Boolean insertByBo(MesLabelTemplateBo bo);

    /** 修改 */
    Boolean updateByBo(MesLabelTemplateBo bo);

    /** 批量删除 */
    Boolean deleteByIds(Collection<Long> ids);

    /**
     * 渲染预览: 样例数据渲染指令串(不发送打印机)
     *
     * @return {payload, charset, lang}
     */
    Map<String, Object> preview(Long templateId);

    /**
     * 测试打印: 样例数据渲染并同步直发打印机
     *
     * @return true 发送成功
     */
    boolean testPrint(Long templateId);

    /**
     * 人工补打(MES-LABEL-02): 按 SN + 模板重新渲染并直发打印机, 留痕 print_source=REPRINT。
     * <p>造码联动的自动打印带幂等键(同 SN 同模板已成功打印则跳过), 重打必须走本入口,
     * 保证"一个 SN 打了几张"在留痕里可查。</p>
     *
     * @param snId       SN ID
     * @param templateId 模板ID
     * @return true 发送成功
     */
    boolean reprint(Long snId, Long templateId);
}
