package nan.produced.prism.device.common.utils;

public class CommonUtils {

    private CommonUtils() {}

    public static int toIntSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            return 0;
        }
        if (sizeBytes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) sizeBytes;
    }
}
