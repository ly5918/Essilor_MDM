package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdLoopCheckLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 层级环路检测日志视图对象 cmd_loop_check_log
 * <p>
 * 对应页面：客户层级 hier 提交前校验结果展示（冲突路径证据）。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdLoopCheckLog.class)
public class CmdLoopCheckLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 检测编号 */
    private String checkCode;

    /** 层级类型 */
    private String hierarchyType;

    /** 待校验父节点 */
    private String parentOneId;

    /** 待校验子节点 */
    private String childOneId;

    /** 检测类型 */
    private String checkType;

    /** 检测结果（PASS / FAIL / WARN） */
    private String checkResult;

    /** 冲突路径 */
    private String conflictPath;

    /** 冲突节点列表 */
    private String conflictNodes;

    /** 检测信息 */
    private String message;

    /** 修正建议 */
    private String suggestion;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 关联关系 ID */
    private Long relationId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
