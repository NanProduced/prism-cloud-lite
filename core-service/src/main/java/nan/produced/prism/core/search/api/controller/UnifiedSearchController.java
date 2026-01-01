package nan.produced.prism.core.search.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.search.api.dto.UnifiedSearchResponse;
import nan.produced.prism.core.search.application.service.UnifiedSearchApplicationService;
import nan.produced.prism.core.search.application.service.UnifiedSearchApplicationService.SearchType;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Unified Search")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class UnifiedSearchController {

    private final UnifiedSearchApplicationService unifiedSearchApplicationService;

    @Operation(summary = "聚合搜索（全局搜索/Command Palette）")
    @GetMapping
    public ResponseEntity<BffResponse<UnifiedSearchResponse>> search(
            @RequestParam("q") String q,
            @RequestParam(value = "types", required = false) String types,
            @RequestParam(value = "limit", required = false) Integer limit) {

        if (!StringUtils.hasText(q)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "q is required");
        }

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        Set<SearchType> typeSet = parseTypes(types);
        UnifiedSearchResponse data = unifiedSearchApplicationService.search(userId, q, typeSet, limit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    private Set<SearchType> parseTypes(String types) {
        if (!StringUtils.hasText(types)) {
            return EnumSet.allOf(SearchType.class);
        }
        EnumSet<SearchType> set = EnumSet.noneOf(SearchType.class);
        for (String part : types.split(",")) {
            SearchType t = SearchType.parse(part);
            if (t != null) {
                set.add(t);
            }
        }
        return set.isEmpty() ? EnumSet.allOf(SearchType.class) : set;
    }
}

