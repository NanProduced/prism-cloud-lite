package nan.produced.prism.core.assistant.application.tools;

import java.util.List;

public interface AssistantToolPolicyCatalog {

    AssistantToolPolicy policyFor(String toolName);

    List<AssistantToolPolicy> listPolicies();
}
