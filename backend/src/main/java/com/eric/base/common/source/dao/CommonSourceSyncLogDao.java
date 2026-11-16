package com.eric.base.common.source.dao;

import com.eric.base.common.source.mapper.CommonSourceSyncLogMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表结构注解同步审计数据访问对象
 *
 * @author Eric
 * @date 2026/11/16
 * @description 与 CommonSourceSyncLogMapper 一对一对接，不承载业务逻辑；对应审计表
 *              common_source_sync_log（设计方案 §5.3，流水类口径只增不改），仅提供
 *              汇总审计行写入与 managed 历史表清单查询，无 update / delete 语义，
 *              因此不走 BaseDao 基类（BaseDao 承载 BaseModel 实体 CRUD，审计表无对应实体模型）。
 *              <p>Mapper 经 ObjectProvider 惰性构造注入（对齐同包 SourceSchemaDiffer 先例）：
 *              运行容器经 mybatis starter 自动装配必含 Mapper；排除 MyBatis 自动配置的
 *              测试上下文（CommonSystemApplicationTests）装配本 Bean 时以 null 持有不阻断
 *              加载，实际调用时显式校验抛 IllegalStateException。</p>
 */
@Repository
public class CommonSourceSyncLogDao {

    /** 审计 Mapper（测试上下文无 MyBatis 自动配置时为 null，调用时校验） */
    private final CommonSourceSyncLogMapper mapper;

    /**
     * 构造注入 Mapper（ObjectProvider 惰性形态）
     *
     * @param mapperProvider Mapper 提供者
     */
    public CommonSourceSyncLogDao(ObjectProvider<CommonSourceSyncLogMapper> mapperProvider) {
        this.mapper = mapperProvider.getIfAvailable();
    }

    /**
     * 汇总审计行落库（SUCCESS / FAILED 各一条，只增不改；字段对齐设计方案 §5.3）。
     * field_sync_time / field_create_time 由库默认 CURRENT_TIMESTAMP 维护。
     *
     * @param fieldId         主键 ID（应用侧 IDUtil.initID 生成）
     * @param fieldMode       同步模式（VALIDATE/INSTALL/APPLY）
     * @param fieldStatus     结果（SUCCESS/FAILED）
     * @param fieldTableTotal 标注表数量
     * @param fieldChangeTotal 变更条数（含 L1/L2/L3）
     * @param fieldChanges    变更明细 JSON（[{level,type,table,detail,sql}]，无明细为 null）
     * @param fieldError      失败原因（异常链；SUCCESS 为 null）
     * @param fieldCostMs     耗时毫秒
     * @param fieldCreatorId  创建者 ID（引擎系统身份）
     * @return 影响行数
     */
    public int insert(String fieldId, String fieldMode, String fieldStatus, int fieldTableTotal,
                      int fieldChangeTotal, String fieldChanges, String fieldError,
                      long fieldCostMs, String fieldCreatorId) {
        requireMapper("审计行写入");
        return mapper.insert(fieldId, fieldMode, fieldStatus, fieldTableTotal, fieldChangeTotal,
                fieldChanges, fieldError, fieldCostMs, fieldCreatorId);
    }

    /**
     * managed 历史表清单：历史审计行 field_changes JSON 明细提取去重表名（XML 内 JSON_TABLE 实现）。
     * managed 口径 = 当前标注表 ∪ 本查询产出（设计方案 §4.4；实现口径偏差由主控回写）。
     *
     * @return 历史标注表名清单（去重）
     */
    public List<String> selectManagedTables() {
        requireMapper("managed 历史表清单查询");
        return mapper.selectManagedTables();
    }

    /**
     * Mapper 可用性校验（测试上下文无 MyBatis 自动配置时为 null，显式 fail-fast）
     */
    private void requireMapper(String action) {
        if (null == mapper) {
            throw new IllegalStateException("CommonSourceSyncLogMapper 不可用（MyBatis 未装配），无法执行" + action);
        }
    }
}
