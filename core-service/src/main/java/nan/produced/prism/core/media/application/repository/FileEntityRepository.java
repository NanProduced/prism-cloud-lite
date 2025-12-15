package nan.produced.prism.core.media.application.repository;

import nan.produced.prism.core.media.application.domain.FileEntity;

import java.util.List;
import java.util.Optional;

/**
 * 文件实体仓库端口
 * <p>
 * 六边形架构中的出站端口，定义文件实体的数据访问接口
 *
 * @author Nan
 */
public interface FileEntityRepository {

    /**
     * 根据 MD5 查找文件实体
     *
     * @param md5 文件MD5哈希值
     * @return 文件实体，不存在返回空
     */
    Optional<FileEntity> findByMd5(String md5);

    /**
     * 批量根据 MD5 查找文件实体
     *
     * @param md5List MD5哈希值列表
     * @return 已存在的文件实体列表
     */
    List<FileEntity> findByMd5In(List<String> md5List);

    /**
     * 根据 ID 查找文件实体
     *
     * @param fileId 文件ID
     * @return 文件实体，不存在返回空
     */
    Optional<FileEntity> findById(String fileId);

    /**
     * 批量根据 ID 查找文件实体
     *
     * @param fileIds 文件ID列表
     * @return 文件实体列表
     */
    List<FileEntity> findByIdIn(List<String> fileIds);

    /**
     * 保存文件实体
     *
     * @param fileEntity 文件实体
     * @return 保存后的文件实体
     */
    FileEntity save(FileEntity fileEntity);

    /**
     * 批量保存文件实体
     *
     * @param fileEntities 文件实体列表
     * @return 保存后的文件实体列表
     */
    List<FileEntity> saveAll(List<FileEntity> fileEntities);

    /**
     * 增加引用计数
     *
     * @param fileId 文件ID
     * @param delta  增量（正数增加，负数减少）
     * @return 更新后的引用计数
     */
    int incrementRefCount(String fileId, int delta);
}
