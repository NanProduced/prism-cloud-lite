package nan.produced.prism.device.common.exception;

public interface ErrorCode {

    String getCode();
    String getMessage();
    ErrorLevel getLevel();
    HttpStatusCode getHttpStatus();

}