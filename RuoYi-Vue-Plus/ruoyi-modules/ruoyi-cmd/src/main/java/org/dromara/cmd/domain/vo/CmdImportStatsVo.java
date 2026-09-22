package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入中心全局统计（全量口径，与分页无关）
 * <p>
 * 对应页面：批量导入 / 批量治理顶部「批次总览」KPI。
 * 此前 KPI 由前端对当前页任务累加得到，翻页数字跳变、首屏未加载时全为 0；
 * 改为服务端全量聚合后，「菜单角标 / KPI / 列表」三者口径一致。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdImportStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 导入任务总数 */
    private Long jobCount;

    /** 上传数据总行数 */
    private Long totalRows;

    /** Exact 关联已有 One ID */
    private Long exactCount;

    /** Suspected 待治理 */
    private Long suspectedCount;

    /** New 待审批 */
    private Long newCount;

    /** Review 待复核 */
    private Long reviewCount;

    /** Invalid 退回修复 */
    private Long invalidCount;
}
