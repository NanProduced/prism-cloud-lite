package nan.produced.prism.infrastracture.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import nan.produced.prism.application.domain.device.DeviceAccountStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * 终端设备认证
 * 用于Spring security认证和授权
 *
 * @author Nan
 */
@Data
public class DevicePrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 6626609904542200501L;
    /**
     * 终端设备唯一Id
     */
    private Long deviceId;

    private DeviceAccountStatus status;

    public DevicePrincipal(Long deviceId, DeviceAccountStatus status) {
        this.deviceId = deviceId;
        this.status = status;
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("DEVICE"));
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * 用设备Id作为唯一识别
     * @return 设备Id
     */
    @Override
    public String getUsername() {
        return deviceId.toString();
    }

    /**
     * 账号是否未过期 - 终端设备账号不会过期
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账号是否未锁定 - 根据状态判断
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return status != null && status == DeviceAccountStatus.ENABLE;
    }

    /**
     * 凭证是否未过期 - 终端设备凭证不会过期
     */
    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否可用 - 根据状态判断
     */
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status != null && status == DeviceAccountStatus.ENABLE;
    }


    @Override
    public String toString() {
        return String.format("DevicePrincipal{deviceId=%d}", deviceId);
    }
}
