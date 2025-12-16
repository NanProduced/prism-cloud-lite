package nan.produced.prism.core.device.domain.customfield;

import java.util.Comparator;

public class CustomFieldConstant {

    private CustomFieldConstant() {
    }

    public static final Comparator<DeviceCustomFieldDefEntity> DEF_SORT = Comparator
            .comparing((DeviceCustomFieldDefEntity d) -> d.getSequence() != null ? d.getSequence() : Integer.MAX_VALUE)
            .thenComparing(DeviceCustomFieldDefEntity::getFieldId);

    public static final Comparator<DeviceCustomFieldOptionEntity> OPTION_SORT = Comparator
            .comparing((DeviceCustomFieldOptionEntity o) -> o.getSequence() != null ? o.getSequence() : Integer.MAX_VALUE)
            .thenComparing(DeviceCustomFieldOptionEntity::getOptionId);
}
