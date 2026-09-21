package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerStatsVo;
import org.dromara.cmd.domain.vo.CmdCustomerSubmitVo;
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
     * 按当前筛选条件统计客户指标概览（列表顶部指标带）
     * <p>
     * 与 {@link #selectPageCustomerList} 共用同一套查询条件，保证「指标」与「列表」口径一致。
     *
     * @param bo 查询条件（与列表相同）
     * @return 指标概览（总数 / Active / 待处理 / 跨 BU / 疑似重复 / 平均质量分）
     */
    CmdCustomerStatsVo selectCustomerStats(CmdCustomerBo bo);

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
     * 提交客户新建申请：落主档 + 自动检查（DQ / 重复）+ 生成统一待办 + 拉起 Warm-Flow 流程实例
     * <p>
     * 对应页面：「新建客户申请」弹窗「提交申请」。与 insertCustomer 的差异：
     * <ul>
     *   <li>主档状态直接置为 pending（待审批），并写入 DQ 分与匹配结论；</li>
     *   <li>生成 cmd_approval_task 待办（scope=BU，当前节点 BU Scope 初审），进入 Data Steward 队列；</li>
     *   <li>调用 Warm-Flow 部署并启动流程实例，回写 flow_instance_id / flow_task_id / 当前节点镜像。</li>
     * </ul>
     *
     * @param bo 客户信息（含业务上下文与动态字段值）
     * @return 提交结果（One ID / 申请编号 / 当前节点 / 流程实例）
     */
    CmdCustomerSubmitVo submitApplication(CmdCustomerBo bo);

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
