package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户合并记录 cmd_merge_record
 * <p>
 * 总设计「审计与合并记录」：每次合并保存原因、操作者、审批流实例与 Before / After 证据，
 * 支持按保留期回溯（POC 保留 can_rollback 标记，不实现物理回滚）。
 * 客户详情弹窗「合并记录」页签按 survivor / merged 双向查询本表。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_merge_record")
public class CmdMergeRecord extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 合并单编号（MG-yyyyMMdd-####） */
    private String mergeCode;

    /** 保留方 One ID（Golden Record） */
    private String survivorOneId;

    /** 被合并方 One ID */
    private String mergedOneId;

    /** 合并类型（MANUAL 人工 / AUTO 系统判定） */
    private String mergeType;

    /** 字段取舍策略（如 TARGET_FIRST 目标优先补全） */
    private String mergeStrategy;

    /** 字段级合并决策（源记录补进了目标哪些字段） */
    private String fieldJson;

    /** 合并前证据（源记录快照） */
    private String beforeJson;

    /** 合并后结果（目标记录快照） */
    private String afterJson;

    /** 发起 / 执行原因 */
    private String reason;

    /** 是否可回滚（Y / N） */
    private String canRollback;

    /** 回滚时间 */
    private LocalDateTime rollbackTime;

    /** 回滚操作人 */
    private Long rollbackBy;

    /** 状态（EFFECTIVE 生效 / ROLLED_BACK 已回滚） */
    private String status;

    /** 关联审批流实例 */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
