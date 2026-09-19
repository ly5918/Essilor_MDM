package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 营业执照 OCR 识别结果视图对象
 * <p>
 * 对应「OCR识别结果」弹窗：执照原件信息 + 字段识别值列表。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdOcrRecognizeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 营业执照原件信息 */
    private CmdOcrLicenseVo license;

    /** 字段识别结果（字段名对齐客户模型元数据，供前端回填表单） */
    private List<CmdOcrResultVo> fields;
}
