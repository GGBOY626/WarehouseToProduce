package nz.co.warehouse.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class WriteAuthorizationInterceptor implements HandlerInterceptor {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (READ_METHODS.contains(request.getMethod()) || request.getRequestURI().equals("/api/auth/login")) return true;
        var session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(AuthController.AUTH_SESSION_KEY))) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"AUTH_REQUIRED\",\"message\":\"请登录后进行管理操作。\",\"fieldErrors\":[]}");
        return false;
    }
}
