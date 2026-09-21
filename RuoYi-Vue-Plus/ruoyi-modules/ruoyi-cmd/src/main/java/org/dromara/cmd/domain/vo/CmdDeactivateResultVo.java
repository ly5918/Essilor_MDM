package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 逻辑停用 结果视图对象
 * <p>
 * 对应页面：变更与停用 change 的「逻辑停用 · 数据库结果」弹窗，
 * 用「业务视图 + 落库记录」双视角证明无物理删除：
 * 业务侧只看到状态从 Active 变为 Inactive，后台记录显示
 * status 被改写、is_deleted 仍为 false、One ID 未回收、版本继续递增。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdDeactivateResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务视图：人可读的键值对（One ID / 状态 / 停用原因 / 是否物理删除 ...） */
    private List<KvVo> businessView = new ArrayList<>();

    /** 后台记录：逐条列出实际落库的字段与取值，作为「无物理删除」的证据 */
    private List<String> dbRecords = new ArrayList<>();

    /** 该 One ID 的完整版本链（含停用版本），证明历史全部保留 */
    private List<CmdCustomerVersionVo> versions = new ArrayList<>();

    /** 键值对 */
    @Data
    public static class KvVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 展示项名称 */
        private String key;

        /** 展示项取值 */
        private String value;

        /**
         * 构造键值对
         *
         * @param key   名称
         * @param value 取值
         * @return 键值对
         */
        public static KvVo of(String key, String value) {
            KvVo vo = new KvVo();
            vo.setKey(key);
            vo.setValue(value);
            return vo;
        }
    }
}
