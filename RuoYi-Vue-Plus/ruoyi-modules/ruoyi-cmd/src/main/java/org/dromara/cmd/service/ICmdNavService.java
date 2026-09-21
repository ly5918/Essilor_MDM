package org.dromara.cmd.service;

import java.util.Map;

/**
 * 侧边导航统计 服务层接口
 * <p>
 * 对应页面：全部角色的左侧 ROLE-BASED NAVIGATION 菜单。
 * 每个菜单项右上角的数字（数据统计角标）由本服务统一提供，
 * 保证「全应用所有能统计的菜单都有数字」，且与点进去的页面数字一致。
 *
 * @author Essilor CMD POC
 */
public interface ICmdNavService {

    /**
     * 查询某角色的菜单统计角标
     * <p>
     * 返回 Map 的 key = 菜单标识（与前端角色菜单 id 一一对应，如 approval / hier / batch），
     * value = 该菜单需要处理的条数；为 0 时前端不显示角标。
     * <p>
     * 口径（全部实时取自业务表，与对应页面列表一致）：
     * <ul>
     *   <li>dash —— 待办合计（其余「待办类」菜单之和，工作台作为总入口）</li>
     *   <li>approval —— cmd_approval_task：待处理 + 退回待补充（按角色 BU / GC 队列）</li>
     *   <li>customers —— cmd_customer：待审批 + 退回补充的客户</li>
     *   <li>hier —— 待归位主数据（客户主档 × 层级节点）+ 待审批的层级关系申请</li>
     *   <li>batch —— cmd_import_job：待复核 / 进行中 / 部分成功（仍需人工处置）</li>
     *   <li>change —— cmd_change_request：待审批 + 退回补充的变更申请</li>
     *   <li>flowWorkitem —— flow_instance：未结束的运行中实例</li>
     *   <li>flowDone —— flow_instance：今日已结束的实例</li>
     *   <li>audit —— audit_event：失败或高风险事件（需 Auditor 关注）</li>
     *   <li>integration —— int_run：失败 / 重试中的集成运行</li>
     *   <li>admin —— 平台管理为配置态页面，无待办口径，恒为 0</li>
     * </ul>
     *
     * @param role 角色标识（business / bu / gc / admin / audit），为空按 BU 处理
     * @return 菜单标识 → 统计数
     */
    Map<String, Long> selectMenuBadges(String role);
}
