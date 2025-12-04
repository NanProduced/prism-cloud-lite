package nan.produced.prism.device.boot.global;

import nan.produced.prism.device.common.exception.DeviceResponseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 设备请求响应处理，设备只需要HttpStatus
     * @param ex 设备请求异常
     * @return 设备响应结果
     */
    @ExceptionHandler(DeviceResponseException.class)
    public ResponseEntity<Void> handlerDeviceException(DeviceResponseException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus().getValue())
                .build();
    }
}
