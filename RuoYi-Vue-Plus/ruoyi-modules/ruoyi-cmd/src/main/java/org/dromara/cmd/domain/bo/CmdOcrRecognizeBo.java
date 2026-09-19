package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 营业执照 OCR 识别入参
 * <p>
 * 对应页面：客户管理 customers —— 新建客户弹窗「上传并OCR识别」→「OCR识别结果」弹窗。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdOcrRecognizeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 上传文件名（可选）
     * <p>
     * POC 阶段用于区分测试素材（docs/cmd-poc/测试素材/营业执照/license_0x.png），
     * 演示时保证「执照图片 ↔ 识别结果」一致；为空或未匹配时返回默认演示结果。
     */
    private String fileName;
}
