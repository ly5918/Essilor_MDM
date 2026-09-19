package org.dromara.cmd.domain.bo;

import lombok.Data;
import org.dromara.cmd.domain.CmdImportJob;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量导入任务查询对象 cmd_import_job
 * <p>
 * 对应页面：批量导入 batch 列表查询（按任务编号 / 场景 / BU / 状态 / 关键字过滤）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdImportJobBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务编号 */
    private String jobCode;

    /** 场景 */
    private String scene;

    /** 归属 BU */
    private String buScope;

    /** 任务状态 */
    private String jobStatus;

    /** 关键字（任务编号 / 文件名 模糊匹配） */
    private String keyword;

    /** 文件名（创建任务时传入） */
    private String fileName;

    /** 任务名称 */
    private String jobName;

    /** 总行数 */
    private Integer totalCount;

    /** 提交人 */
    private String submitBy;

    /** 备注 */
    private String remark;

    /** 构造查询实体：仅复制列表查询需要的字段 */
    public CmdImportJob toQueryEntity() {
        CmdImportJob entity = new CmdImportJob();
        entity.setJobCode(this.jobCode);
        entity.setScene(this.scene);
        entity.setBuScope(this.buScope);
        entity.setJobStatus(this.jobStatus);
        return entity;
    }
}
