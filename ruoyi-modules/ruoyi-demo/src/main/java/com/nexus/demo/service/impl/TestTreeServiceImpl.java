package com.nexus.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.demo.domain.TestTree;
import com.nexus.demo.domain.bo.TestTreeBo;
import com.nexus.demo.domain.vo.TestTreeVo;
import com.nexus.demo.mapper.TestTreeMapper;
import com.nexus.demo.service.ITestTreeService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 测试树表Service业务层处理
 *
 * @author wanghuan
 * @date 2021-07-26
 */
// @DS("slave") // 切换从库查询
@RequiredArgsConstructor
@Service
public class TestTreeServiceImpl implements ITestTreeService {

    private final TestTreeMapper baseMapper;

    /**
     * 根据主键查询测试树表详情
     *
     * @param id 测试树表主键
     * @return 测试树表视图对象
     */
    @Override
    public TestTreeVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    // @DS("slave") // 切换从库查询
    /**
     * 查询测试树表列表（不分页）
     *
     * @param bo 查询条件业务对象
     * @return 测试树表视图对象列表
     */
    @Override
    public List<TestTreeVo> queryList(TestTreeBo bo) {
        LambdaQueryWrapper<TestTree> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<TestTree> buildQueryWrapper(TestTreeBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<TestTree> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, TestTree::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, TestTree::getUserId, bo.getUserId());
        lqw.like(StringUtils.isNotBlank(bo.getTreeName()), TestTree::getTreeName, bo.getTreeName());
        lqw.between(params.get("beginCreateTime") != null && params.get("endCreateTime") != null,
            TestTree::getCreateTime, params.get("beginCreateTime"), params.get("endCreateTime"));
        lqw.orderByAsc(TestTree::getId);
        return lqw;
    }

    /**
     * 根据业务对象新增测试树表
     *
     * @param bo 新增业务对象
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(TestTreeBo bo) {
        TestTree add = MapstructUtils.convert(bo, TestTree.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 根据业务对象修改测试树表
     *
     * @param bo 编辑业务对象
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(TestTreeBo bo) {
        TestTree update = MapstructUtils.convert(bo, TestTree.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     *
     * @param entity 实体类数据
     */
    private void validEntityBeforeSave(TestTree entity) {
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除测试树表
     *
     * @param ids     主键集合
     * @param isValid 是否校验，true-删除前校验，false-不校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
