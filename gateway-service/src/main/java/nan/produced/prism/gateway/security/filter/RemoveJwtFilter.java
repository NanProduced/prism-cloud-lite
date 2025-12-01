package nan.produced.prism.gateway.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

@Component
public class RemoveJwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest modifiedRequest = new RemoveAuthHeaderRequestWrapper(request);
        filterChain.doFilter(modifiedRequest, response);
    }

    private static class RemoveAuthHeaderRequestWrapper extends HttpServletRequestWrapper {

        public RemoveAuthHeaderRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        public Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}
