package nan.produced.prism.auth.security.otp;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.ThirdPartyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 号码认证服务（Phone Number Verification Service）
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PnvService {

    private final RedisTemplate<String, String> redisTemplate;
    private final PnvProps pnvProps;
    private final AsyncClient aliyunClient;

    private static final String PNV_RATE_LIMIT_KEY_PREFIX = "auth:pnv:rate-limit:";
    private static final String PNV_ATTEMPT_KEY_PREFIX = "auth:pnv:attempt:";

    /**
     * 判断是否可以请求验证码
     * @param phone 手机号码
     * @return 是否可以请求验证码
     */
    public Boolean canApplyPnv(String phone) {
        String key = PNV_RATE_LIMIT_KEY_PREFIX + phone;
        return redisTemplate.opsForValue().setIfAbsent(key, "1", pnvProps.getRateLimit().getWindowMinutes(), TimeUnit.MINUTES);
    }

    public void verifyPnvCode(String phone, String code) {
        checkRateLimit(phone);

        CheckSmsVerifyCodeRequest checkSmsVerifyCodeRequest = CheckSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .verifyCode(code)
                .build();

        try {
            CompletableFuture<CheckSmsVerifyCodeResponse> response = aliyunClient.checkSmsVerifyCode(checkSmsVerifyCodeRequest);
            CheckSmsVerifyCodeResponse resp = response.get();
            if (resp.getBody().getSuccess().equals(false)) {
                log.warn("阿里云短信服务调用结果: {}", resp.getBody().getMessage());
                throw new BizException(ErrorCode.INVALID_OTP);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("阿里云短信服务调用异常", e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new ThirdPartyException(ErrorCode.PHONE_NUMBER_VALIDATION_CODE_VERIFY_ERROR);
        }

    }

    /**
     * 发送PNV验证码
     * @param phone 手机号码
     */
    public void sendPnvCode(String phone) {

        try {
            // 异步获取API请求的返回值
            CompletableFuture<SendSmsVerifyCodeResponse> responseFuture = aliyunClient.sendSmsVerifyCode(buildLoginPnvReqeust(phone));

            SendSmsVerifyCodeResponse sendSmsVerifyCodeResponse = responseFuture.get();

            log.debug("阿里云短信服务调用结果: {}", sendSmsVerifyCodeResponse);

        } catch (ExecutionException e) {
            log.warn("阿里云短信服务调用异常", e);
            throw new ThirdPartyException(ErrorCode.PHONE_NUMBER_VALIDATION_CODE_GAIN_ERROR);
        } catch (InterruptedException e) {
            log.warn("阿里云短信服务调用异常（异步）", e);
            Thread.currentThread().interrupt(); // 重新设置中断状态
            throw new ThirdPartyException(ErrorCode.PHONE_NUMBER_VALIDATION_CODE_GAIN_ERROR);
        }
    }

    /**
     * 检查频率限制
     * @param phone 手机号码
     */
    private void checkRateLimit(String phone) {
        String attemptKey = PNV_ATTEMPT_KEY_PREFIX + phone;
        String attemptCountStr = redisTemplate.opsForValue().get(attemptKey);
        if (attemptCountStr != null) {
            int attemptCount = Integer.parseInt(attemptCountStr);
            if (attemptCount >= pnvProps.getRateLimit().getMaxAttempts()) {
                throw new BizException(ErrorCode.OTP_VERIFY_TOO_FREQUENT,
                        String.format("验证失败次数过多，请在%d分钟后重试", pnvProps.getRateLimit().getWindowMinutes()));
            }
        }
    }

    /**
     * 构建登录验证码请求
     * @param phone 手机号码
     * @return 请求
     */
    private SendSmsVerifyCodeRequest buildLoginPnvReqeust(String phone) {
        return SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(pnvProps.getSignName())
                .templateCode(pnvProps.getTemplate().getLogin())
                .codeLength(pnvProps.getLength())
                .templateParam(pnvProps.getTemplateParam())
                .build();
    }

    /**
     * 构建绑定操作验证码请求
     * @param phone 手机号码
     * @return 请求
     */
    private SendSmsVerifyCodeRequest buildBindPnvReqeust(String phone) {
        return SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(pnvProps.getSignName())
                .templateCode(pnvProps.getTemplate().getBind())
                .codeLength(pnvProps.getLength())
                .templateParam(pnvProps.getTemplateParam())
                .build();
    }

    /**
     * 构建重置密码验证码请求
     * @param phone 手机号码
     * @return 请求
     */
    private SendSmsVerifyCodeRequest buildResetPwdPnvReqeust(String phone) {
        return SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(pnvProps.getSignName())
                .templateCode(pnvProps.getTemplate().getReset())
                .codeLength(pnvProps.getLength())
                .templateParam(pnvProps.getTemplateParam())
                .build();
    }

    /**
     * 构建通用验证码请求
     * @param phone 手机号码
     * @return 请求
     */
    private SendSmsVerifyCodeRequest buildCommonPnvReqeust(String phone) {
        return SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(pnvProps.getSignName())
                .templateCode(pnvProps.getTemplate().getCommon())
                .codeLength(pnvProps.getLength())
                .templateParam(pnvProps.getTemplateParam())
                .build();
    }



}
