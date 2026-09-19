package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 批量导入任务 cmd_import_job
 * <p>
 * 对应页面：批量导入 batch（导入任务列表 / 导入结果分流）。
 * 设计约束：一行一个导入批次，统计字段（精确/疑似/新建/复核/无效）由导入过程回写，
 * 本 POC 阶段由种子数据或后端接口直接赋值，不引入文件解析等复杂逻辑。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_import_job")
public class CmdImportJob extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 任务编号（页面 Job ID） */
    private String jobCode;

    /** 任务名称 */
    private String jobName;

    /** 场景（DOOR / LEGAL / GROUP 等） */
    private String scene;

    /** 模板ID */
    private Long templateId;

    /** 模板编码 */
    private String templateCode;

    /** 模板版本 */
    private String templateVersion;

    /** 归属 BU */
    private String buScope;

    /** 文件名 */
    private String fileName;

    /** 文件路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 总行数 */
    private Integer totalCount;

    /** 精确匹配数 */
    private Integer exactCount;

    /** 疑似重复数 */
    private Integer suspectedCount;

    /** 新建数 */
    private Integer newCount;

    /** 待复核数 */
    private Integer reviewCount;

    /** 无效数 */
    private Integer invalidCount;

    /** 成功数 */
    private Integer successCount;

    /** 任务状态（WAIT_REVIEW / RUNNING / PARTIAL_SUCCESS / FAILED / COMPLETED） */
    private String jobStatus;

    /** 进度（0-100） */
    private Integer progress;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 错误信息 */
    private String errorMessage;

    /** 提交人（页面展示用，冗余保存） */
    private String submitBy;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
