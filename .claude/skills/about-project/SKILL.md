---
name: 了解AI RSS Hub项目
description: 了解AI RSS Hub项目的架构、技术栈和开发规范
---
# AI RSS Hub 项目规范

## 项目概述
AI RSS Hub 是一个基于 Spring Boot 的 RSS 智能筛选和摘要系统，使用 AI 模型对 RSS 内容进行筛选和摘要生成，并通过邮件推送。

## 技术栈

### 后端
- **Spring Boot 2.7.18** - 核心框架
- **Spring Security** - 认证授权
- **Thymeleaf** - 模板引擎
- **SQLite** - 数据库
- **Rome 1.18.0** - RSS 解析
- **Caffeine** - 本地缓存
- **OkHttp3** - HTTP 客户端
- **Lombok** - 代码简化

### 前端
- **Thymeleaf** - 服务端渲染
- **jQuery 3.6.0** - JavaScript 库
- **CSS变量** - 深色主题支持

### 部署
- **Docker** - 多阶段构建
- **Maven** - 构建工具

## 项目结构

```
src/main/java/com/rssai/
├── config/           # 配置类（安全、数据库、邮件、缓存等）
├── constant/         # 常量定义
├── controller/       # MVC控制器层
├── dto/              # 数据传输对象
├── exception/        # 异常处理器
├── mapper/           # 数据访问层（基于JdbcTemplate）
├── model/            # 实体模型
├── security/         # 安全相关（Remember Me等）
├── service/          # 业务逻辑层
│   └── ai/          # AI服务子模块
└── util/             # 工具类

src/main/resources/
├── templates/       # Thymeleaf模板
│   └── fragments/   # 页面片段
├── static/
│   ├── css/        # 样式文件
│   └── js/         # JavaScript文件
├── application*.yml # 配置文件
├── update.sql      # 数据库迁移脚本
└── logback-spring.xml # 日志配置
```

## 代码规范

### Java 命名规范
- **类名**: PascalCase (如 `UserService`, `AiService`)
- **方法名**: camelCase (如 `findByUsername`, `sendDailyDigest`)
- **常量**: UPPER_SNAKE_CASE (如 `MAX_RETRY_ATTEMPTS`, `DEFAULT_BATCH_SIZE`)
- **包名**: 全小写，点分隔 (如 `com.rssai.service.ai`)
- **字段**: camelCase (如 `userId`, `baseUrl`)

### Java 编码规范

1. **依赖注入**: 使用构造器注入
```java
@Service
public class UserService {
    private final UserMapper userMapper;

    @Autowired
    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }
}
```

2. **常量类**: 防止实例化
```java
public class Constants {
    private Constants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    public static final int MAX_RETRY_ATTEMPTS = 3;
}
```

3. **实体类**: 使用 Lombok @Data
```java
@Data
public class User {
    private Long id;
    private String username;
    private LocalDateTime createdAt;
}
```

4. **使用 final 字段**: 保证不可变性
```java
@Service
public class SomeService {
    private final Dependency1 dep1;
    private final Dependency2 dep2;
}
```

### Thymeleaf 模板规范

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <script>
        // 深色主题初始化
        (function() {
            const savedTheme = localStorage.getItem('theme');
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
                document.documentElement.setAttribute('data-theme', 'dark');
            }
        })();
    </script>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
    <div th:replace="fragments/header :: header"></div>
    <div th:if="${condition}">
        <span th:text="${value}">Placeholder</span>
    </div>
    <div th:each="item : ${items}">
        <span th:text="${item.name}"></span>
    </div>
</body>
</html>
```

### CSS 规范

```css
/* 主题变量 */
:root {
    color-scheme: light dark;
}

/* 暗色主题覆盖 */
html[data-theme="dark"] {
    background-color: #272727;
    color: #f1f5f9;
}

/* 响应式设计 */
@media (max-width: 480px) { }

/* 减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
    * {
        animation-duration: 0.01ms !important;
    }
}
```

## 架构模式

### Controller 层
- 使用 `@Controller` + `@RequestMapping` 定义路由
- 返回模板名或 JSON
- 注入 `Authentication` 获取当前用户
- 使用 `Model` 传递数据到视图

```java
@Controller
@RequestMapping("/ai-config")
public class AiConfigController {
    @GetMapping
    public String configPage(Authentication auth, Model model) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("config", aiConfigMapper.findByUserId(user.getId()));
        return "ai-config";
    }
}
```

### Service 层
- 单一职责原则
- 使用 `@Service` 注解
- 依赖其他 Service 和 Mapper
- 使用 `@Transactional` 保证事务性
- 使用 `@Async` 异步执行

```java
@Service
public class UserService {
    @Transactional(rollbackFor = Exception.class)
    public User register(String username, String password, String email) {
        // 业务逻辑
    }
}
```

### Mapper 层
- 使用 `@Repository` 注解
- 注入 `JdbcTemplate`
- 定义 `RowMapper` 用于结果映射
- 支持 Caffeine 缓存

```java
@Repository
public class UserMapper {
    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, User> userCache;

    public User findByUsername(String username) {
        return userCache.get(username, key ->
            jdbcTemplate.queryForObject(
                "SELECT * FROM user WHERE username = ?",
                userRowMapper(), key
            )
        );
    }
}
```

## 关键功能模块

### 用户管理
- 注册: 支持邮箱验证码、域名限制、第一个用户自动为管理员
- 登录: Spring Security 认证，支持 Remember Me
- 权限: 基于角色 (`isAdmin`) 的简单权限控制
- 用户状态: 支持封禁 (`isBanned`)、强制修改密码 (`forcePasswordChange`)

### RSS 源管理
- 添加、编辑、删除、启用/禁用
- 按用户分组并发抓取
- 可配置线程池参数
- 30 天去重窗口

### AI 配置
- 支持 OpenAI 兼容 API（OpenAI、DeepSeek 等）
- 自动识别推理模型（如 deepseek-r1）
- 批量筛选，默认每批 10 条
- 服务异常告警

### 邮件调度
- 每日摘要、关键词匹配、验证码、AI 服务告警
- 定时任务调度
- 异步发送

### 安全配置
- BCrypt 密码加密
- Remember Me 持久化（14 天有效期）
- CSRF 保护
- AES-128 加密敏感配置

## 数据库规范

### 数据库配置
- 数据库: SQLite (文件: `data/rss.db`)
- 迁移: 基于 `update.sql` 的版本化迁移系统
- 初始化: `DatabaseInitializer` 实现 `CommandLineRunner`

### 迁移脚本格式
```sql
-- Migration X: 描述
CREATE TABLE IF NOT EXISTS example (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

UPDATE schema_version SET version = X;
```

## 配置文件规范

### YAML 配置
```yaml
spring:
  application:
    name: ai-rss-hub
  datasource:
    url: jdbc:sqlite:data/rss.db?date_string_format=yyyy-MM-dd HH:mm:ss
  thymeleaf:
    cache: false
    prefix: classpath:/templates/

rss:
  timezone: GMT+8
  fetch:
    thread-pool:
      core-size: 5
      max-size: 10

security:
  remember-me-key: ${REMEMBER_ME_KEY:default}
  encryption-key: ${ENCRYPTION_KEY:default}
```

- 使用环境变量和默认值组合
- 按功能模块分组配置

## 注释规范

### 类注释
```java
/**
 * AI服务
 * 负责RSS条目的AI筛选和摘要生成
 * 重构后职责更清晰：
 * - AiClient: 负责HTTP通信
 * - AiResponseParser: 负责响应解析
 * - AiService: 负责业务逻辑编排
 */
@Service
public class AiService {
    // ...
}
```

### 方法注释
```java
/**
 * 筛选单个RSS条目并返回原因（带重试）
 */
public String filterRssItemWithReason(AiConfig config, String title, String description) {
    // ...
}
```

## 总结

### 架构特点
1. 分层清晰: Controller → Service → Mapper，职责明确
2. 配置驱动: 大量使用 application.yml 配置化
3. 异步处理: 邮件发送、RSS 抓取使用异步
4. 缓存优化: Caffeine 缓存用户数据、HTTP 客户端
5. 重试机制: AI 请求使用重试工具
6. 数据库迁移: 版本化迁移系统，自动执行增量 SQL
7. 安全加固: 敏感配置加密、Remember Me 持久化
8. 响应式设计: 深色主题、移动端适配
9. 日志完善: 详细的日志记录，支持内存日志收集
10. Docker 友好: 多阶段构建，数据卷挂载

### 开发建议
- 遵循分层架构，保持单一职责
- 使用构造器注入和 final 字段
- 充分利用 Caffeine 缓存
- 新增数据库变更需更新 update.sql
- 保持深色主题兼容性
- 异步操作使用 @Async 注解
- 敏感操作添加详细日志