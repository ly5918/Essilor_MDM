package org.dromara.cmd.service;

import org.dromara.cmd.domain.vo.CmdOcrRecognizeVo;

/**
 * 营业执照 OCR 识别 服务层接口
 * <p>
 * 对应页面：客户管理 customers —— 新建客户弹窗「上传并OCR识别」→「OCR识别结果」弹窗。
 *
 * @author Essilor CMD POC
 */
public interface ICmdOcrService {

    /**
     * 识别营业执照，返回执照原件信息与字段识别值
     *
     * @param fileName 上传文件名（可选）。POC 阶段用于区分 docs/cmd-poc/测试素材/营业执照 下的测试图；
     *                 为空或未匹配时返回默认演示识别结果。
     * @return 执照信息 + 字段识别结果
     */
    CmdOcrRecognizeVo recognize(String fileName);
}
