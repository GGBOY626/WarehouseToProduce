package nz.co.warehouse.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import nz.co.warehouse.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String AUTH_SESSION_KEY = "warehouse.authenticated";

    @Value("${ADMIN_USERNAME:admin}") private String configuredUsername;
    @Value("${ADMIN_PASSWORD:change-this-password}") private String configuredPassword;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(boolean authenticated, String username) {}

    @GetMapping("/status")
    public AuthResponse status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null && Boolean.TRUE.equals(session.getAttribute(AUTH_SESSION_KEY));
        return new AuthResponse(authenticated, authenticated ? configuredUsername : null);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (!same(request.username(), configuredUsername) || !same(request.password(), configuredPassword))
            throw new BusinessException("LOGIN_FAILED", "账号或密码错误。", HttpStatus.UNAUTHORIZED);
        servletRequest.getSession(true).setAttribute(AUTH_SESSION_KEY, true);
        return new AuthResponse(true, configuredUsername);
    }

    @PostMapping("/logout")
    public AuthResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return new AuthResponse(false, null);
    }

    private boolean same(String provided, String configured) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8));
    }
}
