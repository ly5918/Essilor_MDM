package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 层级环路检测日志实体 cmd_loop_check_log
 * <p>
 * 对应页面：客户层级 hier —— 提交前校验（父子相同 / 多父冲突 / 路径循环）与 Loop Check 证据。
 * <p>
 * 每次「提交前校验」都留一条记录：通过记 PASS，阻塞记 FAIL 并写入 conflictPath（冲突路径）。
 * 这满足 Demo Topic「Loop Check：直接/间接循环阻断与冲突路径，完整证据」的要求 ——
 * 系统不只是拒绝，还要能拿出「为什么拒绝」的路径证据供 Auditor 追溯。
 * <p>
 * 本表没有 create_dept / update_* 列，因此不继承 BaseEntity。
 *
 * @author Essilor CMD POC
 */
@Data
@TableName("cmd_loop_check_log")
public class CmdLoopCheckLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 检测编号 */
    private String checkCode;

    /** 层级类型 */
    private String hierarchyType;

    /** 待校验父节点 One ID */
    private String parentOneId;

    /** 待校验子节点 One ID */
    private String childOneId;

    /** 检测类型（SELF_REF / LEVEL_RULE / MULTI_PARENT / CYCLE / VALIDITY） */
    private String checkType;

    /** 检测结果（PASS / FAIL / WARN） */
    private String checkResult;

    /** 冲突路径（如 A3-001 → A2-0188 → A1-000128 → A3-001） */
    private String conflictPath;

    /** 冲突节点列表（逗号分隔） */
    private String conflictNodes;

    /** 检测信息 */
    private String message;

    /** 修正建议 */
    private String suggestion;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 关联关系 ID（编辑场景） */
    private Long relationId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;

    /** 创建者 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
