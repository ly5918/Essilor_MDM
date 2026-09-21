package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户主档指标概览视图对象
 * <p>
 * 对应页面：客户管理 customers 列表顶部指标带（客户总数 / Active / 待处理 / 跨 BU 全局 / 疑似重复 / 平均质量分）。
 * <p>
 * 设计说明：指标随「当前筛选条件」实时计算，与下方列表共用同一套查询条件，
 * 因此不会出现「指标按全量、列表按筛选」造成的口径错位。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdCustomerStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户总数（当前筛选条件下的记录数） */
    private Long total;

    /** 生效中数量（status = active） */
    private Long activeCount;

    /** 待处理数量（status = pending 待审批 / returned 退回待补充） */
    private Long pendingCount;

    /** 跨 BU 全局可见数量（gc_scope_flag = Y） */
    private Long crossBuCount;

    /** 疑似重复数量（duplicate_flag = Y） */
    private Long duplicateCount;

    /** 平均质量分（仅统计已跑过 DQ 的记录，未评分的 0 分不计入分母） */
    private Long avgDqScore;
}
