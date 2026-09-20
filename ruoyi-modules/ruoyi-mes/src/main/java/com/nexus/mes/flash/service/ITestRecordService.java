package com.nexus.mes.flash.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.bo.TestRecordBo;
import com.nexus.mes.flash.domain.vo.TestRecordVo;

import java.util.List;
import java.util.Map;

/**
 * MES测试记录 Service 接口 (SN级全检)
 * <p>一次测试(同一 test_no)多行: 该 SN 该测试物料范围内每个测试项一行;
 * beginTest 开单 -> 自动匹配测试项落待测行(含限值/ENUM快照) ->
 * reportTestRows 填报实测值并自动判定 PASS/FAIL。</p>
 *
 * @author Nexus
 */
public interface ITestRecordService {

    /** 业务服务名 */
    String SERVICE_NAME = "mesTestRecordService";

    /** 结果-通过/失败 */
    String RESULT_PASS = "PASS";
    String RESULT_FAIL = "FAIL";

    /**
     * 分页查询测试记录(条件: 测试单号/SN/工单/固件/测试项/结果/模式/时间区间)
     */
    TableDataInfo<TestRecordVo> selectPageList(TestRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询测试记录详情
     */
    TestRecordVo selectById(Long testRecordId);

    /**
     * 开单: 按 SN 物料匹配测试项(固件范围过滤: 0=通用/指定固件)落待测行,
     * 自动生成测试单号(MES_TEST_NO), 快照限值/ENUM等; 返回生成的待测行(testNo 一致)
     */
    List<TestRecordVo> beginTest(TestRecordBo bo);

    /**
     * 填报: 批量提交实测值, 按行自动判定 PASS/FAIL(NUMERIC限值/TEXT非空/ENUM合格值), 返回判定后的行
     */
    List<TestRecordVo> reportTestRows(List<TestRecordBo> rows);

    /**
     * 批量删除测试记录行(仅未判定(result 为空)的草稿行可删, 已判定行保留追溯)
     */
    Boolean deleteByIds(Long[] testRecordIds);

    /**
     * 按测试单号查询全部行(详情/追溯)
     */
    List<TestRecordVo> selectByTestNo(String testNo);

    /**
     * 统计数据(测试总次数/通过率), 供概览
     */
    Map<String, Object> summary(TestRecordBo bo);

    /**
     * AUTO 设备批量上报测试结果(开放接口, 宽松模式):
     * 每 SN 按测试项编码匹配物料标准自动判定落库(test_mode=AUTO),
     * 返回 {success, failed:[{snCode,reason}], details:[{snCode,testNo,judged}]}
     */
    Map<String, Object> reportTestAutoBatch(List<com.nexus.mes.flash.domain.bo.TestReportBo> list);

    /**
     * 该 SN 是否存在未处置的 FAIL 测试行(MES-TEST-04, 供工序/完工门禁查询)。
     * <p>判定 FAIL 时会自动创建 NCR; 已判定行不可覆盖改写, 故存在 FAIL 行即视为未处置
     * (精确到 NCR 关闭状态需 NCR 侧按 SN 查询接口支持)。</p>
     *
     * @param snId SN ID
     * @return true 存在未处置 FAIL 行
     */
    boolean hasUnhandledFail(Long snId);
}