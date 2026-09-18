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
}
