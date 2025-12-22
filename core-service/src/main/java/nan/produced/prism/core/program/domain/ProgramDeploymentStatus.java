package nan.produced.prism.core.program.domain;

/**
 * 设备与节目版本的绑定状态（由设备下载进度上报驱动）。
 */
public enum ProgramDeploymentStatus {
    /**
     * 已收到下载进度上报（/wp-json/screen/v1/info）。
     */
    DOWNLOADING,
    /**
     * 设备已完成该节目所需文件下载（根据下载进度判断）。
     */
    DOWNLOADED,
}
