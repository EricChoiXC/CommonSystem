
# 二、BPM（Business Process Management，业务流程管理）系统

## 包路径

com.eric.base.sys.bpm

## 前置基础功能

- 流程管理改造 v1.7
- 编号机制 v1.6
- 自定义表单 v1.5
- 公式定义器 v1.4

## 基本功能描述

由管理员维护的 流程分类 + 流程模板（如【考勤管理-请假、销假】）；
用户创建流程实例进行审批；触发相关业务逻辑

## 基本表结构

### 流程分类和流程模板

```mermaid
erDiagram
    sys_bpm_category {
        field_id varchar(32) PK
        field_name varchar(200) "分类名称"
        field_parent_id varchar(32) "上级分类ID"
        field_order int "排序"
        field_creator_id varchar(32) "创建人ID"
        field_create_time timestamp "创建时间"
        field_updater_id varchar(32) "更新人ID"
        field_update_time timestamp "更新时间"
        field_enabled boolean "是否启用"
    }
%%    可使用者
    sys_bpm_category_viewer {
        field_category_id varchar(32) "分类id"
        field_element_id varchar(32) "组织架构id"
    }
    
    sys_bpm_template {
        field_id varchar(32) PK
        field_name varchar(200) "模板名称"
        field_category_id varchar(32) "分类ID"
        field_description varchar(200) "描述"
        field_version int "版本号"
        field_order int "排序"
        field_creator_id varchar(32) "创建人ID"
        field_create_time timestamp "创建时间"
        field_updater_id varchar(32) "更新人ID"
        field_update_time timestamp "更新时间"
        field_enabled boolean "是否启用"
        field_process_xml json "流程定义"
        field_form_xml text "表单定义XML"
        field_number_rule json "编号规则"
        field_auto_title boolean "自动生成标题"
        field_auto_title_rule json "自动标题规则"
    }
%%    可使用者
    sys_bpm_template_viewer {
        field_template_id varchar(32) "模板id"
        field_element_id varchar(32) "组织架构id" 
    }
%%    历史版本
    sys_bpm_template_history {
        field_id varchar(32) PK
        field_template_id varchar(32) "模板ID"
        field_version int "版本号"
        field_process_xml json "流程定义"
        field_form_xml text "表单定义XML"
        field_number_rule json "编号规则"
        field_auto_title boolean "自动生成标题"
        field_auto_title_rule json "自动标题规则"
    }
```

### 流程实例

```mermaid
erDiagram
    sys_bpm_main {
        field_id varchar(32) PK
        field_code varchar(200) "编号"
        field_name varchar(200) "标题"
        field_template_id varchar(32) "模板ID"
        field_template_version int "模板版本"
        field_creator_id varchar(32) "创建人ID"
        field_create_time timestamp "创建时间"
        field_updater_id varchar(32) "更新人ID"
        field_update_time timestamp "更新时间"
        field_doc_status varchar(20) "单据状态"
        field_form_data json "表单数据"
    }
```