package com.nexus.mes.label.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.domain.vo.MesLabelTemplateVo;
import com.nexus.mes.label.domain.vo.LabelPrintTaskVo;

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
     * 保留历史二参数方法签名，但缺少原任务和原因的补打现在明确拒绝。
     *
     * @param snId SN ID
     * @param templateId 模板 ID
     * @return 仅表示 TCP 发送成功，未确认实际出纸
     */
    boolean reprint(Long snId, Long templateId);

    /**
     * 显式补打：原任务引用及原因必填，原任务存在快照时复用其冻结指令，逐次追加审计。
     *
     * @param snId SN ID
     * @param templateId 模板 ID
     * @param originalPrintLogId 本租户、本 SN、本模板的原任务 ID
     * @param reprintReason 非空补打原因，最多 500 字符
     * @return 仅表示 TCP 发送成功，未确认实际出纸
     */
    boolean reprint(Long snId, Long templateId, Long originalPrintLogId, String reprintReason);
    /** 持久化测试打印，返回任务状态；请求ID用于人工提交幂等。 */
    LabelPrintTaskVo testPrintTask(Long templateId, String requestId);

    /** 持久化显式补打，必须关联原任务和原因。 */
    LabelPrintTaskVo reprintTask(Long snId, Long templateId, Long originalPrintLogId, String reprintReason, String requestId);

    /** 按当前租户查询任务状态，不暴露冻结指令。 */
    LabelPrintTaskVo printTask(Long taskId);
}
