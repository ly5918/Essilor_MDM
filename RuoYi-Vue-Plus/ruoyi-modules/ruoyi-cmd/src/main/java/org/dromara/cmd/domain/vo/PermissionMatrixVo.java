package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限矩阵视图对象
 * <p>
 * 对应页面：平台管理 → 角色与权限的矩阵表（能力 × 五类技术角色）。
 * 说明：矩阵文案属于平台展示规则，POC 阶段在 Service 中按常量生成；
 * 后续如需运维调整，改为读取 cfg_config 配置表即可。
 *
 * @author Essilor CMD POC
 */
@Data
public class PermissionMatrixVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 能力项 */
    private String capability;

    /** Business User */
    private String business;

    /** Data Steward */
    private String steward;

    /** Platform Admin */
    private String admin;

    /** Auditor */
    private String auditor;
}
