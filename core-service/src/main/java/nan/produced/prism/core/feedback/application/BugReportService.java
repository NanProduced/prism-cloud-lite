package nan.produced.prism.core.feedback.application;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportDetailView;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportListItemView;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportPageView;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportReplyRequest;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportUpdateStatusRequest;
import nan.produced.prism.core.feedback.api.dto.BugReportAttachment;
import nan.produced.prism.core.feedback.api.dto.UserBugReportCreateRequest;
import nan.produced.prism.core.feedback.api.dto.UserBugReportCreatedView;
import nan.produced.prism.core.feedback.domain.BugReportEntity;
import nan.produced.prism.core.feedback.domain.BugReportStatus;
import nan.produced.prism.core.feedback.infrastructure.BugReportRepository;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.service.UserProfileService;
import nan.produced.prism.core.notification.email.AdminMailService;
import nan.produced.prism.core.notification.email.AdminMailTemplate;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private static final Safelist SAFE_HTML = Safelist.relaxed()
            .addTags("span")
            .addAttributes(":all", "style")
            .addAttributes("a", "target", "rel")
            .addProtocols("img", "src", "http", "https", "data");

    private final BugReportRepository bugReportRepository;
    private final UserProfileService userProfileService;
    private final AdminMailService adminMailService;

    @Transactional
    public UserBugReportCreatedView createForCurrentUser(UserBugReportCreateRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.title())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "title is required");
        }
        if (!StringUtils.hasText(request.contentHtml())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "contentHtml is required");
        }

        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();

        String sanitizedHtml = sanitizeHtml(request.contentHtml());
        String plain = toPlainText(sanitizedHtml);

        BugReportEntity entity = BugReportEntity.builder()
                .id(UUID.randomUUID())
                .userId(profile.getId())
                .userPublicId(profile.getPublicId())
                .userEmail(profile.getEmail())
                .userPhone(profile.getPhone())
                .contactEmail(normalizeEmail(request.contactEmail()))
                .title(request.title().trim())
                .contentHtml(sanitizedHtml)
                .contentPlain(plain)
                .pageUrl(trimOrNull(request.pageUrl(), 500))
                .userAgent(extractUserAgent(httpRequest))
                .attachments(normalizeAttachments(request.attachments()))
                .status(BugReportStatus.OPEN)
                .build();

        BugReportEntity saved = bugReportRepository.save(entity);
        return new UserBugReportCreatedView(saved.getId(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AdminBugReportPageView listForAdmin(BugReportStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<BugReportEntity> result = status == null
                ? bugReportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : bugReportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        List<AdminBugReportListItemView> items = result.getContent().stream()
                .map(it -> new AdminBugReportListItemView(
                        it.getId(),
                        it.getStatus(),
                        it.getTitle(),
                        it.getUserPublicId(),
                        it.getUserEmail(),
                        it.getContactEmail(),
                        it.getPageUrl(),
                        it.getCreatedAt()
                ))
                .toList();

        return new AdminBugReportPageView(items, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminBugReportDetailView getDetail(UUID id) {
        BugReportEntity it = bugReportRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ENDPOINT_NOT_FOUND, "bug report not found"));

        return new AdminBugReportDetailView(
                it.getId(),
                it.getStatus(),
                it.getTitle(),
                it.getContentHtml(),
                it.getContentPlain(),
                it.getPageUrl(),
                it.getUserAgent(),
                it.getUserPublicId(),
                it.getUserEmail(),
                it.getUserPhone(),
                it.getContactEmail(),
                denormalizeAttachments(it.getAttachments()),
                it.getAdminNote(),
                it.getReplySubject(),
                it.getReplyHtml(),
                it.getRepliedAt(),
                it.getRepliedBy(),
                it.getCreatedAt()
        );
    }

    @Transactional
    public void updateStatus(UUID id, AdminBugReportUpdateStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "status is required");
        }
        BugReportEntity entity = bugReportRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ENDPOINT_NOT_FOUND, "bug report not found"));

        entity.setStatus(request.status());
        entity.setAdminNote(StringUtils.hasText(request.adminNote()) ? request.adminNote().trim() : null);
        bugReportRepository.save(entity);
    }

    @Transactional
    public BugReportEntity saveReply(UUID id, AdminBugReportReplyRequest request) {
        if (request == null || !StringUtils.hasText(request.replyHtml())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "replyHtml is required");
        }
        BugReportEntity entity = bugReportRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ENDPOINT_NOT_FOUND, "bug report not found"));

        String sanitized = sanitizeHtml(request.replyHtml());
        entity.setReplyHtml(sanitized);
        entity.setReplySubject(StringUtils.hasText(request.subject()) ? request.subject().trim() : defaultReplySubject(entity));
        entity.setRepliedAt(Instant.now());
        entity.setRepliedBy(CloudAuthContext.getCurrentPublicId());
        entity.setStatus(BugReportStatus.IN_PROGRESS);
        BugReportEntity saved = bugReportRepository.save(entity);

        String to = StringUtils.hasText(saved.getContactEmail()) ? saved.getContactEmail() : saved.getUserEmail();
        Map<String, Object> vars = new HashMap<>();
        vars.put("replyHtml", saved.getReplyHtml());
        vars.put("bugId", saved.getId());
        vars.put("title", saved.getTitle());

        adminMailService.sendTemplateEmail(
                AdminMailTemplate.BUG_REPORT_REPLY,
                to,
                saved.getReplySubject(),
                "mail/bug-report-reply",
                vars
        );

        return saved;
    }

    private static String defaultReplySubject(BugReportEntity entity) {
        String suffix = entity != null && StringUtils.hasText(entity.getTitle()) ? " - " + entity.getTitle().trim() : "";
        String base = "关于你提交的反馈";
        String combined = base + suffix;
        return combined.length() > 200 ? combined.substring(0, 200) : combined;
    }

    private static String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trimOrNull(request.getHeader("User-Agent"), 500);
    }

    private static String sanitizeHtml(String html) {
        String cleaned = Jsoup.clean(html, SAFE_HTML);
        return StringUtils.hasText(cleaned) ? cleaned : "<p></p>";
    }

    private static String toPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private static String trimOrNull(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static List<Map<String, Object>> normalizeAttachments(List<BugReportAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (BugReportAttachment attachment : attachments) {
            if (attachment == null || !StringUtils.hasText(attachment.url())) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("url", attachment.url().trim());
            if (StringUtils.hasText(attachment.name())) {
                map.put("name", attachment.name().trim());
            }
            if (StringUtils.hasText(attachment.mimeType())) {
                map.put("mimeType", attachment.mimeType().trim());
            }
            if (attachment.sizeBytes() != null && attachment.sizeBytes() > 0) {
                map.put("sizeBytes", attachment.sizeBytes());
            }
            result.add(map);
        }
        return result;
    }

    private static List<BugReportAttachment> denormalizeAttachments(List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<BugReportAttachment> result = new ArrayList<>();
        for (Map<String, Object> map : attachments) {
            if (map == null) {
                continue;
            }
            Object url = map.get("url");
            if (url == null || !StringUtils.hasText(url.toString())) {
                continue;
            }
            Object name = map.get("name");
            Object mimeType = map.get("mimeType");
            Object sizeBytes = map.get("sizeBytes");
            Long size = null;
            if (sizeBytes != null) {
                try {
                    size = Long.parseLong(sizeBytes.toString());
                } catch (Exception ignored) {
                    size = null;
                }
            }
            result.add(new BugReportAttachment(
                    url.toString(),
                    name == null ? null : name.toString(),
                    mimeType == null ? null : mimeType.toString(),
                    size
            ));
        }
        return result;
    }
}
