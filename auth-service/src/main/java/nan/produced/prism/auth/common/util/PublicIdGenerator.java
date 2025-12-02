package nan.produced.prism.auth.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * PublicId 生成器
 * 生成格式: prism-{YYYYMMDD}-{8位随机字符}
 *
 * 示例: prism-20250102-A7F3B2E9
 *
 * 特点:
 * - 平台标识: prism- 前缀标识来源
 * - 时间信息: YYYYMMDD 格式的创建日期
 * - 唯一性: 8位Base32随机字符（排除易混淆字符）
 * - URL安全: 只包含字母和数字，适合作为路径参数
 */
public class PublicIdGenerator {

    private static final String PREFIX = "prism";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Base32 字符集（排除易混淆字符：0/O, 1/I/L）
     * 共32个字符：2-9（8个）+ A-H, J-K, M-N, P-Z（24个，排除I/L/O）
     */
    private static final String BASE32_CHARS = "234567892ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 PublicId
     * @return 格式为 prism-{YYYYMMDD}-{8位随机字符} 的字符串
     */
    public static String generate() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        String randomPart = generateRandomPart();
        return String.format("%s-%s-%s", PREFIX, date, randomPart);
    }

    /**
     * 生成指定长度的随机字符串（Base32编码）
     */
    private static String generateRandomPart() {
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            int index = RANDOM.nextInt(BASE32_CHARS.length());
            sb.append(BASE32_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 验证 PublicId 格式是否合法
     * @param publicId 待验证的 PublicId
     * @return 格式是否合法
     */
    public static boolean isValid(String publicId) {
        if (publicId == null) {
            return false;
        }

        // 格式: prism-YYYYMMDD-RRRRRRRR (总长度：25字符)
        // prism(5) - (1) YYYYMMDD(8) - (1) RRRRRRRR(8) = 23
        if (publicId.length() != 23) {
            return false;
        }

        String[] parts = publicId.split("-");
        if (parts.length != 3) {
            return false;
        }

        // 验证前缀
        if (!PREFIX.equals(parts[0])) {
            return false;
        }

        // 验证日期部分（8位数字）
        if (!parts[1].matches("\\d{8}")) {
            return false;
        }

        // 验证随机部分（8位Base32字符）
        if (parts[2].length() != 8) {
            return false;
        }
        for (char c : parts[2].toCharArray()) {
            if (BASE32_CHARS.indexOf(c) == -1) {
                return false;
            }
        }

        return true;
    }
}
