package org.dromara.cmd.service;

import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdMergeRecord;
import org.dromara.cmd.domain.CmdRole;
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.MdValueSet;
import org.dromara.cmd.domain.OneIdRule;
import org.dromara.cmd.domain.vo.OneIdPolicyVo;
import org.dromara.cmd.domain.vo.PermissionMatrixVo;
import org.dromara.cmd.domain.vo.PlatformVersionVo;

import java.util.List;

/**
 * 平台管理 / One ID 服务层
 * <p>
 * 对应页面：平台管理 admin（字段与值集、角色与权限）、One ID 规则管理 oneid。
 *
 * @author Essilor CMD POC
 */
public interface ICmdPlatformService {

    /**
     * 查询元数据字段列表
     *
     * @param keyword   关键字（字段编码 / 名称）
     * @param modelCode 模型编码
     * @return 字段列表
     */
    List<MdField> selectFieldList(String keyword, String modelCode);

    /**
     * 新增或修改元数据字段
     *
     * @param field 字段信息
     * @return 字段编码
     */
    String saveField(MdField field);

    /**
     * 查询值集列表
     *
     * @return 值集列表
     */
    List<MdValueSet> selectValueSetList();

    /**
     * 新增或修改值集（按 set_code 幂等 upsert）
     *
     * @param valueSet 值集信息
     * @return 值集编码
     */
    String saveValueSet(MdValueSet valueSet);

    /**
     * 查询模型版本列表（按字段版本号聚合）
     *
     * @return 版本列表
     */
    List<PlatformVersionVo> selectVersionList();

    /**
     * 查询技术角色列表
     *
     * @return 角色列表
     */
    List<CmdRole> selectRoleList();

    /**
     * 查询权限矩阵（能力 × 角色）
     *
     * @return 权限矩阵
     */
    List<PermissionMatrixVo> selectPermissionMatrix();

    /**
     * 新增或修改角色权限
     *
     * @param role 角色信息
     * @return 角色编码
     */
    String saveRole(CmdRole role);

    /**
     * 批量保存角色权限
     *
     * @param roles 角色列表
     * @return 提示文案
     */
    String saveRoles(List<CmdRole> roles);

    /**
     * 查询默认 One ID 规则
     *
     * @return 规则
     */
    OneIdRule selectOneIdRule();

    /**
     * 保存（更新）当前默认 One ID 规则
     *
     * @param rule 规则修改内容
     * @return 提示文案
     */
    String saveOneIdRule(OneIdRule rule);

    /**
     * 发布 One ID 规则
     *
     * @return 提示文案
     */
    String publishOneIdRule();

    /**
     * 复制 One ID 规则为草稿
     *
     * @return 提示文案
     */
    String copyOneIdRule();

    /**
     * 按当前生效规则生成下一个 One ID
     *
     * @return One ID 编码
     */
    String generateOneId();

    /**
     * 查询 Legacy Code ↔ One ID 映射
     *
     * @param oneId One ID，为空时查全部
     * @return 映射列表
     */
    List<CmdLegacyMapping> selectLegacyMapping(String oneId);

    /**
     * 发布指定模型版本
     *
     * @param version 版本号
     * @return 提示文案
     */
    String publishVersion(String version);

    /**
     * 基于当前已发布版本，克隆出一条新的 Draft 版本（字段整体复制，状态置为 Draft）
     *
     * @return 新版本号
     */
    String createVersion();

    /**
     * 查询 One ID 生成与状态策略
     *
     * @return 策略列表
     */
    List<OneIdPolicyVo> selectOneIdPolicy();

    /**
     * 查询某个 One ID 的变更历史（取自审计事件）
     *
     * @param oneId One ID
     * @return 历史事件
     */
    List<AuditEvent> selectOneIdHistory(String oneId);

    /**
     * 查询某个 One ID 的合并记录（总设计「审计与合并记录」）
     * <p>双向口径：作为保留方（survivor）或被合并方（merged）参与的合并单均返回。
     *
     * @param oneId One ID
     * @return 合并记录列表（按时间倒序）
     */
    List<CmdMergeRecord> selectMergeRecords(String oneId);
}
