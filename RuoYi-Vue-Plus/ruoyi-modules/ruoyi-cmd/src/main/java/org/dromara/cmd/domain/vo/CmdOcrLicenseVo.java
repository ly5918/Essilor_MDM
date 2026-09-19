package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 营业执照原件信息视图对象
 * <p>
 * 对应「OCR识别结果」弹窗「营业执照预览」右侧 4 行。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdOcrLicenseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 名称 */
    private String name;

    /** 类型 */
    private String type;

    /** 住所 */
    private String address;
}
