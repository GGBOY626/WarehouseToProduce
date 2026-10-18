package nz.co.warehouse.ai;

import nz.co.warehouse.auth.AuthController;
import nz.co.warehouse.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class AiAccessTest {
    private final String token="test-only-internal-token-32-characters";
    private AiAccess access(){var a=new AiAccess();ReflectionTestUtils.setField(a,"token",token);ReflectionTestUtils.setField(a,"enabled",true);return a;}
    private MockHttpServletRequest logged(){var r=new MockHttpServletRequest();r.getSession().setAttribute(AuthController.AUTH_SESSION_KEY,true);return r;}
    private final AiDtos.Scope scope=new AiDtos.Scope("","","A",null,"UPPER",false,null);
    @Test void anonymousCannotCreateCapabilityAndOrdinaryUsersCannotForgeInternalToken(){
        var a=access();assertThatThrownBy(()->a.issue(new MockHttpServletRequest(),scope)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->a.internal("Bearer forged")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->a.internal(null)).isInstanceOf(BusinessException.class);
        a.internal("Bearer "+token);
        assertThatThrownBy(()->a.resolve("forged")).isInstanceOf(BusinessException.class);
    }
    @Test void capabilityIsScopeBoundRevokedAndLimited(){
        var a=access();var r=logged();String ticket=a.issue(r,scope);
        for(int n=0;n<6;n++)assertThat(a.resolve(ticket)).isEqualTo(scope);
        assertThatThrownBy(()->a.resolve(ticket)).isInstanceOf(BusinessException.class);
        a.revoke(ticket);assertThatThrownBy(()->a.resolve(ticket)).isInstanceOf(BusinessException.class);
        final String revoked=a.issue(r,scope);r.getSession().invalidate();
        assertThatThrownBy(()->a.resolve(revoked)).isInstanceOf(BusinessException.class);
    }
    @Test void rateLimitSurvivesSessionRotationAndDisabledAiFailsClosed(){
        var a=access();for(int n=0;n<10;n++){String id=a.issue(logged(),scope);a.revoke(id);}
        assertThatThrownBy(()->a.issue(logged(),scope)).isInstanceOf(BusinessException.class);
        ReflectionTestUtils.setField(a,"enabled",false);assertThatThrownBy(a::configured).isInstanceOf(BusinessException.class);
    }
}
