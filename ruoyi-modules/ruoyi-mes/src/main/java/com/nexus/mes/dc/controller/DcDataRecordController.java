package com.nexus.mes.dc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.dc.domain.bo.DcDataRecordBo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.service.IDcDataRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES采集数据记录 Controller
 *
 * <p>属于MES 数据采集模块，提供数据记录相关的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/dc/data-record")
public class DcDataRecordController extends BaseController {

    private final IDcDataRecordService dcDataRecordService;

    /**
     * 分页查询数据记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:dc:record:list")
    @GetMapping("/list")
    public TableDataInfo<DcDataRecordVo> list(DcDataRecordBo bo, PageQuery pageQuery) {
        return dcDataRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 获取数据记录详情
     *
     * @param recordId 数据记录ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:record:query")
    @GetMapping("/{recordId}")
    public R<DcDataRecordVo> getInfo(@PathVariable Long recordId) {
        return R.ok(dcDataRecordService.selectById(recordId));
    }

    /**
     * 新增数据记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:record:add")
    @Log(title = "采集数据录入", businessType = BusinessType.INSERT)
    @PostMapping
    public R<DcDataRecordVo> add(@Validated @RequestBody DcDataRecordBo bo) {
        return R.ok(dcDataRecordService.insertByBo(bo));
    }
}
