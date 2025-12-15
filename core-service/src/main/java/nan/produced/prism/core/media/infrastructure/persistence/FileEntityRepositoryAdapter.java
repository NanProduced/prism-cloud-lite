package nan.produced.prism.core.media.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.domain.FileEntity;
import nan.produced.prism.core.media.application.repository.FileEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 文件实体数据仓库适配器
 * <p>
 * 实现 {@link FileEntityRepository} 出站端口
 * 作为六边形架构中的适配器，负责将域模型的仓库接口与底层的持久化实现（Spring Data JPA）相连接
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileEntityRepositoryAdapter implements FileEntityRepository {

    private final FileEntityRepositoryJpa fileEntityRepositoryJpa;

    @Override
    public Optional<FileEntity> findByMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            log.debug("FileEntityRepositoryAdapter - 查询MD5为空，返回空结果");
            return Optional.empty();
        }
        return fileEntityRepositoryJpa.findByMd5(md5);
    }

    @Override
    public List<FileEntity> findByMd5In(List<String> md5List) {
        if (md5List == null || md5List.isEmpty()) {
            log.debug("FileEntityRepositoryAdapter - MD5列表为空，返回空结果");
            return Collections.emptyList();
        }
        // 过滤空值
        var validMd5List = md5List.stream()
                .filter(md5 -> md5 != null && !md5.isBlank())
                .toList();
        if (validMd5List.isEmpty()) {
            return Collections.emptyList();
        }
        return fileEntityRepositoryJpa.findByMd5In(validMd5List);
    }

    @Override
    public Optional<FileEntity> findById(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            log.debug("FileEntityRepositoryAdapter - 查询文件ID为空，返回空结果");
            return Optional.empty();
        }
        return fileEntityRepositoryJpa.findById(fileId);
    }

    @Override
    public List<FileEntity> findByIdIn(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            log.debug("FileEntityRepositoryAdapter - 文件ID列表为空，返回空结果");
            return Collections.emptyList();
        }
        var validIds = fileIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }
        return fileEntityRepositoryJpa.findAllById(validIds);
    }

    @Override
    public FileEntity save(FileEntity fileEntity) {
        if (fileEntity == null) {
            throw new IllegalArgumentException("FileEntity cannot be null");
        }
        return fileEntityRepositoryJpa.save(fileEntity);
    }

    @Override
    public List<FileEntity> saveAll(List<FileEntity> fileEntities) {
        if (fileEntities == null || fileEntities.isEmpty()) {
            return Collections.emptyList();
        }
        return fileEntityRepositoryJpa.saveAll(fileEntities);
    }

    @Override
    @Transactional
    public int incrementRefCount(String fileId, int delta) {
        if (fileId == null || fileId.isBlank()) {
            log.warn("FileEntityRepositoryAdapter - 增加引用计数时文件ID为空");
            return 0;
        }
        return fileEntityRepositoryJpa.incrementRefCount(fileId, delta);
    }
}
