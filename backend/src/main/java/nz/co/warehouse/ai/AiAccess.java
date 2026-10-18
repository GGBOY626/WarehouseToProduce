package nz.co.warehouse.ai;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import nz.co.warehouse.auth.AuthController;
import nz.co.warehouse.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class AiAccess {
    @Value("${AI_INTERNAL_TOKEN:}") private String token;
    @Value("${AI_ENABLED:true}") private boolean enabled;
    private final Map<String,Permit> permits=new HashMap<>();
    private final Deque<Long> requests=new ArrayDeque<>();
    private record Permit(AiDtos.Scope scope,HttpSession session,long expires,int[] calls) {}
    public void configured(){if(!enabled||token==null||token.length()<32)throw new BusinessException("AI_CONFIG","AI 未启用或内部认证未配置（Token 至少 32 字符）。",HttpStatus.SERVICE_UNAVAILABLE);}
    public String token(){return token;}
    public void internal(String authorization){
        configured();
        if(authorization==null||!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),authorization.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException("AI_FORBIDDEN","内部认证失败。",HttpStatus.FORBIDDEN);
    }
    public synchronized String issue(HttpServletRequest request,AiDtos.Scope scope){
        var session=request.getSession(false);
        if(!authenticated(session))throw new BusinessException("AUTH_REQUIRED","请登录后使用 AI。",HttpStatus.UNAUTHORIZED);
        configured();long now=System.currentTimeMillis();
        permits.values().removeIf(p->p.expires<now);
        while(!requests.isEmpty()&&requests.peekFirst()<now-60000)requests.removeFirst();
        // Current application has one shared admin identity: a global budget also prevents session rotation bypass.
        if(requests.size()>=10||permits.size()>=2)throw new BusinessException("AI_RATE_LIMIT","AI 请求较多，请稍后再试。",HttpStatus.TOO_MANY_REQUESTS);
        requests.addLast(now);String id=UUID.randomUUID().toString();permits.put(id,new Permit(scope,session,now+90000,new int[]{0}));return id;
    }
    public synchronized AiDtos.Scope resolve(String ticket){
        var p=permits.get(ticket);
        if(p==null||p.expires<System.currentTimeMillis()||!authenticated(p.session))throw new BusinessException("AI_SCOPE_EXPIRED","查询范围已失效，请重新提问。",HttpStatus.FORBIDDEN);
        if(++p.calls[0]>6)throw new BusinessException("AI_TOOL_LIMIT","查询次数超过上限，请缩小问题范围。",HttpStatus.TOO_MANY_REQUESTS);
        return p.scope;
    }
    public synchronized void revoke(String ticket){permits.remove(ticket);}
    private boolean authenticated(HttpSession s){try{return s!=null&&Boolean.TRUE.equals(s.getAttribute(AuthController.AUTH_SESSION_KEY));}catch(IllegalStateException ex){return false;}}
}
