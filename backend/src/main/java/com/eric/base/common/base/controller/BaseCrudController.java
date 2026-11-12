package com.eric.base.common.base.controller;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.ImportResultVo;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.base.service.IBaseService;
import com.eric.base.common.base.service.ImportExportService;
import com.eric.base.common.base.service.ImportRowHandler;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationRejectedEvent;
import com.eric.base.common.utils.ImportExportUtils;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Eric
 * @date 2026/8/3
 * @description T 继承BaseModel的实体类<br/>
 * S 继承IBaseService的Service<br/>
 * R 继承BaseResolver的解析器<br/>
 * Q 继承BaseRequest的请求对象
 */
public class BaseCrudController<
                T extends BaseModel,
                S extends IBaseService<T>,
                R extends BaseResolver>
        extends BaseController<T, S> {

    /** params JSON 解析器（静态复用：仅解析 Map，无日期等定制需求） */
    private static final ObjectMapper PARAMS_MAPPER = new ObjectMapper();

    private final R baseResolver;

    private final ApplicationEventPublisher eventPublisher;

    /** 导入导出公共编排层；null = 未接入模块（exportExcel / importExcel 守卫抛错） */
    private final ImportExportService importExportService;

    protected BaseCrudController(S baseService, R baseResolver, ApplicationEventPublisher eventPublisher) {
        this(baseService, baseResolver, eventPublisher, null);
    }

    /**
     * 四参构造：接入导入导出公共通道的模块传入 ImportExportService，
     * 未接入模块传 null（既有三参构造委托本构造，既有模块零改动可编译）。
     *
     * @param baseService         模块业务服务
     * @param baseResolver        模块鉴权解析器（必填）
     * @param eventPublisher      鉴权拒绝事件发布器（必填）
     * @param importExportService 导入导出公共编排层；null = 未接入
     */
    protected BaseCrudController(S baseService, R baseResolver, ApplicationEventPublisher eventPublisher,
                                 ImportExportService importExportService) {
        super(baseService);
        this.baseResolver = Objects.requireNonNull(baseResolver, "baseResolver must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.importExportService = importExportService;
    }

    protected final R getBaseResolver() {
        return baseResolver;
    }

    /**
     * 标准新增
     */
    protected T insert(T body) {
        return getBaseService().insert(body);
    }

    /**
     * 标准编辑
     */
    protected T update(T body) {
        return getBaseService().update(body);
    }

    /**
     * 标准删除
     */
    protected void deleteById(String fieldId) {
        getBaseService().deleteById(fieldId);
    }

    /**
     * 标准查询
     */
    protected T selectById(String fieldId) {
        return getBaseService().selectById(fieldId);
    }

    /**
     * 标准列表查询
     */
    protected List<T> selectList(T body, Pages pages) {
        return getBaseService().selectList(body, pages);
    }


    /**
     * 基于请求对象组装鉴权上下文并鉴权，失败时抛出 {@link AuthorizationException}
     */
    protected void authorize(String action, T body) {
        AuthorizationContext context = new AuthorizationContext(action, body);
        authorize(context);
    }

    /**
     * 基于文档 ID 组装鉴权上下文并鉴权，失败时抛出 {@link AuthorizationException}
     */
    protected void authorize(String action, String documentId) {
        AuthorizationContext context = new AuthorizationContext(action, documentId);
        authorize(context);
    }

    /**
     * 执行最终鉴权
     * <p>未登录或凭证已失效（当前用户为空）时抛出 {@code UNAUTHORIZED}，
     * 前端据此清理本地登录态并跳转登录页；已登录但缺少权限时抛出 {@code FORBIDDEN}，
     * 两者由全局异常处理器统一转换为 BaseResponse。</p>
     */
    protected void authorize(AuthorizationContext context) {
        SysOrgUser currentUser = UserUtil.getCurrentUser();
        if (null == currentUser) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!getBaseResolver().resolve(context)) {
            // 统一鉴权入口：FORBIDDEN 时发布鉴权拒绝事件，供日志模块记录安全审计日志
            publishAuthorizationRejected(currentUser.getFieldId(), context);
            throw new AuthorizationException("FORBIDDEN",
                    "no permission for action: " + context.getAction());
        }
    }

    /**
     * 发布鉴权拒绝事件（安全审计）：记录当前用户、接口动作、文档 ID、拒绝原因；
     * 审计日志记录失败不影响鉴权流程本身。
     */
    private void publishAuthorizationRejected(String userId, AuthorizationContext context) {
        try {
            eventPublisher.publishEvent(new AuthorizationRejectedEvent(userId, context.getAction(),
                    context.getDocumentId(), "no permission for action: " + context.getAction()));
        } catch (RuntimeException e) {
            // 忽略：审计失败不得阻断鉴权
        }
    }

    /* ==================== 导入导出公共通道（6.6 契约，不含鉴权） ==================== */

    /**
     * 公共导出：全量查询 → 转导出 VO → 码值转换 + Excel 写出 → 文件流响应。
     * <p>全量查询口径：request 为 null 时无条件查询（criteria / pages 均传 null，
     * mapper 对二者均容忍：字段条件均为动态 if、分页仅 pages 非空且 needPage=true
     * 时生效）；否则 criteria = request.getBody()（可空，等同无条件）、pages 恒为
     * null（导出无分页）。文件名 = {fileNamePrefix}_{yyyyMMddHHmmss}.xlsx。</p>
     * <p>公共方法不含鉴权：authorize 留在各 Controller 方法入口；不给本方法加
     * Swagger 注解（OpenAPI 注解留各模块 Controller 方法）。</p>
     *
     * @param request       列表查询请求（null 时无条件全量；body 作 criteria）
     * @param fileNamePrefix 导出文件名前缀
     * @param exportVoClass  带 @Export 注解的导出 VO 类
     * @param converter      实体列表 → 导出 VO 列表转换函数
     * @return xlsx 文件流响应（attachment）
     */
    protected ResponseEntity<Resource> exportExcel(BaseRequest<?> request, String fileNamePrefix,
            Class<?> exportVoClass, Function<List<T>, List<?>> converter) {
        // BaseRequest<?> 的 getBody() 为 capture 类型，无法直接赋给泛型 T；调用方保证与实体类型一致
        @SuppressWarnings("unchecked")
        T criteria = null == request ? null : (T) request.getBody();
        List<T> rows = getBaseService().selectList(criteria, null);
        return exportExcel(fileNamePrefix, exportVoClass, rows, converter);
    }

    /**
     * 公共导出（行数据给定重载）：与 {@link #exportExcel(BaseRequest, String, Class, Function)}
     * 唯一差异是数据行不经泛型实体查询、由调用方直接给定，供「导入导出行实体 ≠ Controller
     * 泛型实体」的模块使用（dict 试点：泛型实体为 sys_dict_type，行实体为 sys_dict_item，
     * 泛型实体查询与导入导出对象错位，IE4-BE-001 R3 契约缺口增量）。
     * <p>转换 → 码值转换 + Excel 写出 → 文件流响应；文件名 = {fileNamePrefix}_{yyyyMMddHHmmss}.xlsx。
     * 公共方法不含鉴权：authorize 留在各 Controller 方法入口；不给本方法加 Swagger 注解。</p>
     *
     * @param fileNamePrefix 导出文件名前缀
     * @param exportVoClass  带 @Export 注解的导出 VO 类
     * @param rows           数据行（行实体列表，通常由模块 Dao 全量查询）；null 视为仅标题行
     * @param converter      行实体列表 → 导出 VO 列表转换函数
     * @param <E>            行实体类型
     * @param <R>            转换结果列表类型（Function 返回侧用独立类型变量声明，
     *                       使 Function&lt;List&lt;E&gt;, List&lt;String&gt;&gt; 等具体返回类型可推断；
     *                       若声明为 Function&lt;List&lt;E&gt;, List&lt;?&gt;&gt;，E 位于嵌套位置
     *                       且返回侧为通配符，调用点无法推断 E）
     * @return xlsx 文件流响应（attachment）
     */
    protected <E extends BaseModel, R extends List<?>> ResponseEntity<Resource> exportExcel(String fileNamePrefix,
            Class<?> exportVoClass, List<E> rows, Function<List<E>, R> converter) {
        requireImportExport();
        List<?> exportRows = converter.apply(null == rows ? List.of() : rows);
        byte[] bytes = importExportService.export(exportRows, exportVoClass);
        return toExcelResponse(bytes, fileNamePrefix + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx");
    }

    /**
     * 公共导入：multipart file + params → 编排导入（解析 / 通用校验 / 码值回转 /
     * rowHandler 模块校验 / 分批 insert）→ 统一响应。
     * <p>params 为 JSON 对象字符串（如 {"ignoreError":true}）；null / 空白串视为
     * 空 Map；非法 JSON 抛 {@link IllegalArgumentException}("导入配置解析失败")。</p>
     *
     * @param file         上传的 xlsx 文件
     * @param params       导入参数 JSON 字符串（公共开关 ignoreError；fullUpdate 透传）
     * @param importVoClass 带 @Export 注解的导入 VO 类
     * @param rowHandler   模块行级业务校验扩展点；null 时跳过模块校验
     * @param toEntity     VO → 实体转换函数
     * @param <V>          导入 VO 类型
     * @return 统一响应，data 为导入结果（total / success / failed / errors）
     */
    protected <V> BaseResponse<ImportResultVo> importExcel(MultipartFile file, String params,
            Class<V> importVoClass, ImportRowHandler<V> rowHandler, Function<V, T> toEntity) {
        return importExcel(file, params, importVoClass, rowHandler, toEntity, getBaseService());
    }

    /**
     * 公共导入（落库 Service 给定重载）：与 {@link #importExcel(MultipartFile, String, Class,
     * ImportRowHandler, Function)} 唯一差异是落库走调用方给定的 {@link IBaseService}，
     * 供「导入导出行实体 ≠ Controller 泛型实体」的模块使用（dict 试点：泛型实体为
     * sys_dict_type，行实体为 sys_dict_item，IE4-BE-001 R3 契约缺口增量）。
     *
     * @param file              上传的 xlsx 文件
     * @param params            导入参数 JSON 字符串（公共开关 ignoreError；fullUpdate 透传）
     * @param importVoClass     带 @Export 注解的导入 VO 类
     * @param rowHandler        模块行级业务校验扩展点；null 时跳过模块校验
     * @param toEntity          VO → 实体转换函数
     * @param persistenceService 行实体落库服务（分批 insert）；通常为行实体所属模块的 Service
     * @param <V>               导入 VO 类型
     * @param <E>               行实体类型
     * @return 统一响应，data 为导入结果（total / success / failed / errors）
     */
    protected <V, E extends BaseModel> BaseResponse<ImportResultVo> importExcel(MultipartFile file, String params,
            Class<V> importVoClass, ImportRowHandler<V> rowHandler, Function<V, E> toEntity,
            IBaseService<E> persistenceService) {
        requireImportExport();
        Map<String, Object> paramMap = parseParams(params);
        ImportResultVo result = importExportService.importExcel(file, paramMap, importVoClass,
                rowHandler, toEntity, persistenceService);
        return BaseResponse.success(result, null);
    }

    /**
     * 公共模板：空模板文件流响应；文件名 = {fileNamePrefix}_template.xlsx。
     * <p>不守卫：模板走静态 {@link ImportExportUtils}，不需要
     * {@link ImportExportService}（6.6 原文）。</p>
     *
     * @param fileNamePrefix 模板文件名前缀
     * @param voClass        带 @Export 注解的导入导出 VO 类
     * @return xlsx 文件流响应（attachment，仅标题行）
     */
    protected ResponseEntity<Resource> exportTemplate(String fileNamePrefix, Class<?> voClass) {
        byte[] bytes = ImportExportUtils.template(voClass);
        return toExcelResponse(bytes, fileNamePrefix + "_template.xlsx");
    }

    /** 守卫：模块未接入导入导出公共通道时抛出，提示由四参构造传 Service 接入 */
    private void requireImportExport() {
        if (null == importExportService) {
            throw new IllegalStateException("当前模块未接入导入导出公共通道");
        }
    }

    /** params JSON 解析：null / 空白串 → 空 Map；非法 JSON 抛「导入配置解析失败」 */
    private static Map<String, Object> parseParams(String params) {
        if (null == params || params.isBlank()) {
            return Map.of();
        }
        try {
            return PARAMS_MAPPER.readValue(params,
                    PARAMS_MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("导入配置解析失败", e);
        }
    }

    /** xlsx 文件流响应：对齐 i18n 先例（URLEncoder + filename*=UTF-8'' + octet-stream） */
    private static ResponseEntity<Resource> toExcelResponse(byte[] bytes, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(bytes));
    }
}
