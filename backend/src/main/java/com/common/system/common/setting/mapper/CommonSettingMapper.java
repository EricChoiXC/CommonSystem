package com.common.system.common.setting.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.setting.model.CommonSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公共配置信息数据访问映射
 *
 * @author Eric
 * @date 2026/8/19
 * @description 与 common_setting 一对一对接，不承载业务逻辑。
 */
@Mapper
public interface CommonSettingMapper extends IBaseMapper<CommonSetting> {

    /**
     * 按类型 + 配置字段查询（系统启动增量同步与内部读取时使用）
     */
    CommonSetting selectByTypeAndKey(@Param("fieldType") String fieldType, @Param("fieldKey") String fieldKey);

    /**
     * 按类型查询全部配置（内部按类型读取配置值集合）
     */
    List<CommonSetting> selectByType(@Param("fieldType") String fieldType);
}
