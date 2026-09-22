package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型版本视图对象（由 md_field 按版本号聚合得出）
 * <p>
 * 对应页面：平台管理 → 字段与值集的模型版本列表。
 * 总设计 V6.1：「每一步均形成状态、版本、差异和审计证据」——
 * diff 即版本间差异摘要，draftCreatedAt 提供草稿创建时间追溯。
 *
 * @author Essilor CMD POC
 */
@Data
public class PlatformVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 版本号 */
    private String version;

    /** 状态（Current / Draft） */
    private String status;

    /** 差异摘要（较上一版本：新增 / 变更字段数；首个版本为「基线」） */
    private String diff;

    /** 草稿创建时间（该版本字段最早的创建时间） */
    private String draftCreatedAt;

    /** 发布时间 */
    private String publishedAt;
}
