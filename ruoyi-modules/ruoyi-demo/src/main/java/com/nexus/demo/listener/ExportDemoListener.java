package com.nexus.demo.listener;

import cn.hutool.core.util.NumberUtil;
import cn.idev.excel.context.AnalysisContext;
import com.nexus.common.core.utils.ValidatorUtils;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.core.DefaultExcelListener;
import com.nexus.common.excel.core.DropDownOptions;
import com.nexus.demo.domain.vo.ExportDemoVo;

import java.util.List;

/**
 * Excel带下拉框的解析处理器
 *
 * @author Emil.Zhang
 */
public class ExportDemoListener extends DefaultExcelListener<ExportDemoVo> {

    public ExportDemoListener() {
        // 显示使用构造函数，否则将导致空指针
        super(true);
    }

    /**
     * 逐行解析带下拉框的 Excel 数据
     * <p>先按新增分组校验必填项，再解析省市县级联下拉选项并写入对应 ID，最后按编辑分组校验后收集到结果列表。</p>
     *
     * @param data    当前行解析出的 {@link ExportDemoVo} 数据
     * @param context 解析上下文
     */
    @Override
    public void invoke(ExportDemoVo data, AnalysisContext context) {
        // 先校验必填
        ValidatorUtils.validate(data, AddGroup.class);

        // 处理级联下拉的部分
        String province = data.getProvince();
        String city = data.getCity();
        String area = data.getArea();
        // 本行用户选择的省
        List<String> thisRowSelectedProvinceOption = DropDownOptions.analyzeOptionValue(province);
        if (thisRowSelectedProvinceOption.size() == 2) {
            String provinceIdStr = thisRowSelectedProvinceOption.get(1);
            if (NumberUtil.isNumber(provinceIdStr)) {
                // 严格要求数据的话可以在这里做与数据库相关的判断
                // 例如判断省信息是否在数据库中存在等，建议结合RedisCache做缓存10s，减少数据库调用
                data.setProvinceId(Integer.parseInt(provinceIdStr));
            }
        }
        // 本行用户选择的市
        List<String> thisRowSelectedCityOption = DropDownOptions.analyzeOptionValue(city);
        if (thisRowSelectedCityOption.size() == 2) {
            String cityIdStr = thisRowSelectedCityOption.get(1);
            if (NumberUtil.isNumber(cityIdStr)) {
                data.setCityId(Integer.parseInt(cityIdStr));
            }
        }
        // 本行用户选择的县
        List<String> thisRowSelectedAreaOption = DropDownOptions.analyzeOptionValue(area);
        if (thisRowSelectedAreaOption.size() == 2) {
            String areaIdStr = thisRowSelectedAreaOption.get(1);
            if (NumberUtil.isNumber(areaIdStr)) {
                data.setAreaId(Integer.parseInt(areaIdStr));
            }
        }

        // 处理完毕以后判断是否符合规则
        ValidatorUtils.validate(data, EditGroup.class);

        // 添加到处理结果中
        getExcelResult().getList().add(data);
    }
}
