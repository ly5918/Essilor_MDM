package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeDetailVo;
import org.dromara.cmd.domain.vo.CmdChangeFieldVo;
import org.dromara.cmd.domain.vo.CmdChangeKpiVo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdDeactivateResultVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 变更与停用 服务层
 * <p>
 * 对应页面：变更与停用 change（工作台 4 张指标卡 / 变更申请 / 停用申请 / 版本历史）。
 * 覆盖泳道图两条链路：「关键客户属性变更」与「客户逻辑停用」。
 *
 * @author Essilor CMD POC
 */
public interface ICmdChangeService {

    /**
     * 可变更字段目录（发起属性变更弹窗的字段选择器）
     * <p>
     * 只返回已配置物理列（physical_column 非空）的 CUSTOMER 模型字段：
     * 这些字段变更后能真正写回客户主档，其余扩展字段需先在平台管理维护物理列。
     *
     * @return 字段目录（按显示顺序）
     */
    List<CmdChangeFieldVo> selectChangeableFields();

    /**
     * 分页查询变更 / 停用申请
     *
     * @param bo        查询条件（关键字 / 变更类型 / 状态 / BU / One ID）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CmdChangeRequestVo> selectPage(CmdChangeRequestBo bo, PageQuery pageQuery);

    /**
     * 按申请编号查询详情
     *
     * @param requestCode 申请编号
     * @return 申请详情
     */
    CmdChangeRequestVo selectByRequestCode(String requestCode);

    /**
     * 提交变更 / 停用申请
     * <p>
     * 一次提交完成：申请单落库 → 字段级差异快照（Before / After）→ 关联关系影响检查
     * → 登记审批待办（进入审批中心 BU Scope 队列）→ 写审批轨迹、工作流步骤与审计事件。
     *
     * @param bo 申请信息（变更场景必带 diffs 字段级明细）
     * @return 影响行数
     */
    int submit(CmdChangeRequestBo bo);

    /**
     * 变更与停用指标卡（工作台 4 张卡：待审批变更 / 待审批停用 / 本月已生效 / One ID 重生成）
     *
     * @return 指标列表
     */
    List<CmdChangeKpiVo> selectKpi();

    /**
     * 申请详情（Before / After 差异 + 影响面 + 审批轨迹 + 版本上下文）
     *
     * @param requestCode 申请编号
     * @return 详情
     */
    CmdChangeDetailVo selectDetail(String requestCode);

    /**
     * 查询客户主档版本历史
     * <p>
     * 用于证明「换版本不换 One ID」：版本号逐次递增，One ID 终身稳定。
     *
     * @param oneId 客户主数据标识
     * @return 版本快照列表（按版本号倒序）
     */
    List<CmdCustomerVersionVo> selectVersions(String oneId);

    /**
     * 逻辑停用结果（业务视图 + 落库记录）
     * <p>
     * 双视角证明「无物理删除」：业务侧只看到状态变化，后台记录显示
     * status 被改写、is_deleted 仍为 false、One ID 未回收。
     *
     * @param oneId 客户主数据标识
     * @return 停用结果
     */
    CmdDeactivateResultVo selectDeactivateResult(String oneId);

    /**
     * 生效申请（写主档新版本）
     * <p>
     * 仅允许审批通过（APPROVED）的申请生效：按差异明细写回主档、递增版本号、
     * 追加版本快照，One ID 保持不变；停用场景把状态置为 inactive / archived。
     *
     * @param requestCode 申请编号
     * @return 影响行数
     */
    int effect(String requestCode);

    /**
     * 撤回申请（提交人自行撤销，尚未审批完成时可用）
     *
     * @param requestCode 申请编号
     * @return 影响行数
     */
    int cancel(String requestCode);
}
