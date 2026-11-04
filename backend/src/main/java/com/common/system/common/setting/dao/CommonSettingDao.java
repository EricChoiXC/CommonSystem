package com.common.system.common.setting.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.setting.mapper.CommonSettingMapper;
import com.common.system.common.setting.model.CommonSetting;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 公共配置信息数据访问对象
 *
 * @author Eric
 * @date 2026/8/19
 * @description 与 CommonSettingMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class CommonSettingDao extends BaseDao<CommonSetting, CommonSettingMapper> {

    public CommonSettingDao(CommonSettingMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按类型 + 配置字段查询（系统启动增量同步与内部读取时使用）
     */
    public CommonSetting selectByTypeAndKey(String fieldType, String fieldKey) {
        return getBaseMapper().selectByTypeAndKey(fieldType, fieldKey);
    }

    /**
     * 按类型查询全部配置（内部按类型读取配置值集合）
     */
    public List<CommonSetting> selectByType(String fieldType) {
        return getBaseMapper().selectByType(fieldType);
    }
}
