package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.MdValueSet;
import org.dromara.cmd.domain.vo.PlatformVersionVo;
import org.dromara.cmd.service.ICmdPlatformService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据 / 字段与值集 控制层
 * <p>
 * 对应页面：平台管理 admin → 字段与值集。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/metadata")
public class CmdMetadataController extends BaseController {

    private final ICmdPlatformService platformService;

    /**
     * 查询字段列表
     *
     * @param keyword   关键字
     * @param modelCode 模型编码
     * @return 字段列表
     */
    @GetMapping("/field/list")
    public R<List<MdField>> fieldList(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String modelCode) {
        return R.ok(platformService.selectFieldList(keyword, modelCode));
    }

    /**
     * 新增或修改字段
     *
     * @param field 字段信息
     * @return 字段编码
     */
    @Log(title = "平台管理", businessType = BusinessType.INSERT)
    @PostMapping("/field")
    public R<String> saveField(@Validated @RequestBody MdField field) {
        return R.ok("字段已保存", platformService.saveField(field));
    }

    /**
     * 查询模型版本列表
     *
     * @return 版本列表
     */
    @GetMapping("/version/list")
    public R<List<PlatformVersionVo>> versionList() {
        return R.ok(platformService.selectVersionList());
    }

    /**
     * 发布模型版本
     *
     * @param version 版本号
     * @return 提示文案
     */
    @Log(title = "平台管理", businessType = BusinessType.UPDATE)
    @PutMapping("/version/publish")
    public R<String> publishVersion(@RequestParam(required = false) String version) {
        return R.ok(platformService.publishVersion(version));
    }

    /**
     * 查询值集列表
     *
     * @return 值集列表
     */
    @GetMapping("/valueset/list")
    public R<List<MdValueSet>> valueSetList() {
        return R.ok(platformService.selectValueSetList());
    }

    /**
     * 新增或修改值集（平台管理：字段与值集 → 值集维护）
     *
     * @param valueSet 值集信息
     * @return 值集编码
     */
    @Log(title = "平台管理", businessType = BusinessType.INSERT)
    @PostMapping("/valueset")
    public R<String> saveValueSet(@Validated @RequestBody MdValueSet valueSet) {
        return R.ok("值集已保存", platformService.saveValueSet(valueSet));
    }

    /**
     * 基于当前已发布版本，克隆出一条新的 Draft 版本（平台管理：模型版本 → 新建版本）
     *
     * @return 新版本号
     */
    @Log(title = "平台管理", businessType = BusinessType.INSERT)
    @PostMapping("/version")
    public R<String> createVersion() {
        return R.ok("已创建新版本", platformService.createVersion());
    }
}
