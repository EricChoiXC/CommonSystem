package com.eric.base.common.module.controller;

import com.eric.base.common.base.controller.BaseController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.module.model.CommonModule;
import com.eric.base.common.module.request.CommonModuleRequest;
import com.eric.base.common.module.resolver.ModuleResolver;
import com.eric.base.common.module.service.CommonModuleService;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_ENABLE;
import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_LIST;
import static com.eric.base.common.module.properties.CommonModuleProperties.ROLE_MODULE_ADMIN;

/**
 * 模块组管理接口
 *
 * @author Eric
 * @date 2026/11/12
 * @description 后台管理-模块管理：模块组开关状态列表与启停。本 Controller 不打 @ModuleGroup
 *              注解——关闭任意模块组后管理面必须仍可用（可经本接口恢复开启）。
 *              所有接口执行核心业务逻辑前先经 ModuleResolver 完成鉴权：
 *              list 登录即可，enable 仅 SYS_MODULE_ADMIN（SYS_ADMIN 自动放行）；
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/common/module")
@Tag(name = "模块管理", description = "后台管理-模块管理：模块组开关状态列表与启停；"
        + "list 登录即可，enable 仅 " + ROLE_MODULE_ADMIN + "（SYS_ADMIN 自动放行）；"
        + "本接口自身不受模块组开关控制（无 @ModuleGroup 注解）")
public class CommonModuleController extends BaseController<CommonModule, CommonModuleService> {

    private final ModuleResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public CommonModuleController(CommonModuleService baseService,
                                  ModuleResolver baseResolver,
                                  AuthorizationHelper authorizationHelper) {
        super(baseService);
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 模块组列表：POST /backend/common/module/list
     * 请求体遵循 list 通道 { body, pages: { pageNo, pageSize, needPage } }；body 首批无筛选字段，
     * 模块组量极小默认全量返回（按 field_key 排序），metadata 不强制携带 total。
     *
     * @param request 列表请求（条件 + 分页），当前不参与查询
     * @return 统一响应，data 为模块组列表
     */
    @PostMapping("/list")
    @Operation(summary = "模块组列表", description = "鉴权动作 " + ACTION_LIST + "（登录即可，无角色要求）；"
            + "请求体 { body, pages }，body 为查询条件预留位（首批无筛选字段），"
            + "模块组量极小默认全量返回，按 field_key 排序，metadata 不强制携带 total")
    public BaseResponse<List<CommonModule>> list(@RequestBody CommonModuleRequest request) {
        authorizationHelper.authorize(baseResolver, ACTION_LIST);
        return BaseResponse.success(getBaseService().listModules(), null);
    }

    /**
     * 启停用模块组：PATCH /backend/common/module/enable
     * 请求体为 { fieldId, fieldEnable }，仅更新启用状态；成功后刷新本实例状态缓存
     * 并经 Redis 广播失效通知其他实例（best-effort，失败依赖 TTL 收敛）。
     *
     * @param body 请求体 { fieldId, fieldEnable }，fieldId 必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用模块组", description = "鉴权动作 " + ACTION_ENABLE + "（仅 " + ROLE_MODULE_ADMIN
            + "，SYS_ADMIN 自动放行）；请求体 { fieldId, fieldEnable }，仅更新启用状态，"
            + "成功后刷新本实例状态缓存并广播其他实例（best-effort，失败依赖 TTL 收敛），"
            + "缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<Void> enable(@RequestBody CommonModule body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorizationHelper.authorize(baseResolver, ACTION_ENABLE, body.getFieldId());
        getBaseService().updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }
}
