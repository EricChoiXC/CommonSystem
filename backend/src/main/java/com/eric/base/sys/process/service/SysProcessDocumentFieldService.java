package com.eric.base.sys.process.service;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.properties.SysProcessTemplateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程文档字段读取服务
 *
 * @author Eric
 * @date 2026/9/28
 * @description 供流程流转引擎在「审批来源=表单字段」时读取业务文档字段值
 *              （依据 artificial/docs/设计文档/sys/process/流程核心业务.md「审批人配置」）：
 *              按 process_template.${fieldModelName}.class 定位文档实体类，经容器内 IBaseMapper
 *              按实体泛型参数建立索引后按 id 读取文档，再反射取出指定字段值。
 *              文档实体字段字典由各模块 process_template.${fieldModelName}.fields 维护。
 */
@Service
public class SysProcessDocumentFieldService {

    private static final Logger log = LoggerFactory.getLogger(SysProcessDocumentFieldService.class);

    private final ApplicationContext applicationContext;

    /** 文档实体类 -> 对应 IBaseMapper（首次使用时一次性构建并缓存） */
    private volatile Map<Class<?>, IBaseMapper<?>> mapperByEntity;

    public SysProcessDocumentFieldService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 读取流程所属业务文档指定字段的值（String 化）。
     *
     * @param modelName 模板分类（process_template 二级配置项，即业务文档 field_model_name）
     * @param modelId   业务文档 id
     * @param fieldName 文档实体字段属性名（取自分类 fields 字典）
     * @return 字段值字符串；任一参数为空、实体类/Mapper 缺失或字段值不存在时返回 null
     */
    public String resolveFieldValue(String modelName, String modelId, String fieldName) {
        if (StringUtil.isEmpty(modelName) || StringUtil.isEmpty(modelId) || StringUtil.isEmpty(fieldName)) {
            return null;
        }
        String className = SysProcessTemplateProperties.getClassName(modelName.trim());
        if (StringUtil.isEmpty(className)) {
            log.warn("process_template 未配置文档实体类，无法读取审批人字段: model={}", modelName);
            return null;
        }
        Class<?> entityClass = loadClassQuietly(className);
        if (null == entityClass) {
            log.warn("文档实体类加载失败，无法读取审批人字段: {}", className);
            return null;
        }
        IBaseMapper<?> mapper = findMapper(entityClass);
        if (null == mapper) {
            log.warn("未找到文档实体类对应的 Mapper，无法读取审批人字段: {}", className);
            return null;
        }
        Object model = mapper.selectById(modelId.trim());
        if (null == model) {
            return null;
        }
        Object value = readField(model, fieldName.trim());
        return null == value ? null : String.valueOf(value);
    }

    /** 从「实体类 -> Mapper」索引中取实体对应 Mapper（懒加载构建索引）。 */
    private IBaseMapper<?> findMapper(Class<?> entityClass) {
        Map<Class<?>, IBaseMapper<?>> mappers = this.mapperByEntity;
        if (null == mappers) {
            synchronized (this) {
                mappers = this.mapperByEntity;
                if (null == mappers) {
                    mappers = buildMapperIndex();
                    this.mapperByEntity = mappers;
                }
            }
        }
        return mappers.get(entityClass);
    }

    /** 扫描容器内全部 IBaseMapper，按其实体泛型参数建立「实体类 -> Mapper」索引。 */
    private Map<Class<?>, IBaseMapper<?>> buildMapperIndex() {
        Map<Class<?>, IBaseMapper<?>> index = new LinkedHashMap<>();
        Map<String, IBaseMapper> beans = applicationContext.getBeansOfType(IBaseMapper.class);
        for (IBaseMapper<?> mapper : beans.values()) {
            Class<?> entityClass = resolveEntityClass(mapper.getClass());
            if (null != entityClass && !index.containsKey(entityClass)) {
                index.put(entityClass, mapper);
            }
        }
        return index;
    }

    /** 从 Mapper 实现类（MyBatis 代理）实现的接口中解析 IBaseMapper 的实体泛型参数。 */
    private static Class<?> resolveEntityClass(Class<?> mapperClass) {
        for (Class<?> iface : ClassUtils.getAllInterfacesForClass(mapperClass)) {
            Class<?> entity = GenericTypeResolver.resolveTypeArgument(iface, IBaseMapper.class);
            if (null != entity) {
                return entity;
            }
        }
        return null;
    }

    /** 反射读取文档实体指定字段（含父类字段）。 */
    private static Object readField(Object model, String fieldName) {
        Field field = ReflectionUtils.findField(model.getClass(), fieldName);
        if (null == field) {
            log.warn("文档实体类不存在审批人字段: {}.{}", model.getClass().getName(), fieldName);
            return null;
        }
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, model);
    }

    private static Class<?> loadClassQuietly(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
