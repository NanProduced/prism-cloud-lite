package nan.produced.prism.device.infrastracture.encoder;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.port.outbound.auth.EncodePort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptEncoderAdapter implements EncodePort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matchesByPasswordEncoder(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public String encodeByPasswordEncoder(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
