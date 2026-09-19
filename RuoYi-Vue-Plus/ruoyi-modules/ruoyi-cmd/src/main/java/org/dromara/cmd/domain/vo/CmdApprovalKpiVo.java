package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 治理与审批 KPI 视图对象
 * <p>
 * 对应页面：治理与审批 approval 顶部五张指标卡
 * （待我处理 / 临近SLA / 已超时 / 退回待补充 / 本周已处理）。
 * <p>
 * 说明：指标口径全部来自 cmd_approval_task 表统计，不引入额外缓存或中间件；
 * 如需调整口径，改 Service 中的查询条件即可，运维无需改配置。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdApprovalKpiVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 指标名称 */
    private String label;

    /** 指标值 */
    private Long value;

    /** 辅助说明 */
    private String hint;
}
