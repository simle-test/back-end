# 公务员行测刷题助手 - 后端服务

## 📖 项目简介

本系统是为**公务员行测（行政职业能力测验）刷题小程序**提供服务的后端 API 系统。支持六大考试模块的题目管理、用户答题追踪、基于 SM-2 算法的间隔重复复习、微信小程序登录、每日/每周学习数据统计等功能。

## ✨ 核心功能

### 题库管理
- **六大题型模块**：言语理解与表达、数量关系、判断推理、资料分析、常识判断、政治理论
- **材料分析题**：支持包含材料的复合题目（材料 + 多道子题）
- **题目搜索**：按关键词搜索题目（支持标题和内容模糊匹配）
- **随机抽题**：随机抽取指定数量的题目进行练习
- **历年真题**：按考试来源（如"公务员行测真题"）浏览试卷

### 用户系统
- **微信小程序登录**：通过微信 `jscode2session` 接口实现免注册登录
- **用户名/密码注册**：支持传统注册方式
- **头像上传**：支持用户自定义头像
- **个人信息管理**：修改用户名、手机号等

### 学习追踪
- **答题记录**：记录每次答题的对错、选项、时间
- **SM-2 间隔重复算法**：智能安排错题复习计划
  - 答对 → 延长复习间隔，提升掌握度
  - 答错 → 缩短间隔，重新加入错题本
  - 连续答对 3 次 → 标记为"已掌握"
- **错题本**：查看所有错题，支持删除已掌握的错题
- **收藏夹**：收藏重点题目以便反复练习

### 数据统计
- **每日统计**：今日做题数、正确数、正确率
- **每周图表**：七天做题数量/正确/错误趋势图数据
- **模块分析**：每个模块的正确率和完成进度
- **每日重置**：午夜自动清零每日数据并归档到周统计
- **登录天数**：记录累计登录天数

### 其他
- **AI 辅助**：向前端提供 Coze AI 接口密钥，支持 AI 题目解析
- **文件上传**：支持图片等文件上传（如题目配图）

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| **核心框架** | Spring Boot 3.2.10 |
| **开发语言** | Java 17 |
| **构建工具** | Maven |
| **数据库** | PostgreSQL |
| **ORM** | MyBatis 3.0.3 + Spring JdbcTemplate |
| **JSON 处理** | Jackson (ObjectMapper) |
| **定时任务** | Spring `@Scheduled` |
| **微信集成** | RestTemplate 调用微信 API |

## 🌍 运行环境

- **JDK**：17+
- **Maven**：3.6+
- **数据库**：PostgreSQL
- **操作系统**：Linux (Ubuntu) / Windows / macOS
- **端口**：8080

> ⚠️ 项目配置的数据库地址为远程服务器（`124.222.105.118:5432/test`），如需本地开发请修改 `application.properties` 中的数据库连接信息。

## 📁 项目结构

```
back-end/
├── pom.xml                                    # Maven 构建配置
├── question_img/                              # 静态题目图片资源
├── uploads/                                   # 用户上传文件目录
├── src/
│   └── main/
│       ├── java/org/example/
│       │   ├── Main.java                      # 应用入口（@MapperScan + @EnableScheduling）
│       │   ├── common/
│       │   │   └── Result.java                # 统一 API 响应封装 {code, message, data}
│       │   ├── config/
│       │   │   ├── CorsConfig.java            # 跨域配置（开发环境全开放）
│       │   │   ├── WebConfig.java             # 内容协商 + 静态资源映射
│       │   │   ├── RestTemplateConfig.java    # RestTemplate Bean
│       │   │   └── AcceptHeaderFilter.java    # 强制 Accept: application/json
│       │   ├── controller/
│       │   │   ├── QuestionController.java    # 核心控制器（题目CRUD、答题、统计、登录等）
│       │   │   ├── FileController.java        # 文件上传/下载
│       │   │   ├── UploadController.java      # 头像上传
│       │   │   ├── MaterialController.java    # 材料文件读取
│       │   │   └── AiKeyController.java       # AI Key 接口
│       │   ├── service/
│       │   │   └── QuestionService.java       # 业务逻辑层
│       │   ├── dao/
│       │   │   └── UserDAO.java               # 用户数据访问层（JdbcTemplate）
│       │   ├── mapper/
│       │   │   ├── QuestionMapper.java        # 题目 MyBatis 接口
│       │   │   └── MaterialProblemMapper.java # 材料题 MyBatis 接口
│       │   ├── entity/
│       │   │   ├── User.java                  # 用户实体
│       │   │   ├── UserData.java              # 用户学习数据实体
│       │   │   └── WechatSession.java         # 微信会话响应实体
│       │   ├── domain/
│       │   │   ├── Question.java              # 题目核心实体（含 SRS 字段）
│       │   │   ├── QuestionContent.java       # 题目内容（题目、文本、图片、选项）
│       │   │   └── MaterialProblem.java       # 材料子题实体
│       │   ├── handler/
│       │   │   └── JsonTypeHandler.java       # PostgreSQL JSON <-> Java 对象类型转换
│       │   └── scheduler/
│       │       └── DailyResetScheduler.java   # 每日数据重置定时任务
│       └── resources/
│           ├── application.properties         # 主配置文件
│           ├── text.json                      # 种子题库数据
│           └── mapper/
│               ├── QuestionMapper.xml          # 题目 SQL 映射
│               └── MaterialProblemMapper.xml   # 材料题 SQL 映射
└── target/
    └── application-local.properties           # 外部覆盖配置（coze.api.key 等，gitignore）
```

## 🔌 API 接口文档

### 统一响应格式

```json
{
  "code": "200",
  "message": "success",
  "data": {}
}
```

### 题目接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/logical` | 获取所有判断推理题目 |
| `GET` | `/api/common` | 获取所有常识判断题目 |
| `GET` | `/api/speech` | 获取所有言语理解题目 |
| `GET` | `/api/quantitative` | 获取所有数量关系题目 |
| `GET` | `/api/data` | 获取所有资料分析题目 |
| `GET` | `/api/all` | 获取所有题目（按 category_id 分组） |
| `GET` | `/api/select?keyword=&limit=` | 搜索题目 |
| `GET` | `/api/random?count=` | 随机获取指定数量题目（含材料子题） |
| `POST` | `/api/addQuestion` | 添加题目 |
| `DELETE` | `/api/delete` | 删除题目 |
| `PATCH` | `/api/update` | 更新题目 |

### 答题与学习追踪

| 方法 | 路径 | 说明 |
|------|------|------|
| `PUT` | `/api/submit` | 提交答案（触发 SM-2 算法 + 更新每日统计） |
| `POST` | `/api/home/upDataDetailedProblems` | 记录单次答题 + 触发 SM-2 SRS |
| `GET` | `/api/wrong?dataId=` | 获取错题列表（含 SRS 状态标签） |
| `GET` | `/api/wrong/ids?dataId=` | 获取错题 ID 列表 |
| `DELETE` | `/api/wrong/delete` | 从错题本中删除指定题目 |
| `POST` | `/api/home/collect` | 收藏题目 |
| `GET` | `/api/home/getCollect?userId=` | 获取收藏列表 |
| `DELETE` | `/api/home/collectDelete` | 取消收藏 |

### 统计分析

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/analysis/moduleAccuracy?dataId=` | 各模块正确率 |
| `GET` | `/api/analysis/moduleProgress?dataId=` | 各模块完成进度 |
| `POST` | `/api/home/TodayNumber` | 更新今日做题数 |
| `POST` | `/api/home/TodayCorrect` | 更新今日正确数 |
| `GET` | `/api/home/TodayCorrectRate?userId=` | 获取今日正确率 |
| `POST` | `/api/home/WeekData` | 更新周数据 |
| `POST` | `/api/home/updataData` | 更新每日数据（做题数 + 正确数 + 正确率） |
| `POST` | `/api/home/loginDays` | 记录登录天数 |

### 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/login` | 微信小程序登录（jscode2session） |
| `POST` | `/api/register` | 用户名密码注册 |
| `GET` | `/api/gainUserData?dataId=` | 获取用户完整学习数据 |
| `POST` | `/api/updateUserData` | 更新用户学习数据 |
| `POST` | `/api/upUserData` | 更新用户基本信息 |
| `POST` | `/api/delUserData?data_id=` | 清除所有用户数据 |

### 试卷与详情

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/exam/papers` | 获取试卷列表（按来源分组） |
| `GET` | `/api/exam/problems?source=` | 获取指定试卷的所有题目 |
| `GET` | `/api/problem/gainProblemDetail?user_id=` | 获取最近 3 次答题详情 |
| `GET` | `/api/problems_detail?user_id=` | 获取所有答题历史 |

### 其他接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/ai-key` | 获取 Coze AI API Key |
| `POST` | `/api/uploadAvatar` | 上传头像 |
| `POST` | `/file/upload` | 上传文件 |
| `GET` | `/file/{fileName}` | 下载文件 |
| `GET` | `/material/{fileName}` | 读取材料文本文件 |

## 🗄 数据库表结构

| 表名 | 说明 |
|------|------|
| `users` | 用户账号表（用户名、密码、微信 openid、头像、手机号） |
| `user_data` | 用户学习数据表（错题列表、收藏列表、每日/每周统计、SRS 状态） |
| `questions_total` | 统一题库视图 |
| `language_understanding` | 言语理解题目表 |
| `quantity_relation` | 数量关系题目表 |
| `logical_judgment` | 判断推理题目表 |
| `material_analysis` | 资料分析题目表 |
| `common_sense_judgment` | 常识判断题目表 |
| `political_theory` | 政治理论题目表 |
| `material_problems` | 材料分析子题表 |
| `problems_detail` | 答题明细记录表 |

## 🧠 SM-2 间隔重复算法

系统集成了 SuperMemo SM-2 算法，实现智能错题复习：

- **EF（难度系数）**：默认 2.5，最低 1.3，答对 +0.1，答错 -0.2
- **复习间隔**：1 天 → 3 天 → 间隔 × EF
- **掌握判定**：连续答对 3 次 → 标记"已掌握"，移出错题本
- **早鸟复习**：提前答对只记录日期，不推进 EF 和间隔

SRS 状态标签：
- 🔴 `待复习` — 还未到复习时间
- 🟡 `正确1/3` — 已连续答对 1 次
- 🟠 `正确2/3` — 已连续答对 2 次
- 🟢 `已掌握` — 已连续答对 3 次，移出错题本

## 🚀 快速开始

### 1. 环境准备

确保已安装：
- JDK 17+
- Maven 3.6+
- PostgreSQL 数据库

### 2. 配置数据库

修改 `src/main/resources/application.properties` 中的数据库连接：

```properties
spring.datasource.url=jdbc:postgresql://your-host:5432/your-database
spring.datasource.username=your-username
spring.datasource.password=your-password
```

### 3. 配置微信小程序

```properties
wechat.appid=your-wechat-appid
wechat.secret=your-wechat-secret
```

### 4. 配置 AI Key（可选）

创建 `application-local.properties` 文件：

```properties
coze.api.key=your-coze-api-key
```

### 5. 初始化数据库

运行数据库迁移脚本（如项目根目录下的 `migration_add_question_status.sql`）：

```bash
psql -h your-host -U your-username -d your-database -f migration_add_question_status.sql
```

### 6. 启动服务

```bash
# 开发环境
mvn spring-boot:run

# 生产环境打包
mvn clean package -DskipTests
java -jar target/back_end.jar --spring.config.location=target/application-local.properties
```

服务启动后访问：`http://localhost:8080`

### 7. 验证

```bash
# 测试获取所有题目
curl http://localhost:8080/api/all

# 测试搜索题目
curl "http://localhost:8080/api/select?keyword=逻辑&limit=10"
```

## ⚙️ 配置说明

### 主配置 (`application.properties`)

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | `8080` |
| `server.address` | 绑定地址 | `0.0.0.0` |
| `spring.datasource.url` | 数据库连接 | PostgreSQL |
| `spring.servlet.multipart.max-file-size` | 上传文件大小限制 | `10MB` |
| `wechat.appid` | 微信小程序 AppID | `wxe5f28ef6dfb8330d` |
| `wechat.secret` | 微信小程序 Secret | （已脱敏） |
| `coze.api.key` | Coze AI 密钥 | 外部配置注入 |

### 外部覆盖配置 (`application-local.properties`)

部署时将敏感配置外置，通过 `--spring.config.location` 参数加载，避免将密钥提交到代码仓库。

## 📄 许可证

本项目仅用于学习目的。

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
