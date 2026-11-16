在企业级 OA / BPM 系统中，\*\*公式定义器（Formula Designer）\*\*建议作为动态表单引擎的一个独立功能模块开发，而不是简单地做成一个公式输入框。

它的核心目标是：让管理员通过可视化方式定义业务计算公式，并将公式应用于表单字段、审批条件、流程分支、数据校验和业务规则中。

例如：

- 请假天数 = 结束日期 - 开始日期
- 报销总金额 = 明细表所有金额之和
- 含税金额 = 未税金额 × (1 + 税率)
- 审批条件 = 申请金额 > 10000
- 是否显示某字段 = 员工类型 == "正式员工"

我建议将它设计为一个**可视化公式编辑器 + 公式解析引擎 + 运行时执行引擎**的完整体系。

# 一、整体架构设计

**公式定义器 Formula Designer**

可视化编辑、函数选择、字段引用、公式预览

**公式定义层**

公式、参数、返回类型、配置

**函数管理层**

内置函数、自定义函数、扩展函数

**公式解析引擎**

词法分析、语法分析、类型检查、依赖分析

**公式运行引擎**

上下文注入、公式计算、异常处理、结果返回

动态表单计算

字段动态显隐

字段动态校验

流程条件判断

这里需要特别注意：公式定义、公式解析、公式执行应该分开。这样以后不仅能给 OA 表单使用，也可以给 BPM 流程引擎、业务规则引擎使用。

# 二、公式定义器的前端设计

建议采用 Vue + CodeMirror 6 或 Monaco Editor 实现编辑区域，再配合自己的可视化函数面板。

推荐分为四个区域：

| 区域      | 功能                |
| ------- | ----------------- |
| 左侧字段面板  | 当前表单字段、明细表字段、系统变量 |
| 中间公式编辑区 | 输入公式、插入字段、语法高亮    |
| 右侧函数面板  | 数学、日期、字符串、逻辑、集合函数 |
| 底部调试区   | 测试数据、计算结果、错误提示    |

## 1. 字段引用

不能简单地让用户输入字段名称。

建议给每个字段定义唯一标识：

```
{
  "fieldId": "field_1001",
  "fieldName": "申请金额",
  "fieldCode": "applyAmount",
  "dataType": "DECIMAL"
}
```

公式中使用稳定的字段标识，而不是显示名称。

例如：

```
${field_1001} * 0.13
```

界面显示：

```
申请金额 × 0.13
```

这样即使管理员修改字段名称，也不会导致公式失效。

## 2. 明细表字段引用

明细表需要特殊处理。

例如报销明细：

| 项目  | 金额   |
| --- | ---- |
| 差旅费 | 1000 |
| 住宿费 | 800  |
| 餐饮费 | 200  |

公式：

```
SUM(detailTable.amount)
```

建议支持：

| 函数      | 作用   |
| ------- | ---- |
| SUM     | 求和   |
| AVG     | 平均值  |
| MAX     | 最大值  |
| MIN     | 最小值  |
| COUNT   | 数量   |
| COUNTIF | 条件计数 |
| SUMIF   | 条件求和 |

还应支持明细表行内计算：

```
quantity * unitPrice
```

以及明细表汇总：

```
SUM(expenseDetails.totalAmount)
```

明细表字段应具有作用域，避免不同明细表中相同字段编码产生冲突。

# 三、公式函数体系设计

公式定义器的价值，很大程度取决于函数体系是否完善。

建议将函数分成以下几类。

| 分类    | 函数示例                                   | 用途      |
| ----- | -------------------------------------- | ------- |
| 数学函数  | ABS、ROUND、CEIL、FLOOR、POWER             | 数值计算    |
| 逻辑函数  | IF、AND、OR、NOT、IFNULL                   | 条件判断    |
| 比较函数  | EQ、NE、GT、GTE、LT、LTE                    | 数值和文本比较 |
| 字符串函数 | CONCAT、SUBSTRING、LENGTH、TRIM           | 字符串处理   |
| 日期函数  | NOW、TODAY、DATEADD、DATEDIFF、YEAR        | 日期计算    |
| 集合函数  | SUM、AVG、COUNT、MAX、MIN                  | 明细表统计   |
| 类型转换  | TO_NUMBER、TO_STRING、TO_DATE            | 类型转换    |
| 系统函数  | CURRENT_USER、CURRENT_DEPT、CURRENT_ROLE | 系统上下文   |
| 表单函数  | GET_FIELD、GET_DETAIL、GET_PARENT        | 表单数据读取  |

### 1. 函数定义也要配置化

不建议把函数名称、参数说明、返回值等信息直接写死在 Vue 页面中。

建议设计统一函数注册表：

```
{
  "functionCode": "ROUND",
  "functionName": "四舍五入",
  "category": "MATH",
  "description": "对数值进行精度处理",
  "parameters": [
    {
      "name": "value",
      "type": "NUMBER",
      "required": true
    },
    {
      "name": "precision",
      "type": "INTEGER",
      "required": false,
      "defaultValue": 2
    }
  ],
  "returnType": "NUMBER"
}
```

这样可以实现：

- 函数自动展示。
- 自动生成参数输入提示。
- 函数参数类型校验。
- 函数使用说明。
- 自定义函数扩展。
- 前后端共用函数定义。

### 2. 可视化函数插入

例如用户点击 ROUND 函数：

**插入函数：ROUND**

四舍五入，返回指定精度的数值

---

参数 1：计算数值

$申请金额

参数 2：保留小数位

2

---

**生成公式**

ROUND($申请金额, 2)

# 四、公式解析和执行引擎

这是整个功能最核心的部分。

我建议不要自己从零编写完整的数学表达式解析器，而是基于成熟的表达式引擎开发。

## 1. 可选技术方案

| 方案            | 技术                | 适用场景           |
| ------------- | ----------------- | -------------- |
| Math.js       | JavaScript        | 前端公式解析和计算      |
| AviatorScript | Java              | Java 后端公式执行    |
| QLExpress     | Java              | 业务表达式和规则执行     |
| 自研 AST        | TypeScript / Java | 需要统一语法和特殊规则的场景 |

推荐参考：

- Math.js：表达式解析文档。
- AviatorScript：可作为 Java 后端引擎的候选。
- QLExpress：可作为业务规则表达式引擎的候选。
- Vue 公式解析器实现参考：有 Vue3 和公式依赖分析示例。

**针对你的 Spring Boot + Vue 架构，我推荐：**

- Vue：CodeMirror 6 + 自定义公式编辑器。
- 前端：TypeScript 负责公式编辑、语法检查、字段提示。
- 后端：Java 表达式引擎负责最终计算。
- 数据：JSON 保存公式定义。
- 函数：统一函数注册中心。

特别注意：不要直接使用 JavaScript `eval()` 执行用户公式，也不要把管理员编写的 JSP、JS 代码块和普通公式混为一谈。

## 2. 公式执行过程

例如：

```
公式：
ROUND(${申请金额} * ${税率}, 2)
```

前端提交公式定义：

```
{
  "formulaCode": "taxAmount",
  "expression": "ROUND(applyAmount * taxRate, 2)",
  "returnType": "NUMBER",
  "fieldDependencies": [
    "applyAmount",
    "taxRate"
  ]
}
```

后端执行时：

```
Map<String, Object> context = new HashMap<>();

context.put("applyAmount", new BigDecimal("1000"));
context.put("taxRate", new BigDecimal("0.13"));

Object result = formulaEngine.execute(
    expression,
    context
);
```

返回：

```
{
  "success": true,
  "result": 130.00,
  "dataType": "NUMBER"
}
```

这里的 `formulaEngine` 是你需要封装的统一执行接口，不应该让业务模块直接调用底层表达式引擎。

## 3. 必须具备的安全机制

公式引擎一定要有以下限制：

1. 禁止任意 Java 类调用。
2. 禁止反射、文件操作和网络访问。
3. 只允许调用注册过的函数。
4. 限制公式长度、计算时间和递归深度。
5. 限制集合数据规模。
6. 对空值、除零、类型转换进行统一处理。
7. 对公式执行进行异常捕获和日志记录。

此外，公式执行应该使用独立、只读的上下文，避免公式通过赋值操作修改原始表单数据。

# 五、公式之间的依赖关系

这是企业级动态表单非常容易遗漏的功能。

例如：

```
A = 数量 * 单价
B = A * 税率
C = A + B
```

当数量发生变化时，系统不能简单地只重新计算 A，而应该自动重新计算 B、C。

因此，需要构建公式依赖关系。

**公式依赖关系示意**

**数量、单价**

原始字段

**A = 数量 × 单价**

**B = A × 税率**

**C = A + B**

(自动计算依赖字段)

建议实现：

- 解析公式，提取字段依赖。
- 建立有向依赖图。
- 使用拓扑排序确定计算顺序。
- 字段值发生变化时，只计算受影响的公式。
- 检查循环依赖，例如 A 依赖 B，B 又依赖 A。
- 检查字段被删除、禁用或修改类型后产生的失效引用。

---

# 六、数据库设计

建议至少拆分为四张核心表。

| 表名                     | 作用   | 主要字段                                 |
| ---------------------- | ---- | ------------------------------------ |
| sys_formula            | 公式定义 | id、code、name、expression、return_type  |
| sys_formula_function   | 函数注册 | code、name、category、parameters        |
| sys_formula_dependency | 公式依赖 | formula_id、field_id、dependency_type  |
| sys_formula_version    | 公式版本 | formula_id、version、expression、status |

其中：

**sys_formula** 建议额外包含：

```
id
formula_code
formula_name
formula_type
expression
expression_json
return_type
business_type
business_id
enabled
version
description
create_time
update_time
```

`formula_type` 可以区分：

- FIELD：字段计算公式
- CONDITION：条件公式
- VALIDATION：校验公式
- VISIBILITY：显隐公式
- WORKFLOW：流程判断公式
- CUSTOM：自定义业务公式

`business_type` 和 `business_id` 用于确定公式所属业务对象，例如某个表单、流程模板或业务模块。

这里建议同时保存 `expression` 和 `expression_json`：

- `expression`：便于阅读、调试和编辑。
- `expression_json`：保存结构化表达式，方便后续迁移、字段引用校验和编辑器重建。

# 七、如何接入 OA / BPM 动态表单

我建议将公式定义器作为公共能力，由动态表单引擎调用。

具体可以这样设计：

| 动态表单功能 | 公式定义器的使用方式       |
| ------ | ---------------- |
| 默认值    | 表单初始化时计算         |
| 自动计算   | 监听依赖字段变化         |
| 动态显隐   | 计算 Boolean 返回值   |
| 动态禁用   | 计算 Boolean 返回值   |
| 动态必填   | 计算 Boolean 返回值   |
| 动态校验   | 计算 Boolean 或校验结果 |
| 字段联动   | 根据公式结果更新目标字段     |
| 明细表计算  | 按行计算及整表汇总        |
| 流程分支   | 后端执行条件公式         |
| 审批人规则  | 根据业务上下文计算人员选择条件  |

例如：

**表单字段配置：报销金额**

---

**计算公式**

SUM(expenseDetails.amount)

**动态显隐**

expenseType == "TRAVEL"

**动态校验**

applyAmount

---

计算结果预览

# 2,000.00

有一点需要特别强调：

**不要让显隐、校验、计算、流程条件分别开发自己的表达式解析逻辑。**

它们应该使用同一套公式解析引擎，只是调用场景和返回类型不同。

# 八、建议增加的高级功能

如果希望后续能支持复杂的企业业务，我建议在基础公式定义器之外，再考虑以下能力。

| 功能       | 价值              | 优先级 |
| -------- | --------------- | --- |
| 字段依赖分析   | 自动计算关联字段        | 高   |
| 公式调试器    | 输入模拟数据，查看结果     | 高   |
| 公式类型检查   | 防止数字、日期、字符串类型错误 | 高   |
| 循环依赖检测   | 避免公式死循环         | 高   |
| 公式版本管理   | 修改后可以回滚         | 高   |
| 自定义函数    | 扩展业务计算能力        | 中   |
| 外部数据查询函数 | 查询 ERP、主数据等业务数据 | 中   |
| 公式执行日志   | 排查历史计算问题        | 中   |
| 公式性能监控   | 识别执行耗时过长的公式     | 中   |

尤其是外部数据查询函数，建议不要允许公式直接发起 HTTP 请求或执行 SQL，而是通过预先注册的业务服务函数获取数据，并设置权限、超时和缓存策略。

# 九、最终推荐的项目模块划分

结合你的 Spring Boot + Vue 单体架构，我建议将公式定义器规划为以下公共模块：

```
common
 └── formula
      ├── controller
      │    ├── FormulaController
      │    └── FormulaFunctionController
      ├── service
      │    ├── FormulaService
      │    ├── FormulaParseService
      │    ├── FormulaExecuteService
      │    ├── FormulaDependencyService
      │    └── FormulaValidateService
      ├── engine
      │    ├── FormulaEngine
      │    ├── FormulaContext
      │    └── FormulaFunctionRegistry
      ├── model
      ├── enums
      └── exception
```

Vue 前端：

```
components
 └── FormulaDesigner
      ├── FormulaEditor.vue
      ├── FieldSelector.vue
      ├── FunctionSelector.vue
      ├── FormulaPreview.vue
      ├── FormulaDebugger.vue
      └── FormulaReference.vue
```

## 十、我的实施建议

建议分三期建设：

1. **第一期：基础公式定义器**
    - 数学、逻辑、字符串、日期函数。
    - 字段引用。
    - 可视化函数插入。
    - 公式语法校验。
    - 公式测试和预览。
2. **第二期：动态表单集成**
    - 字段自动计算。
    - 明细表计算和汇总。
    - 动态显隐、禁用、必填。
    - 动态校验。
    - 字段依赖关系和循环依赖检测。
3. **第三期：业务规则扩展**
    - BPM 流程条件。
    - 自定义函数注册。
    - 外部业务数据引用。
    - 公式版本管理。
    - 公式执行日志和权限控制。

**最重要的架构决策：**

我建议把公式定义器做成整个 OA 系统的公共表达式服务，而不是动态表单的附属组件。

这样以后审批流程、业务规则、自动化任务、数据计算等模块，都可以直接复用它。

另外，前端可以使用 Math.js 的 AST 解析、编译和预览能力，但最终执行结果以 Java 后端为准；需要特别处理前后端运算规则的一致性。Math.js 官方也明确提示任意表达式执行存在安全风险。

---

**下一步最值得做的是确定公式定义器的数据结构和执行协议。** 这会直接影响后续动态表单、BPM、字段联动和规则引擎能否共用一套基础设施。