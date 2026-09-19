package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdOcrRecognizeBo;
import org.dromara.cmd.domain.vo.CmdOcrRecognizeVo;
import org.dromara.cmd.service.ICmdOcrService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营业执照 OCR 识别 控制层
 * <p>
 * 对应页面：客户管理 customers —— 新建客户弹窗「上传并OCR识别」→「OCR识别结果」弹窗。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/ocr")
public class CmdOcrController extends BaseController {

    private final ICmdOcrService ocrService;

    /**
     * 识别营业执照，返回执照原件信息与字段识别值
     *
     * @param bo 识别入参（fileName 可选，用于区分测试素材；为空返回默认演示识别结果）
     * @return 执照信息 + 字段识别结果
     */
    @PostMapping("/recognize")
    public R<CmdOcrRecognizeVo> recognize(@RequestBody(required = false) CmdOcrRecognizeBo bo) {
        return R.ok(ocrService.recognize(bo == null ? null : bo.getFileName()));
    }
}
