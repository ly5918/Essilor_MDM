package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdGovernanceTaskBo;
import org.dromara.cmd.domain.vo.CmdGovernanceTaskVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;
import java.util.Map;

/**
 * 治理任务 服务层接口
 * <p>
 * 对应页面：治理任务 gov —— 4 张指标卡（Suspect / Review / New / Cross-BU）与下钻明细。
 *
 * @author Essilor CMD POC
 */
public interface ICmdGovernanceService {

    /**
     * 分页查询治理任务列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 治理任务分页结果
     */
    PageResult<CmdGovernanceTaskVo> selectPageTaskList(CmdGovernanceTaskBo bo, PageQuery pageQuery);

    /**
     * 查询治理任务详情
     *
     * @param id 主键
     * @return 治理任务详情
     */
    CmdGovernanceTaskVo selectTaskById(Long id);

    /**
     * 认领治理任务
     *
     * @param id      主键
     * @param userId  认领人
     * @return 影响行数
     */
    int claimTask(Long id, Long userId);

    /**
     * 处理治理任务（关联已有 / 确认新建 / 排除 / 合并 / 退回）
     *
     * @param id         主键
     * @param resolution 处理结论
     * @param opinion    处理意见
     * @return 影响行数
     */
    int resolveTask(Long id, String resolution, String opinion);

    /**
     * 查询治理指标卡统计（4 个指标 + 合计）
     *
     * @param bo 统计范围条件（BU / 跨BU 等）
     * @return 指标统计（key: SUSPECT / REVIEW / NEW / CROSS_BU / TOTAL）
     */
    Map<String, Long> selectTaskStats(CmdGovernanceTaskBo bo);

    /**
     * 查询治理任务列表（不分页）
     *
     * @param bo 查询条件
     * @return 治理任务列表
     */
    List<CmdGovernanceTaskVo> selectTaskList(CmdGovernanceTaskBo bo);

    /**
     * 创建疑似重复治理任务（总设计 MERGE 场景「发现候选」）
     * <p>
     * 触发源：单条创建 Duplicate Check 命中存量主档（Suspected）；
     * 批量导入行级匹配 Suspected。Exact 由系统自动关联，不建治理任务。
     *
     * @param bizId       业务主键（创建审批 taskNo / 导入 jobCode+行号）
     * @param oneId       相关 One ID（新申请方，可能尚未生效）
     * @param subject     任务主题（客户名称）
     * @param buScope     归属 BU
     * @param crossBu     是否跨 BU（决定 SUSPECT / CROSS_BU 类型与 GC 路由）
     * @param matchState  匹配结论（EXACT / SUSPECTED）
     * @param evidenceJson 候选对比证据（含候选 One ID）
     * @return 任务编号 GOV-yyyyMMdd-####
     */
    String createDuplicateTask(String bizId, String oneId, String subject, String buScope,
                               boolean crossBu, String matchState, String evidenceJson);

    /**
     * 发起跨 BU 客户合并请求（总设计 MERGE 场景：发现候选 → 证据准备 → BU 初审 → GC 决策）
     * <p>
     * 创建 sceneCode=MERGE 的审批待办（cmd_approval_task）并启动 Warm-Flow 客户合并审批流，
     * 批准后由 {@link #execMergeTask} 执行合并（Golden Record 更新 + 交叉引用 + 审计）。
     *
     * @param sourceOneId 合并源 One ID（被合并 / 新申请 / 导入行对应记录）
     * @param targetOneId 合并目标 One ID（保留的 Golden Record，One ID 保持稳定）
     * @param reason      发起原因
     * @return 合并审批任务编号 AP-yyyyMMdd-####
     */
    String launchMerge(String sourceOneId, String targetOneId, String reason);

    /**
     * 执行合并审批通过后的业务结果（总设计「执行合并 / 新建 → 结果发布 → 追踪审计」）：
     * RECORD_MERGE：存量主档合并（Golden Record 补全 + 源记录 merged_to_one_id + Legacy 交叉引用）；
     * ROW_LINK：批量导入 Suspected 行关联已有 One ID（行级治理结果回写）。
     *
     * @param task MERGE 审批任务（bizSnapshotJson 携带 mode / source / target）
     */
    void execMergeTask(org.dromara.cmd.domain.CmdApprovalTask task);
}
