package com.nexus.bd.controller;

import com.nexus.bd.domain.BdClient;
import com.nexus.bd.service.IBdClientService;
import com.nexus.common.config.RuoYiConfig;
import com.nexus.common.utils.file.FileUploadUtils;
import com.nexus.common.utils.file.MimeTypeUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户信息Controller
 *
 * @author nexus
 * @date 2025-09-18
 */
@Api(tags = "客户信息Controller")
@RestController
@RequestMapping("/core/bd")
public class BdClientController extends BaseController
{
    @Autowired
    private IBdClientService bdClientService;

    /**
     * 查询客户信息列表
     */
    @ApiOperation("查询客户信息列表")
    @PreAuthorize("@ss.hasPermi('core:bd:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdClient bdClient)
    {
        startPage();
        List<BdClient> list = bdClientService.selectBdClientList(bdClient);
        return getDataTable(list);
    }

    /**
     * 导出客户信息列表
     */
    @ApiOperation("导出客户信息列表")
    @PreAuthorize("@ss.hasPermi('core:bd:export')")
    @Log(title = "客户信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdClient bdClient)
    {
        List<BdClient> list = bdClientService.selectBdClientList(bdClient);
        ExcelUtil<BdClient> util = new ExcelUtil<BdClient>(BdClient.class);
        util.exportExcel(response, list, "客户信息数据");
    }

    /**
     * 获取客户信息详细信息
     */
    @ApiOperation("获取客户信息列表")
    @PreAuthorize("@ss.hasPermi('core:bd:query')")
    @GetMapping(value = "/{clientId}")
    public AjaxResult getInfo(@PathVariable("clientId") Long clientId)
    {
        return success(bdClientService.selectBdClientByClientId(clientId));
    }

    /**
     * 新增客户信息
     */
    @ApiOperation("新增客户信息详细信息")
    @PreAuthorize("@ss.hasPermi('core:bd:add')")
    @Log(title = "客户信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdClient bdClient)
    {
        return toAjax(bdClientService.insertBdClient(bdClient));
    }

    /**
     * 修改客户信息
     */
    @ApiOperation("修改客户信息详细信息")
    @PreAuthorize("@ss.hasPermi('core:bd:edit')")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdClient bdClient)
    {
        return toAjax(bdClientService.updateBdClient(bdClient));
    }

    /**
     * 删除客户信息
     */
    @ApiOperation("删除客户信息信息")
    @PreAuthorize("@ss.hasPermi('core:bd:remove')")
    @Log(title = "客户信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{clientIds}")
    public AjaxResult remove(@PathVariable Long[] clientIds)
    {
        return toAjax(bdClientService.deleteBdClientByClientIds(clientIds));
    }

    @ApiOperation("上传客户logo")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('core:bd:edit')")
    @PostMapping("/uploadLogo")
    public AjaxResult uploadLogo(MultipartFile file, Long clientId)
    {
        if (!file.isEmpty()) {
            try {
                // 使用系统工具类上传文件，限制为图片类型
                String filePath = FileUploadUtils.upload(RuoYiConfig.getProfile() + "/client_logo/", file, MimeTypeUtils.IMAGE_EXTENSION, true);
                
                // 更新客户信息中的logo路径字段
                BdClient client = new BdClient();
                client.setClientId(clientId);
                client.setClientLogo(filePath);
                bdClientService.updateBdClient(client);
                
                return success("上传成功").put("filePath", filePath);
            } catch (Exception e) {
                return error("上传失败: " + e.getMessage());
            }
        }
        return error("上传文件为空");
    }
}
