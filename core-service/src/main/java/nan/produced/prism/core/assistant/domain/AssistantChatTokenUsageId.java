package nan.produced.prism.core.assistant.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantChatTokenUsageId implements Serializable {

    private UUID userId;

    private LocalDate day;
}
