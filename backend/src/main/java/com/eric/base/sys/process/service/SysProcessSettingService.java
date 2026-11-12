package com.eric.base.sys.process.service;

import com.eric.base.common.setting.dao.CommonSettingDao;
import com.eric.base.common.setting.model.CommonSetting;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.request.SysProcessSettingSaveRequest;
import com.eric.base.sys.process.response.SysProcessSettingVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_DESC;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_TYPES;

/**
 * 流程公共配置业务服务
 *
 * @author Eric
 * @date 2026/8/21
 * @description 承载「流程公共配置」页面的后端能力（依据 artificial/docs/设计文档/sys/process/流程核心业务.md）：
 *              <ul>
 *              <li>列表：读取本模块公共配置（field_type ∈ {process, notify}），附加中文描述供页面展示；</li>
 *              <li>提交：仅将有更新（或重置）的配置项保存（复用 CommonSettingService 的配置值维护）；</li>
 *              <li>重置：将单个配置项还原为默认值。</li>
 *              </ul>
 *              配置值的持久化统一收敛到 CommonSettingService.updateValue，本服务不直接写库。
 */
@Service
public class SysProcessSettingService {

    private static final Logger log = LoggerFactory.getLogger(SysProcessSettingService.class);

    private final CommonSettingDao commonSettingDao;
    private final CommonSettingService commonSettingService;

    public SysProcessSettingService(CommonSettingDao commonSettingDao,
                                    CommonSettingService commonSettingService) {
        this.commonSettingDao = commonSettingDao;
        this.commonSettingService = commonSettingService;
    }

    /**
     * 流程公共配置列表：按配置类型（process / notify）+ 配置字段排序返回，
     * 附带中文描述；配置类型顺序与文档「流程公共配置」保持一致。
     */
    public List<SysProcessSettingVo> listSettings() {
        List<SysProcessSettingVo> result = new ArrayList<>();
        for (String type : SETTING_TYPES) {
            for (CommonSetting setting : commonSettingDao.selectByType(type)) {
                SysProcessSettingVo vo = new SysProcessSettingVo();
                vo.setFieldId(setting.getFieldId());
                vo.setFieldType(setting.getFieldType());
                vo.setFieldKey(setting.getFieldKey());
                vo.setFieldValue(setting.getFieldValue());
                vo.setFieldDefaultValue(setting.getFieldDefaultValue());
                vo.setFieldUpdateTime(setting.getFieldUpdateTime());
                vo.setFieldDesc(SETTING_DESC.getOrDefault(type + "." + setting.getFieldKey(), setting.getFieldKey()));
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 流程公共配置提交：仅保存有更新（或重置）的配置项；
     * 配置不存在或提交值为空时抛出参数异常。
     *
     * @param request 提交请求（items 可为空，空列表不处理）
     */
    @Transactional
    public void saveSettings(SysProcessSettingSaveRequest request) {
        if (null == request || null == request.getItems() || request.getItems().isEmpty()) {
            log.info("process setting save: no changed item, skip");
            return;
        }
        for (SysProcessSettingSaveRequest.SysProcessSettingItem item : request.getItems()) {
            if (null == item || StringUtil.isEmpty(item.getFieldId())) {
                throw new IllegalArgumentException("配置项 id 不能为空");
            }
            CommonSetting setting = commonSettingDao.selectById(item.getFieldId());
            if (null == setting) {
                throw new IllegalArgumentException("配置不存在: " + item.getFieldId());
            }
            // 越权防护：仅允许修改本模块（process / notify 类型）的公共配置，且配置值不可为空
            if (!SETTING_TYPES.contains(setting.getFieldType())) {
                throw new IllegalArgumentException("配置不属于流程模块: " + item.getFieldId());
            }
            if (null == item.getFieldValue()) {
                throw new IllegalArgumentException("配置值不能为空: " + item.getFieldId());
            }
            commonSettingService.updateValue(setting.getFieldType(), setting.getFieldKey(), item.getFieldValue());
        }
        log.info("process setting saved: {} items", request.getItems().size());
    }

    /**
     * 流程公共配置重置：将单个配置项还原为默认值。
     *
     * @param fieldId 配置项id（common_setting.field_id）
     */
    @Transactional
    public void resetSetting(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("配置项 id 不能为空");
        }
        CommonSetting setting = commonSettingDao.selectById(fieldId);
        if (null == setting) {
            throw new IllegalArgumentException("配置不存在: " + fieldId);
        }
        // 越权防护：仅允许重置本模块（process / notify 类型）的公共配置
        if (!SETTING_TYPES.contains(setting.getFieldType())) {
            throw new IllegalArgumentException("配置不属于流程模块: " + fieldId);
        }
        commonSettingService.updateValue(setting.getFieldType(), setting.getFieldKey(), setting.getFieldDefaultValue());
        log.info("process setting reset: id={}, key={}.{}", fieldId, setting.getFieldType(), setting.getFieldKey());
    }
}
