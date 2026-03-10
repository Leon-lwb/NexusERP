package com.nexus.common.core.page;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 表格分页数据对象
 * 
 * @author nexus
 */
public class TableDataInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 列表数据 */
    private List<?> rows;

    /** 消息状态码 */
    private int code;

    /** 消息内容 */
    private String msg;

    /** 附加数据（用于返回表单结构等） */
    private Map<String, Object> map;

    /**
     * 表格数据对象
     */
    public TableDataInfo()
    {
    }

    /**
     * 分页
     * 
     * @param list 列表数据
     * @param total 总记录数
     */
    public TableDataInfo(List<?> list, long total)
    {
        this.rows = list;
        this.total = total;
    }

    /**
     * 分页并附加数据
     * 
     * @param list 列表数据
     * @param total 总记录数
     * @param map 附加数据
     */
    public TableDataInfo(List<?> list, long total, Map<String, Object> map)
    {
        this.rows = list;
        this.total = total;
        this.map = map;
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    public List<?> getRows()
    {
        return rows;
    }

    public void setRows(List<?> rows)
    {
        this.rows = rows;
    }

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMsg()
    {
        return msg;
    }

    public void setMsg(String msg)
    {
        this.msg = msg;
    }

    public Map<String, Object> getMap()
    {
        return map;
    }

    public void setMap(Map<String, Object> map)
    {
        this.map = map;
    }

    /**
     * 构建 TableDataInfo 并附加额外数据
     * 
     * @param list 列表数据
     * @param total 总记录数
     * @param map 附加数据
     * @return TableDataInfo
     */
    public static TableDataInfo build(List<?> list, long total, Map<String, Object> map)
    {
        TableDataInfo info = new TableDataInfo();
        info.setRows(list);
        info.setTotal(total);
        info.setMap(map);
        return info;
    }
}
