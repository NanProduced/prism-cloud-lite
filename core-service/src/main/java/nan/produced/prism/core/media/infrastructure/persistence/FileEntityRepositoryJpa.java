package nan.produced.prism.core.media.infrastructure.persistence;

import nan.produced.prism.core.media.application.domain.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件实体 JPA 仓库
 *
 * @author Nan
 */
@Repository
public interface FileEntityRepositoryJpa extends JpaRepository<FileEntity, String> {

    /**
     * 根据 MD5 查找文件实体
     */
    Optional<FileEntity> findByMd5(String md5);

    /**
     * 批量根据 MD5 查找文件实体
     */
    List<FileEntity> findByMd5In(List<String> md5List);

    /**
     * 增加引用计数
     *
     * @param fileId 文件ID
     * @param delta  增量
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE FileEntity f SET f.refCount = f.refCount + :delta WHERE f.fileId = :fileId")
    int incrementRefCount(@Param("fileId") String fileId, @Param("delta") int delta);
}
