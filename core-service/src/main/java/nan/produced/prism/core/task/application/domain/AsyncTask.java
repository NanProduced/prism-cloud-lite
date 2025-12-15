package nan.produced.prism.core.task.application.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "async_task")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "task_type", discriminatorType = DiscriminatorType.STRING)
public abstract class AsyncTask {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    /**
     * 任务类型
     */
    @Column(name = "task_type", insertable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    /**
     * 任务状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * 开始时间
     */
    private Instant startedAt;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * 软删除时间
     * - null: 未删除，列表可见
     * - 有值: 用户已删除，列表不显示，但可通过关联素材查到
     */
    private Instant deletedAt;

    // ========== 状态机方法 ==========

    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete() {
        this.status = TaskStatus.SUCCESS;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * 判断是否可以物理删除
     * 子类重写，检查关联资源是否都已删除
     */
    public abstract boolean canPurge();

}
