package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 集成运行查询对象 int_run
 *
 * @author Essilor CMD POC
 */
@Data
public class IntRunBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行编号 */
    private String runCode;

    /** 方向 */
    private String direction;

    /** 目标系统 */
    private String targetSystem;

    /** 运行状态 */
    private String runStatus;

    /** 关键字 */
    private String keyword;
}
