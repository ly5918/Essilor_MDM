package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 审计事件 audit_event
 * <p>
 * 对应页面：审计中心 audit（变更 / 审批 / 合并 / 权限 / 管理员操作 全量留痕）。
 * 设计约束：只追加、不修改、不物理删除，事件编号由服务端生成。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_event")
public class AuditEvent extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 事件编号 */
    private String eventId;

    /** 事件类型（CREATE / UPDATE / DEACTIVATE / APPROVE / MERGE / PERMISSION / EXPORT ...） */
    private String eventType;

    /** 事件名称（页面展示文案） */
    private String eventName;

    /** 业务类型 */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 关联 One ID */
    private String oneId;

    /** 操作人 */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作角色 */
    private String operatorRole;

    /** 部门 */
    private Long deptId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 客户端 IP */
    private String clientIp;

    /** 客户端标识 */
    private String clientAgent;

    /** 请求地址 */
    private String requestUrl;

    /** 请求方法 */
    private String requestMethod;

    /** 变更前快照（JSON 字符串） */
    private String beforeJson;

    /** 变更后快照（JSON 字符串） */
    private String afterJson;

    /** 变更字段 */
    private String changedFields;

    /** 结果（SUCCESS / FAILED / TEST） */
    private String result;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 风险等级 */
    private String riskLevel;

    /** 是否敏感（Y是 N否） */
    private String sensitiveFlag;

    /** 关联 Warm-Flow 流程实例 */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
