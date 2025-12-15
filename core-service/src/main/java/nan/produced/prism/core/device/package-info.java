/**
 * 设备管理模块
 *
 * 职责：
 * - 处理设备的生命周期管理（创建、更新、删除、查询）
 * - 接收和处理设备上报的事件（状态、属性、命令）
 * - 维护设备的在线状态和属性数据
 *
 * 依赖关系：
 * - common.messaging：依赖 DeviceEventMessage 作为消息传输对象
 *
 * 结构：
 * - application：应用服务层（业务逻辑）
 *   - port：定义出站和入站端口
 *   - service：具体的服务实现
 * - domain：领域模型层
 *   - DeviceEntity：设备聚合根
 *   - DeviceProperties：设备属性值对象
 * - infrastructure：基础设施层
 *   - messaging：消息监听和处理
 *   - persistence：数据访问层
 *
 * 特性：
 * - 支持部分属性更新（JSON 深度合并）
 * - 完整的异常处理和事务管理
 * - 分布式追踪和日志记录
 */
@org.springframework.modulith.ApplicationModule(displayName = "Device")