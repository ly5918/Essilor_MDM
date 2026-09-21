package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 变更与停用 指标卡视图对象
 * <p>
 * 对应页面：变更与停用 change 工作台的 4 张指标卡
 * （待审批变更 / 待审批停用 / 本月已生效 / One ID 重生成）。
 * <p>
 * 其中「One ID 重生成」恒为 0，是对「One ID 稳定、字段变化不重新生成」这一
 * 设计约束的量化证明，不是占位数据。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdChangeKpiVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 指标名称（页面对应卡片标题） */
    private String label;

    /** 指标数值 */
    private Long value;

    /** 口径说明（卡片脚注，写明统计条件的业务含义） */
    private String hint;
}
