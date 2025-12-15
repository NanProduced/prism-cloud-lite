/**
 * 公共 API 包 - 跨模块的公开契约
 *
 * 属于: common 模块
 *
 * 职责：
 * - 定义和暴露跨模块的消息和数据契约
 * - 提供所有业务模块都需要依赖的公开类型
 *
 * 公开类型：
 * - DeviceEventMessage：设备事件消息，从 common.messaging 重新暴露
 *
 * 定位：
 * 这是 common 模块对外的公开 API 层，所有其他模块可以安全地依赖此包中的类型
 * 不是独立模块，而是 common 模块的"公开面"
 *
 * 使用方式：
 * device 模块应该导入：nan.produced.prism.core.common.api.DeviceEventMessage
 * 或者从 common.messaging 中导入（两者等价）
 */
package nan.produced.prism.core.common.api;
