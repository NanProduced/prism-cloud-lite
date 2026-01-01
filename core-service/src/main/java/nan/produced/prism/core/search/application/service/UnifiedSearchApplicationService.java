package nan.produced.prism.core.search.application.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.media.application.port.inbound.MediaSearchFacade;
import nan.produced.prism.core.program.application.port.inbound.ProgramSearchFacade;
import nan.produced.prism.core.search.api.dto.DeviceSearchResult;
import nan.produced.prism.core.search.api.dto.MediaSearchResult;
import nan.produced.prism.core.search.api.dto.ProgramSearchResult;
import nan.produced.prism.core.search.api.dto.UnifiedSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UnifiedSearchApplicationService {

    public enum SearchType {
        DEVICE,
        PROGRAM,
        MEDIA;

        public static SearchType parse(String raw) {
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "device", "devices" -> DEVICE;
                case "program", "programs" -> PROGRAM;
                case "media", "asset", "assets" -> MEDIA;
                default -> null;
            };
        }
    }

    private final DeviceSearchUseCase deviceSearchUseCase;
    private final ProgramSearchFacade programSearchFacade;
    private final MediaSearchFacade mediaSearchFacade;

    @Transactional(readOnly = true)
    public UnifiedSearchResponse search(UUID userId, String q, Set<SearchType> types, Integer limit) {
        if (userId == null) {
            return UnifiedSearchResponse.builder()
                    .devices(List.of())
                    .programs(List.of())
                    .media(List.of())
                    .build();
        }

        if (!StringUtils.hasText(q)) {
            return UnifiedSearchResponse.builder()
                    .devices(List.of())
                    .programs(List.of())
                    .media(List.of())
                    .build();
        }

        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);

        EnumSet<SearchType> safeTypes = (types == null || types.isEmpty())
                ? EnumSet.allOf(SearchType.class)
                : EnumSet.copyOf(types);

        List<DeviceSearchResult> devices = safeTypes.contains(SearchType.DEVICE)
                ? deviceSearchUseCase.searchDevices(userId, q.trim(), safeLimit).stream()
                .filter(java.util.Objects::nonNull)
                .map(d -> DeviceSearchResult.builder()
                        .id(d.id())
                        .name(d.name())
                        .serialNo(d.serialNo())
                        .ip(d.ip())
                        .status(toDeviceStatus(d.onlineStatus()))
                        .model(d.model())
                        .build())
                .toList()
                : List.of();

        List<ProgramSearchResult> programs = safeTypes.contains(SearchType.PROGRAM)
                ? programSearchFacade.searchPrograms(userId, q.trim(), safeLimit).stream()
                .filter(java.util.Objects::nonNull)
                .map(p -> ProgramSearchResult.builder()
                        .id(p.id())
                        .name(p.name())
                        .resolution(p.resolution())
                        .updatedAt(p.updatedAt())
                        .build())
                .toList()
                : List.of();

        List<MediaSearchResult> media = safeTypes.contains(SearchType.MEDIA)
                ? mediaSearchFacade.searchMedia(userId, q.trim(), safeLimit).stream()
                .filter(java.util.Objects::nonNull)
                .map(a -> MediaSearchResult.builder()
                        .id(a.id())
                        .title(a.title())
                        .kind(a.kind())
                        .size(a.sizeBytes())
                        .thumbnailUrl(a.thumbnailUrl())
                        .build())
                .toList()
                : List.of();

        return UnifiedSearchResponse.builder()
                .devices(devices)
                .programs(programs)
                .media(media)
                .build();
    }

    private String toDeviceStatus(Integer onlineStatus) {
        if (onlineStatus == null) {
            return "unknown";
        }
        return onlineStatus == 1 ? "online" : "offline";
    }
}
