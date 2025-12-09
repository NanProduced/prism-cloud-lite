package nan.produced.prism.auth.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpUtils {

    private HttpUtils() {
        throw new UnsupportedOperationException();
    }

    public static void responseJson(String json, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(json.getBytes(StandardCharsets.UTF_8).length);
        response.getWriter().write(json);

    }
}
