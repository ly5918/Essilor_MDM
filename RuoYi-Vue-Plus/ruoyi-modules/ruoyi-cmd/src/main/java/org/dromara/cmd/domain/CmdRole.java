package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 技术角色 cmd_role
 * <p>
 * 对应页面：平台管理 → 角色与权限（角色清单、Scope、权限点）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_role")
public class CmdRole extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色类型 */
    private String roleType;

    /** 数据范围类型 */
    private String scopeType;

    /** 默认 BU */
    private String defaultBu;

    /** 角色描述（页面权限点） */
    private String description;

    /** 排序号 */
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
