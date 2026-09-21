package org.dromara.cmd.service.impl;

import org.dromara.cmd.domain.vo.CmdOcrLicenseVo;
import org.dromara.cmd.domain.vo.CmdOcrRecognizeVo;
import org.dromara.cmd.domain.vo.CmdOcrResultVo;
import org.dromara.cmd.service.ICmdOcrService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营业执照 OCR 识别 服务层实现
 * <p>
 * POC 阶段未接入真实 OCR 引擎：识别结果按 <code>docs/cmd-poc/测试素材/营业执照</code> 下的测试图预置，
 * 上传哪张测试图就返回对应的识别结果，保证演示时「执照图片内容 ↔ 识别结果」一致；
 * 文件名未匹配到测试图时回退到默认结果，不会报错。
 * <p>
 * 后续接入真实 OCR（阿里 / 百度 / 腾讯云等）时，只需替换 {@link #recognize(String)} 的内部实现，
 * 把识别结果映射成本模块的 VO 结构即可，Controller 与前端契约无需改动。
 *
 * @author Essilor CMD POC
 */
@Service
public class CmdOcrServiceImpl implements ICmdOcrService {

    /** 默认测试图（文件名去扩展名），未匹配到时返回该图的识别结果 */
    private static final String DEFAULT_KEY = "license_01";

    /** 测试素材识别结果：文件名（去扩展名，小写） → 识别结果 */
    private static final Map<String, CmdOcrRecognizeVo> SAMPLES = new HashMap<>();

    static {
        SAMPLES.put("license_01", from(new Sample(
            "上海清视眼镜有限公司", "91310106MA1FL2X78K", "有限责任公司（自然人投资或控股）",
            "上海市静安区南京西路1266号恒隆广场二期28层", "上海市", "上海市", 93, 91)));
        SAMPLES.put("license_02", from(new Sample(
            "苏州新视野光学科技有限公司", "91320594MA20TQ531H", "有限责任公司（自然人投资或控股）",
            "江苏省苏州工业园区星湖街328号创意产业园6栋A座", "江苏省", "苏州市", 95, 93)));
        SAMPLES.put("license_03", from(new Sample(
            "广州明眸医疗器械有限公司", "91440101MA9UYB6L3D", "有限责任公司（自然人投资或控股）",
            "广东省广州市天河区天河路228号正佳广场东塔19层", "广东省", "广州市", 88, 90)));
        SAMPLES.put("license_04", from(new Sample(
            "深圳星曜视光科技有限公司", "91440300MA5GQR8T2N", "有限责任公司（自然人投资或控股）",
            "广东省深圳市南山区海德三道199号天利中央广场A座22层", "广东省", "深圳市", 92, 94)));
        SAMPLES.put("license_05", from(new Sample(
            "杭州澄明眼镜贸易有限公司", "91330106MA2B0X7K5W", "有限责任公司（自然人独资）",
            "浙江省杭州市滨江区江南大道588号恒鑫大厦15层", "浙江省", "杭州市", 94, 92)));
        SAMPLES.put("license_06", from(new Sample(
            "南京视界供应链管理有限公司", "91320115MA1WC3Q9H3", "有限责任公司（自然人投资或控股）",
            "江苏省南京市建邺区江东中路108号万达中心B座9层", "江苏省", "南京市", 89, 91)));
        SAMPLES.put("license_07", from(new Sample(
            "成都睛彩光学科技有限公司", "91510100MA6CQX2V8M", "有限责任公司（自然人投资或控股）",
            "四川省成都市高新区天府大道北段1700号环球中心E2区16层", "四川省", "成都市", 91, 93)));
        SAMPLES.put("license_08", from(new Sample(
            "重庆朗目视光医疗管理有限公司", "91500103MA60J5D4R7", "有限责任公司（自然人投资或控股）",
            "重庆市渝中区邹容路68号大都会广场5栋18层", "重庆市", "重庆市", 93, 95)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdOcrRecognizeVo recognize(String fileName) {
        return SAMPLES.getOrDefault(normalizeKey(fileName), SAMPLES.get(DEFAULT_KEY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdOcrRecognizeVo recognizeOfCustomer(String legalName, String creditCode) {
        // 优先按法定名称精确匹配，其次按统一社会信用代码；都未命中回退默认素材
        for (CmdOcrRecognizeVo vo : SAMPLES.values()) {
            if (StringUtils.isNotBlank(legalName) && legalName.equals(vo.getLicense().getName())) {
                return vo;
            }
        }
        for (CmdOcrRecognizeVo vo : SAMPLES.values()) {
            if (StringUtils.isNotBlank(creditCode) && creditCode.equals(vo.getLicense().getCreditCode())) {
                return vo;
            }
        }
        return SAMPLES.get(DEFAULT_KEY);
    }

    /**
     * 归一化文件名：去掉路径与扩展名后转小写
     * <p>
     * 注意：这里用 JDK 原生的 lastIndexOf/substring 而不是 StringUtils.substringAfterLast，
     * 后者在找不到分隔符时返回空串（commons-lang3 语义），会把「无路径的文件名」误清空。
     *
     * @param fileName 原始文件名（可为空）
     * @return 归一化后的键（无法归一化时返回空串）
     */
    private static String normalizeKey(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return StringUtils.EMPTY;
        }
        String name = fileName.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.toLowerCase();
    }

    /**
     * 由测试素材原始数据构造识别结果
     *
     * @param sample 测试素材原始数据
     * @return 识别结果（字段名与客户模型元数据 field_name 一致，供前端回填表单）
     */
    private static CmdOcrRecognizeVo from(Sample sample) {
        CmdOcrLicenseVo license = new CmdOcrLicenseVo();
        license.setCreditCode(sample.creditCode());
        license.setName(sample.name());
        license.setType(sample.type());
        license.setAddress(sample.address());

        List<CmdOcrResultVo> fields = new ArrayList<>();
        fields.add(field("legal_name", "客户法定名称", sample.name(), "98%"));
        fields.add(field("credit_code", "统一社会信用代码", sample.creditCode(), "99%"));
        fields.add(field("address", "注册地址", sample.address(), sample.addressConf() + "%"));
        fields.add(field("province", "省份", sample.province(), "96%"));
        fields.add(field("city", "城市", sample.city(), sample.cityConf() + "%"));

        CmdOcrRecognizeVo vo = new CmdOcrRecognizeVo();
        vo.setLicense(license);
        vo.setFields(fields);
        return vo;
    }

    /**
     * 构造单个字段识别结果
     */
    private static CmdOcrResultVo field(String code, String field, String value, String confidence) {
        CmdOcrResultVo vo = new CmdOcrResultVo();
        vo.setCode(code);
        vo.setField(field);
        vo.setValue(value);
        vo.setConfidence(confidence);
        return vo;
    }

    /**
     * 测试素材原始数据（仅供构造识别结果使用）
     *
     * @param name        企业名称
     * @param creditCode  统一社会信用代码
     * @param type        企业类型
     * @param address     住所（注册地址）
     * @param province    省份
     * @param city        城市
     * @param addressConf 注册地址识别置信度
     * @param cityConf    城市识别置信度
     */
    private record Sample(String name, String creditCode, String type, String address,
                          String province, String city, int addressConf, int cityConf) {
    }
}
