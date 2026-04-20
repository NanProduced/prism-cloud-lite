package nan.produced.prism.payment.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    SUCCESS(
        "PAY-0000",
        "OK",
        "操作成功",
        HttpStatus.OK,
        false
    ),

    INVALID_PARAMETER(
        "PAY-1001",
        "请求参数不合法",
        "请求参数不合法，请检查输入",
        HttpStatus.BAD_REQUEST,
        true
    ),

    ORDER_NOT_FOUND(
        "PAY-1002",
        "订单不存在",
        "订单不存在",
        HttpStatus.NOT_FOUND,
        false
    ),

    ORDER_ALREADY_PAID(
        "PAY-1003",
        "订单已支付",
        "订单已支付，无法重复支付",
        HttpStatus.CONFLICT,
        false
    ),

    ORDER_CANCELED(
        "PAY-1004",
        "订单已取消",
        "订单已取消",
        HttpStatus.CONFLICT,
        false
    ),

    SUBSCRIPTION_NOT_FOUND(
        "PAY-1005",
        "订阅不存在",
        "订阅不存在",
        HttpStatus.NOT_FOUND,
        false
    ),

    PADDLE_API_ERROR(
        "PAY-2001",
        "Paddle API调用失败",
        "支付服务暂时不可用，请稍后再试",
        HttpStatus.BAD_GATEWAY,
        true
    ),

    WEBHOOK_SIGNATURE_INVALID(
        "PAY-2002",
        "Webhook签名验证失败",
        "签名验证失败",
        HttpStatus.UNAUTHORIZED,
        false
    ),

    WEBHOOK_EVENT_DUPLICATE(
        "PAY-2003",
        "Webhook事件重复",
        "事件已处理",
        HttpStatus.OK,
        false
    ),

    INTERNAL_SERVER_ERROR(
        "PAY-5000",
        "系统内部错误",
        "系统繁忙，请稍后再试",
        HttpStatus.INTERNAL_SERVER_ERROR,
        true
    ),

    EXTERNAL_SERVICE_ERROR(
        "PAY-5001",
        "外部服务调用失败",
        "服务暂时不可用，请稍后再试",
        HttpStatus.BAD_GATEWAY,
        true
    );

    private final String code;
    private final String message;
    private final String displayMessage;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    ErrorCode(String code, String message, String displayMessage, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.message = message;
        this.displayMessage = displayMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }
}
