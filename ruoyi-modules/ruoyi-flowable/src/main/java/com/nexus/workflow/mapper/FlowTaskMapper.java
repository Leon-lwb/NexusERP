package com.nexus.workflow.mapper;

import org.apache.ibatis.annotations.Param;


public interface FlowTaskMapper {

    int querySubTaskByParentTaskId(@Param("parentTaskId")  String parentTaskId);
}
