/**
 * 消息中心模块
 *
 * 职责：
 * - 持久化用户可查询的通知/任务消息（这些消息一定会通过 SSE 推送给前端）
 * - 提供“铃铛最近消息”和“消息中心分页筛选查询”接口
 * - 记录已读/未读状态与保留期清理
 *
 * 说明：
 * - 消息中心只承载“通知(Notification)”与“任务(Task)”两类消息
 * - 高频实时数据（telemetry/map 等）仅通过 SSE 推送，不落库到消息中心
 */
@org.springframework.modulith.ApplicationModule(displayName = "Message")
package nan.produced.prism.core.message;

