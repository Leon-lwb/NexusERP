package com.nexus.workflow.domain.vo;

import com.nexus.workflow.domain.WfDeployForm;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部署实例和表单关联视图对象
 *
 * @author nbacheng
 * @createTime 2022/7/17 18:29
 */
@Data
@AutoMapper(target = WfDeployForm.class)
public class WfDeployFormVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程部署主键
     */
    private String deployId;

    /**
     * 表单Key
     */
    private String formKey;

    /**
     * 节点Key
     */
    private String nodeKey;

    /**
     * 表单名称
     */
    private String formName;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 表单内容
     */
    private String content;
}
