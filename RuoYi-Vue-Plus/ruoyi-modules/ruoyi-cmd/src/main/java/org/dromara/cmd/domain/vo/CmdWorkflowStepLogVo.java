package org.dromara.cmd.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdWorkflowStepLog;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 工作流步骤执行日志 视图对象 CmdWorkflowStepLogVO
 * <p>
 * 前端「流程跟踪 / 审批详情」展示用：按 one_id / task_no 拉取，顺序呈现每一步。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdWorkflowStepLog.class)
public class CmdWorkflowStepLogVo implements Serializable {

    private Long id;
    private String oneId;
    private String taskNo;
    private Long flowInstanceId;
    private Integer stepSeq;
    private String stepType;
    private String nodeCode;
    private String nodeName;
    private String actionType;
    private String actionName;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String fromStatus;
    private String toStatus;
    private String opinion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
