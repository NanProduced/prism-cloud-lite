/**
 * 公共基础设施模块
 *
 * 职责：
 * - 提供系统级的共享工具和异常处理
 * - 定义公共的响应结构和数据格式
 * - 提供跨模块的消息传输接口
 * - 定义所有业务模块都需要的公开 API
 *
 * 模块结构：
 * ├─ messaging：消息传输层
 * │  └─ DeviceEventMessage：跨模块的设备事件消息（公开 API）
 * ├─ api：公开 API 层（标记公开类型）
 * ├─ exception：统一异常处理
 * ├─ response：统一响应格式
 * └─ util：工具类集合
 *
 * 公开类型：
 * - nan.produced.prism.core.common.messaging.DeviceEventMessage
 *
 * 依赖关系：
 * 基础设施模块，不依赖任何业务模块，被所有业务模块（device, auth, etc.）依赖
 *
 * 访问规则：
 * - device 模块可以访问 common 模块中的所有公开类型
 * - 其他业务模块也可以依赖 common 模块的类型
 */
@org.springframework.modulith.ApplicationModule(displayName = "Common Infrastructure")
package nan.produced.prism.core.common;