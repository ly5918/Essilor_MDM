package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdCustomerVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 客户主档 服务层接口
 * <p>
 * 对应页面：客户管理 customers、工作台 dash、治理任务 gov 下钻。
 * 业务约束：One ID 生成后永不变更；字段变更只追加版本；停用为逻辑停用（无物理删除）。
 *
 * @author Essilor CMD POC
 */
public interface ICmdCustomerService {

    /**
     * 分页查询客户主档列表（按角色 Scope 由调用方拼接条件）
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 客户分页结果
     */
    PageResult<CmdCustomerVo> selectPageCustomerList(CmdCustomerBo bo, PageQuery pageQuery);

    /**
     * 查询客户列表（不分页，供下拉与导出使用）
     *
     * @param bo 查询条件
     * @return 客户列表
     */
    List<CmdCustomerVo> selectCustomerList(CmdCustomerBo bo);

    /**
     * 按主键查询客户详情
     *
     * @param id 主键
     * @return 客户详情
     */
    CmdCustomerVo selectCustomerById(Long id);

    /**
     * 按 One ID 查询客户详情
     *
     * @param oneId One ID
     * @return 客户详情
     */
    CmdCustomerVo selectCustomerByOneId(String oneId);

    /**
     * 新增客户（草稿或提交审批）
     *
     * @param bo 客户信息
     * @return 生成的 One ID
     */
    String insertCustomer(CmdCustomerBo bo);

    /**
     * 修改客户信息（自动追加版本快照）
     *
     * @param bo 客户信息
     * @return 影响行数
     */
    int updateCustomer(CmdCustomerBo bo);

    /**
     * 客户逻辑停用（写 status=inactive + effectiveTo，并追加 DEACTIVATE 版本）
     *
     * @param oneId  客户 One ID
     * @param reason 停用原因
     * @return 影响行数
     */
    int deactivateCustomer(String oneId, String reason);

    /**
     * 批量逻辑删除客户
     *
     * @param ids      主键集合
     * @param isValid  是否校验（true 时校验是否允许删除）
     * @return 影响行数
     */
    int deleteCustomerByIds(Collection<Long> ids, boolean isValid);

    /**
     * 查询客户的版本历史（用于 Before / After 对比）
     *
     * @param oneId One ID
     * @return 版本列表
     */
    List<CmdCustomerVersionVo> selectVersionList(String oneId);
}
