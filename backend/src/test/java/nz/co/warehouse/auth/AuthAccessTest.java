package nz.co.warehouse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAccessTest {
    private final WriteAuthorizationInterceptor interceptor = new WriteAuthorizationInterceptor();

    @Test
    void publicReadsAreAllowedButAnonymousWritesAreRejected() throws Exception {
        var read = new MockHttpServletRequest("GET", "/api/movements");
        assertThat(interceptor.preHandle(read, new MockHttpServletResponse(), new Object())).isTrue();

        var write = new MockHttpServletRequest("POST", "/api/movements");
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(write, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_REQUIRED");
    }

    @Test
    void loginSessionAllowsWrites() throws Exception {
        var controller = new AuthController();
        ReflectionTestUtils.setField(controller, "configuredUsername", "owner");
        ReflectionTestUtils.setField(controller, "configuredPassword", "secret");
        var request = new MockHttpServletRequest("POST", "/api/auth/login");

        var login = controller.login(new AuthController.LoginRequest("owner", "secret"), request);
        assertThat(login.authenticated()).isTrue();

        request.setMethod("POST");
        request.setRequestURI("/api/products");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
