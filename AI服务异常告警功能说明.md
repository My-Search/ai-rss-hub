# AI服务异常告警功能

## 功能概述
当RSS抓取过程中检测到AI服务持续不可用时，系统会自动向用户发送邮件告警，避免用户不知道服务异常。

## 实现细节

### 1. 数据库变更

#### ai_configs表
在 `ai_configs` 表中添加了两个新字段：
- `service_status`: AI服务状态（0=正常，1=异常已告警）
- `last_status_change_at`: 最后一次状态变更时间

#### rss_items表
在 `rss_items` 表中添加了一个新字段：
- `needs_retry`: 是否需要重试（0=不需要，1=需要重试）
  - 当AI服务不可用导致筛选失败时，该字段设置为1
  - 当重新处理成功或确认不需要重试时，该字段设置为0

### 2. 异常判断逻辑
系统通过以下方式判断AI服务是否不可用：
- 分析AI返回的 `aiReason` 字段，检查是否包含：
  - "ai服务不可用"
  - "处理失败"
  - "服务异常"
  - "连接失败"
  - "超时"
  
- 分析AI的原始响应 `aiRawResponse`，检查是否包含：
  - "connection"
  - "timeout"
  - "http"
  - "error"
  - "failed"
  - "exception"

当判断为AI服务不可用时，会将该RSS条目的 `needs_retry` 字段设置为1。

### 3. 告警触发条件
只有同时满足以下所有条件时才会发送告警：
1. 本次抓取的所有RSS条目都未通过筛选（`passedCount == 0`）
2. 所有未通过的条目都是因为AI服务不可用（`aiServiceFailureCount == totalCount`）
3. 用户已配置邮箱
4. 管理员已配置邮箱（系统配置中的 `email.username`）
5. AI服务状态当前为正常（避免重复告警）

**注意**：采用严格的100%失败判断，确保只有在AI服务完全不可用时才发送告警，避免误报。

### 4. 状态恢复
当检测到有任何RSS条目通过AI筛选时，系统会：
1. 查询该用户所有 `needs_retry = 1` 的RSS条目
2. 使用恢复后的AI服务重新批量处理这些条目
3. 更新条目的筛选结果和原因
4. 如果重新处理后仍然失败且仍是AI服务问题，保持 `needs_retry = 1`
5. 如果重新处理成功或确认不是AI服务问题，设置 `needs_retry = 0`
6. 记录新的筛选日志（标注为"故障恢复重新处理"）
7. 自动将AI服务状态从"异常"恢复为"正常"
8. 向用户发送恢复通知邮件（包含故障时长和重新处理的条目数）
9. 下次再出现异常时会重新发送告警

### 5. 邮件内容

#### 异常告警邮件
告警邮件包含以下信息：
- 受影响的RSS源名称
- AI配置信息（模型、API地址）
- 检测时间
- 排查建议（检查API密钥、服务地址、账户余额、网络连接等）

#### 恢复通知邮件
恢复通知邮件包含以下信息：
- 恢复验证的RSS源名称
- AI配置信息（模型、API地址）
- 恢复时间
- 服务状态时间线（异常开始时间、恢复时间、故障时长）
- 重新处理的条目数量

## 使用说明

### 前置条件
1. 用户需要在个人设置中配置邮箱地址
2. 管理员需要在系统配置中配置SMTP邮件服务

### 告警流程
1. RSS定时任务抓取内容
2. AI服务筛选失败
3. 系统检测到所有条目都因AI服务不可用而失败
4. 发送异常告警邮件给用户
5. 更新AI配置状态为"异常"
6. 后续抓取不再重复发送告警
7. 当AI服务恢复正常时：
   - 查询故障期间受影响的所有RSS条目
   - 使用恢复后的AI服务重新批量处理这些条目
   - 更新条目的筛选结果（可能有些条目会从"未通过"变为"通过"）
   - 发送恢复通知邮件给用户（包含故障时长和重新处理的条目数）
   - 自动更新状态为"正常"
   - 下次异常时会再次告警

## 数据库更新
执行 `update.sql` 中的SQL语句来添加新字段：
```sql
-- AI配置表
ALTER TABLE ai_configs ADD COLUMN service_status INTEGER DEFAULT 0;
ALTER TABLE ai_configs ADD COLUMN last_status_change_at TEXT;
UPDATE ai_configs SET service_status = 0 WHERE service_status IS NULL;

-- RSS条目表
ALTER TABLE rss_items ADD COLUMN needs_retry INTEGER DEFAULT 0;
UPDATE rss_items SET needs_retry = 0 WHERE needs_retry IS NULL;
```

## 相关文件
- `src/main/java/com/rssai/model/AiConfig.java` - 添加状态字段
- `src/main/java/com/rssai/model/RssItem.java` - 添加needs_retry字段
- `src/main/java/com/rssai/mapper/AiConfigMapper.java` - 添加更新状态方法
- `src/main/java/com/rssai/mapper/RssItemMapper.java` - 添加查询需要重试条目的方法
- `src/main/java/com/rssai/service/RssFetchService.java` - 实现异常检测、告警、恢复通知和重新处理逻辑
- `src/main/java/com/rssai/service/EmailService.java` - 添加发送告警和恢复通知邮件方法
- `src/main/resources/templates/email-ai-service-alert.html` - 异常告警邮件模板
- `src/main/resources/templates/email-ai-service-recovery.html` - 恢复通知邮件模板
- `src/main/java/com/rssai/config/DatabaseInitializer.java` - 更新建表语句
- `update.sql` - 数据库增量更新脚本

## 技术细节

### 重新处理逻辑
1. **查询条件**：查询 `needs_retry = 1` 的所有RSS条目（不再依赖时间范围和文本匹配）
2. **批量处理**：使用批量AI筛选接口重新处理所有需要重试的条目
3. **结果更新**：
   - 更新条目的 `ai_filtered` 和 `ai_reason` 字段
   - 如果重新处理后仍然是AI服务问题，保持 `needs_retry = 1`
   - 如果处理成功或不是AI服务问题，设置 `needs_retry = 0`
4. **日志记录**：为每个重新处理的条目创建新的筛选日志，标注为"故障恢复重新处理"
5. **统计信息**：记录重新处理的总数、更新数和新通过数，并在恢复通知邮件中展示

### 优势
- **精确标记**：使用专门的 `needs_retry` 字段，不依赖文本匹配，更可靠
- **跨时间范围**：不受时间限制，只要标记为需要重试就会被处理
- **状态管理**：清晰的状态转换，易于追踪和调试
- **性能优化**：使用索引字段查询，比文本匹配更高效

### 性能考虑
- 使用批量AI筛选接口，提高处理效率
- 只更新状态发生变化的条目，减少数据库写入
- 异步发送邮件，不阻塞主流程
- 可以为 `needs_retry` 字段添加索引以提高查询性能
