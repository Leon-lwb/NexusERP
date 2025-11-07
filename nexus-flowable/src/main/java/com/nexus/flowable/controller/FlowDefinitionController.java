package com.nexus.flowable.controller;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysRole;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.enums.BusinessType;
import com.nexus.flowable.domain.SysDeployForm;
import com.nexus.flowable.domain.SysExpression;
import com.nexus.flowable.domain.dto.FlowSaveXmlVo;
import com.nexus.flowable.service.IFlowDefinitionService;
import com.nexus.flowable.service.ISysDeployFormService;
import com.nexus.flowable.service.ISysExpressionService;
import com.nexus.system.service.ISysRoleService;
import com.nexus.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 工作流程定义
 * </p>
 *
 * @author Tony
 * @date 2021-04-03
 */
@Slf4j
@Tag(name = "流程定义")
@RestController
@RequestMapping("/flowable/definition")
public class FlowDefinitionController extends BaseController {

    @Autowired
    private IFlowDefinitionService flowDefinitionService;

    @Autowired
    private ISysUserService userService;

    @Resource
    private ISysRoleService sysRoleService;
    @Resource
    private ISysExpressionService sysExpressionService;

    @Resource
    private ISysDeployFormService sysDeployFormService;

    @GetMapping(value = "/list")
    @Operation(summary = "流程定义列表")
    public TableDataInfo list(
            @RequestParam Integer pageNum,
            @RequestParam Integer pageSize,
            @RequestParam(required = false) String name) {
        return getDataTable(flowDefinitionService.list(name, pageNum, pageSize));
    }

    @Operation(summary = "导入流程文件")
    @PostMapping("/import")
    public AjaxResult importFile(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            MultipartFile file) {
        InputStream in = null;
        try {
            in = file.getInputStream();
            flowDefinitionService.importFile(name, category, in);
        } catch (Exception e) {
            log.error("导入失败:", e);
            return AjaxResult.success(e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("关闭输入流出错", e);
            }
        }

        return AjaxResult.success("导入成功");
    }

    @Operation(summary = "读取xml文件")
    @GetMapping("/readXml/{deployId}")
    public AjaxResult readXml(@PathVariable String deployId) {
        try {
            return flowDefinitionService.readXml(deployId);
        } catch (Exception e) {
            return AjaxResult.error("加载xml文件异常");
        }

    }

    @Operation(summary = "读取图片文件")
    @GetMapping("/readImage/{deployId}")
    public void readImage(@PathVariable String deployId, HttpServletResponse response) {
        OutputStream os = null;
        BufferedImage image = null;
        try {
            image = ImageIO.read(flowDefinitionService.readImage(deployId));
            response.setContentType("image/png");
            os = response.getOutputStream();
            if (image != null) {
                ImageIO.write(image, "png", os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Operation(summary = "保存流程设计器内的xml文件")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@RequestBody FlowSaveXmlVo vo) {
        InputStream in = null;
        try {
            in = new ByteArrayInputStream(vo.getXml().getBytes(StandardCharsets.UTF_8));
            flowDefinitionService.importFile(vo.getName(), vo.getCategory(), in);
        } catch (Exception e) {
            log.error("导入失败:", e);
            return AjaxResult.error(e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("关闭输入流出错", e);
            }
        }

        return AjaxResult.success("导入成功");
    }

    @Operation(summary = "发起流程")
    @Log(title = "发起流程", businessType = BusinessType.INSERT)
    @PostMapping("/start/{procDefId}")
    public AjaxResult start(@PathVariable String procDefId, @RequestBody Map<String, Object> variables) {
        return flowDefinitionService.startProcessInstanceById(procDefId, variables);
    }

    @Operation(summary = "激活或挂起流程定义")
    @Log(title = "激活/挂起流程", businessType = BusinessType.UPDATE)
    @PutMapping(value = "/updateState")
    public AjaxResult updateState(@RequestParam Integer state, @RequestParam String deployId) {
        flowDefinitionService.updateState(state, deployId);
        return AjaxResult.success();
    }

    @Operation(summary = "删除流程")
    @Log(title = "删除流程", businessType = BusinessType.DELETE)
    @DeleteMapping(value = "/{deployIds}")
    public AjaxResult delete(@PathVariable String[] deployIds) {
        for (String deployId : deployIds) {
            flowDefinitionService.delete(deployId);
        }
        return AjaxResult.success();
    }

    @Operation(summary = "指定流程办理人员列表")
    @GetMapping("/userList")
    public AjaxResult userList(SysUser user) {
        List<SysUser> list = userService.selectUserList(user);
        return AjaxResult.success(list);
    }

    @Operation(summary = "指定流程办理组列表")
    @GetMapping("/roleList")
    public AjaxResult roleList(SysRole role) {
        List<SysRole> list = sysRoleService.selectRoleList(role);
        return AjaxResult.success(list);
    }

    @Operation(summary = "指定流程达式列表")
    @GetMapping("/expList")
    public AjaxResult expList(SysExpression sysExpression) {
        List<SysExpression> list = sysExpressionService.selectSysExpressionList(sysExpression);
        return AjaxResult.success(list);
    }

    /**
     * 挂载流程表单
     */
    @Log(title = "流程表单", businessType = BusinessType.INSERT)
    @PostMapping("/addDeployForm")
    public AjaxResult addDeployForm(@RequestBody SysDeployForm sysDeployForm) {
        return toAjax(sysDeployFormService.insertSysDeployForm(sysDeployForm));
    }

}
