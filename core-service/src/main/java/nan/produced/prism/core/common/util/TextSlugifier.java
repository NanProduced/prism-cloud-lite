package nan.produced.prism.core.common.util;

import com.github.promeg.pinyinhelper.Pinyin;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 文本转slug
 *
 * @author Nan
 */
public final class TextSlugifier {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern TRIM_DASHES = Pattern.compile("(^-+)|(-+$)");
    private static final int DEFAULT_MAX_LENGTH = 64;

    private TextSlugifier() {
    }

    public static String toSlug(String input) {
        return toSlug(input, DEFAULT_MAX_LENGTH);
    }

    public static String toSlug(String input, int maxLength) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        String transliterated = transliterate(input);
        String normalized = Normalizer.normalize(transliterated, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String lowerCase = withoutDiacritics.toLowerCase(Locale.ROOT);
        String collapsed = NON_ALPHANUM.matcher(lowerCase).replaceAll("-");
        String cleaned = TRIM_DASHES.matcher(collapsed).replaceAll("");
        if (maxLength > 0 && cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
            cleaned = TRIM_DASHES.matcher(cleaned).replaceAll("");
        }
        return cleaned;
    }

    public static String transliterate(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(input.length() * 2);
        input.codePoints().forEach(codePoint -> appendCodePoint(builder, codePoint));
        return builder.toString();
    }

    private static void appendCodePoint(StringBuilder builder, int codePoint) {
        if (codePoint <= Character.MAX_VALUE && Pinyin.isChinese((char) codePoint)) {
            builder.append(Pinyin.toPinyin((char) codePoint).toLowerCase(Locale.ROOT));
            builder.append(' ');
        } else {
            builder.appendCodePoint(codePoint);
        }
    }
}
