package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果分流视图对象
 * <p>
 * 对应页面：批量导入 batch 的「查看结果」弹窗（Exact / Suspected / New / Review / Invalid 五路分流）。
 * <p>
 * 说明：分流策略文案（handling / owner）属于平台规则，POC 阶段在
 * {@link org.dromara.cmd.service.impl.CmdImportServiceImpl} 中按常量生成；
 * 后续如需运维调整，改为读取 cfg_config 配置表即可，无需改动前端。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdImportResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务编号 */
    private String jobCode;

    /** 精确匹配数 */
    private Integer exact;

    /** 疑似重复数 */
    private Integer suspected;

    /** 新建数 */
    private Integer created;

    /** 待复核数 */
    private Integer review;

    /** 无效数 */
    private Integer invalid;

    /** 分流处理策略列表 */
    private List<ImportRouteVo> routes = new ArrayList<>();

    /**
     * 分流明细行
     */
    @Data
    public static class ImportRouteVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 匹配结果（Exact / Suspected / Review / New / Invalid） */
        private String result;

        /** 处理方式 */
        private String handling;

        /** 责任角色 */
        private String owner;

        /** 明细文案 */
        private String detail;
    }
}
