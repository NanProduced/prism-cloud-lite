/**
 * 媒体素材管理模块
 *
 * 职责：
 * - 处理文件上传（Better Upload 协议兼容）
 * - 生成 S3 预签名 URL 实现浏览器直传
 * - 管理素材的元数据和生命周期
 * - 支持 MD5 秒传检查
 *
 * 依赖关系：
 * - common：依赖公共异常处理和响应结构
 *
 * 结构：
 * - application：应用服务层
 *   - port：定义出站和入站端口
 *   - service：具体的服务实现
 * - domain：领域模型层（TODO: 后续实现）
 * - infrastructure：基础设施层
 *   - config：S3 和上传配置
 *   - adapter：S3 适配器实现
 *   - web：REST 控制器
 *
 * 特性：
 * - Better Upload v3.0.6 协议兼容
 * - 支持普通上传和 Multipart 分片上传
 * - MD5 秒传检查
 * - 原文件 + 封面图成对联动
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media")
package nan.produced.prism.core.media;
