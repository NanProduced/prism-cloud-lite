# 三方支付中台设计方案

## 一、项目背景

当前项目使用 **Mock 兑换码模式** 实现订阅：
- `auth-service/SubscriptionService` 生成 `PRxxx` 格式验证码
- 用户通过兑换码核销升级订阅
- 现有表结构：`pca_user_subscriptions`、`pca_redeem_codes`、`pca_subscription_events`

现需要接入海外第三方支付平台（Paddle），替换 Mock 兑换码系统，实现真正的支付订阅。

### 核心设计原则

1. **支付中台独立部署** - `payment-service` 作为独立微服务，与业务逻辑分离
2. **订阅数据双写** - payment-service 与 auth-service 各保留一份订阅数据，通过 Webhook 同步
3. **幂等性保证** - 所有 Webhook 事件支持幂等处理
4. **可灰度降级** - 支持 Mock 模式与支付模式并行

---

## 二、支付平台选型

### 推荐：Paddle

| 维度 | Paddle | Stripe |
|------|--------|--------|
| 税务合规 | 自动处理全球VAT/消费税，无需自己报税 | 需自行处理 |
| 出海友好度 | 专为 SaaS 出海设计，支持 200+ 国家 | 配置复杂 |
| Merchant of Record | ✅ 是（平台承担法律责任） | ❌ 否 |
| 定价 | 5% + $0.5/笔 | 2.9% + $0.3 |
| 小程序适配 | Paddle.js 可嵌入 H5 | 需 H5 包装 |

**结论**：出海 SaaS 小程序选 **Paddle**，税务合规自动化是核心差异点。

---

## 三、支付中台架构

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────┐
│                   现有业务（core-service）            │
│         订阅配额校验 / 用户管理 / 内容服务              │
└─────────────────────┬───────────────────────────────┘
                      │ Feign 调用
                      ▼
┌─────────────────────────────────────────────────────┐
│              支付中台（payment-service）新建          │
│                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │  订单管理    │  │  支付网关    │  │  Webhook    │  │
│  │  Order      │  │  Adapter    │  │  Handler    │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
│                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │  订阅管理    │  │  退款/风控   │  │  审计日志   │  │
│  │  Subscription│ │  Refund     │  │  EventLog   │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
└─────────────────────┬───────────────────────────────┘
                      │ Webhook 异步同步
                      ▼
┌─────────────────────────────────────────────────────┐
│                   第三方支付平台（Paddle）            │
└─────────────────────────────────────────────────────┘
                      │
                      │ Feign 同步调用
                      ▼
┌─────────────────────────────────────────────────────┐
│                 auth-service（订阅本地副本）           │
│            保留订阅数据，作为最终一致性备份             │
└─────────────────────────────────────────────────────┘
```

### 3.2 目录结构

```
prism-cloud-lite/
├── payment-service/                    ← 新建
│   └── src/main/java/nan/produced/prism/payment/
│       ├── domain/
│       │   ├── model/                 # PaymentOrder, PaymentSubscription, PaymentEvent
│       │   └── repository/            # JPA Repository
│       ├── infrastructure/
│       │   ├── paddle/                # Paddle SDK 适配
│       │   │   ├── PaddleConfig.java
│       │   │   ├── PaddleClient.java
│       │   │   └── dto/
│       │   └── persistence/           # JPA Entity 映射
│       ├── application/
│       │   └── service/
│       │       ├── PaymentOrderService.java
│       │       ├── SubscriptionSyncService.java
│       │       └── WebhookProcessingService.java
│       └── interface/
│           ├── rest/
│           │   ├── PaymentController.java
│           │   └── WebhookController.java
│           └── feign/                  # Feign Client（调用 auth-service）
│
├── auth-service/                      ← 改造
│   └── src/main/java/nan/produced/prism/auth/
│       └── internal/                  # 新增内部同步 API
│           └── SubscriptionSyncController.java
│
└── core-service/                     ← 改造
    └── src/main/java/nan/produced/prism/core/user/
        └── controller/
            └── PaymentEntryController.java  # 支付入口（代理到 payment-service）
```

---

## 四、数据库模型

### 4.1 payment-service 表结构

```sql
-- 支付订单表
CREATE TABLE pca_payment_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    order_no VARCHAR(64) UNIQUE NOT NULL,      -- 本地订单号
    external_order_no VARCHAR(128),             -- Paddle Order ID
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(32) NOT NULL,               -- PENDING/PAID/FAILED/REFUNDED/CANCELED
    product_type VARCHAR(32) NOT NULL,           -- SUBSCRIPTION/ONE_TIME
    price_id VARCHAR(64),                       -- Paddle Price ID
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 支付订阅表（同步 Paddle 订阅状态）
CREATE TABLE pca_payment_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    external_subscription_id VARCHAR(128) UNIQUE NOT NULL,  -- Paddle Subscription ID
    tier VARCHAR(16) NOT NULL,                  -- PRO
    status VARCHAR(32) NOT NULL,                -- ACTIVE/PAST_DUE/CANCELED/PAUSED/TRIALING
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Webhook 事件日志（幂等性保证）
CREATE TABLE pca_payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(128) UNIQUE NOT NULL,      -- Paddle Event ID
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

-- 支付审计日志
CREATE TABLE pca_payment_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,            -- ORDER_CREATED/ORDER_PAID/SUBSCRIPTION_SYNCED/REFUND
    success BOOLEAN NOT NULL,
    order_id UUID,
    subscription_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL
);
```

---

## 五、核心 API 设计

### 5.1 payment-service 对外 API

```yaml
# 支付订单
POST   /api/v1/payments/create-order       # 创建支付订单，返回 Paddle Checkout URL
GET    /api/v1/payments/{orderId}         # 查询订单状态
POST   /api/v1/payments/cancel/{orderId}  # 取消未支付订单

# 订阅管理
GET    /api/v1/subscriptions/current       # 获取当前订阅（从 payment-service）
POST   /api/v1/subscriptions/cancel        # 取消订阅
POST   /api/v1/subscriptions/pause         # 暂停订阅
POST   /api/v1/subscriptions/resume        # 恢复订阅

# Webhook
POST   /api/v1/webhooks/paddle             # Paddle Webhook 接收端点
```

### 5.2 内部同步 API（auth-service 提供）

```yaml
POST   /auth/internal/subscription/sync-from-payment  # payment-service 同步订阅状态
```

---

## 六、Webhook 事件处理

### 6.1 关键事件

| 事件 | 处理逻辑 |
|------|---------|
| `subscription.created` | 记录订阅，等待激活 |
| `subscription.activated` | **同步到 auth-service**，激活用户订阅 |
| `subscription.canceled` | **同步到 auth-service**，降级为 FREE |
| `subscription.past_due` | **同步到 auth-service**，通知用户 |
| `subscription.paused` | **同步到 auth-service** |
| `subscription.resumed` | **同步到 auth-service** |
| `transaction.completed` | 更新订单状态为 PAID |
| `transaction.failed` | 更新订单状态为 FAILED |

### 6.2 幂等处理

```java
@Transactional
public void processEvent(String eventId, String eventType, JsonNode data) {
    // 1. 幂等检查：eventId 是否已处理
    if (webhookEventRepository.existsByEventId(eventId)) {
        log.warn("Duplicate webhook event: {}", eventId);
        return;
    }

    // 2. 记录事件
    WebhookEvent event = WebhookEvent.builder()
        .eventId(eventId)
        .eventType(eventType)
        .payload(data)
        .processed(false)
        .build();
    webhookEventRepository.save(event);

    // 3. 业务处理
    switch (eventType) {
        case "subscription.activated" -> handleSubscriptionActivated(data);
        case "subscription.canceled" -> handleSubscriptionCanceled(data);
        case "transaction.completed" -> handleTransactionCompleted(data);
    }

    // 4. 标记已处理
    webhookEventRepository.markProcessed(eventId);
}
```

---

## 七、与现有系统集成

### 7.1 订阅状态同步流程

```
1. 用户在 Paddle 完成支付
2. Paddle 发送 Webhook → payment-service
3. payment-service 处理事件，同步到 auth-service
4. auth-service 更新本地订阅副本
5. core-service 读取 auth-service 获取订阅状态
```

### 7.2 同步请求格式

```java
// Feign Client 调用 auth-service
@PostMapping("/auth/internal/subscription/sync-from-payment")
void syncSubscriptionFromPayment(
    @RequestBody SubscriptionSyncRequest request
);

// 请求体
public class SubscriptionSyncRequest {
    private String userId;
    private String externalSubscriptionId;  // Paddle Subscription ID
    private String tier;                   // PRO
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;                 // ACTIVE/CANCELED/PAUSED
}
```

---

## 八、配置项

### payment-service 配置

```yaml
# application.yml
paddle:
  api-key: ${PADDLE_API_KEY}
  client-id: ${PADDLE_CLIENT_ID}
  webhook-secret: ${PADDLE_WEBHOOK_SECRET}
  environment: sandbox  # sandbox / production

payment-service:
  callback-base-url: ${API_CALLBACK_BASE_URL}
```

---

## 九、订阅等级与定价

| 等级 | 月付 | 年付 | Paddle Price ID |
|------|------|------|----------------|
| PRO Monthly | $9.99 | - | `pdl_prm_xxx` |
| PRO Yearly | - | $99.99 | `pdl_pry_xxx` |

> Price ID 需在 Paddle Dashboard 创建后配置到 `application.yml`

---

## 十、实施计划

| 阶段 | 内容 |
|------|------|
| **Phase 1** | payment-service 基础框架搭建（模块、数据库、配置） |
| **Phase 2** | Paddle SDK 集成（创建 Checkout、调用 API） |
| **Phase 3** | Webhook 处理（事件解析、幂等实现、订阅同步） |
| **Phase 4** | 核心服务改造（core-service 支付入口、auth-service 同步 API） |
| **Phase 5** | 降级方案（Mock 兑换码与支付并行） |
| **Phase 6** | 测试与文档 |
