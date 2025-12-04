package nan.produced.prism.auth.internal.controller;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.internal.dto.InternalUserResponse;
import nan.produced.prism.auth.internal.service.InternalUserService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getUserByPublicId(@PathVariable String publicId) {
        try {
            InternalUserResponse response = internalUserService.findByPublicId(publicId);
            return ResponseEntity.ok(ApiResponse.success(response).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalUserResponse>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
