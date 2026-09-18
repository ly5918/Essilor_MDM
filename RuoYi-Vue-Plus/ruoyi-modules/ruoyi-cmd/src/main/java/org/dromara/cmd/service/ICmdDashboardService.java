package org.dromara.cmd.service;

import org.dromara.cmd.domain.vo.CmdDashboardVo;

/**
 * 工作台统计 服务层接口
 * <p>
 * 对应页面：各角色工作台 dash —— 顶部指标卡与待办统计。
 *
 * @author Essilor CMD POC
 */
public interface ICmdDashboardService {

    /**
     * 查询工作台统计（按 BU Scope 与当前用户动态返回）
     *
     * @param buScope BU 范围（为空表示 GC 全局）
     * @param userId  当前用户 ID
     * @return 工作台统计
     */
    CmdDashboardVo selectDashboard(String buScope, Long userId);
}
