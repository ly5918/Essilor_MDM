package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程实例记录 查询对象（流程中心「流程实例记录」列表筛选行）
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowInstanceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键字：申请编号 / 业务标题 */
    private String keyword;

    /** 任务状态（PENDING / APPROVED / REJECTED / RETURNED / ESCALATED / CANCELLED） */
    private String status;

    /** 业务类型（中文，如「客户创建」） */
    private String bizType;

    /** 运行状态（RUNNING 进行中 / DONE 已完成 / NEW 未启动） */
    private String runState;
}
