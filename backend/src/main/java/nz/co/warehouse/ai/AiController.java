package nz.co.warehouse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import nz.co.warehouse.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@RestController
public class AiController {
    private final AiAccess access;
    private final RestClient client;
    public AiController(AiAccess access,@Value("${AI_SERVICE_URL:http://127.0.0.1:8000}") String url){
        this.access=access;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(80));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @PostMapping("/api/ai/chat")
    public ResponseEntity<JsonNode> chat(@Valid @RequestBody AiDtos.Chat body,HttpServletRequest request){
        String ticket=access.issue(request,body.scope());
        try{
            return client.post().uri("/chat").header("Authorization","Bearer "+access.token()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message",body.message(),"scope",body.scope(),"ticket",ticket)).retrieve().toEntity(JsonNode.class);
        }catch(RestClientResponseException ex){
            HttpStatus status=switch(ex.getStatusCode().value()){case 429->HttpStatus.TOO_MANY_REQUESTS;case 504->HttpStatus.GATEWAY_TIMEOUT;default->HttpStatus.SERVICE_UNAVAILABLE;};
            // Only relay the fixed error contract, never raw upstream bodies or credentials.
            String message=status==HttpStatus.TOO_MANY_REQUESTS?"AI 配额或请求频率受限，请稍后重试。":status==HttpStatus.GATEWAY_TIMEOUT?"AI 请求超时，请缩小范围后重试。":"AI 服务不可用，请检查 Python 服务、模型权限和密钥配置。";
            try {
                String code=new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBodyAsString()).path("code").asText();
                message=switch(code){
                    case "GEMINI_KEY_MISSING"->"尚未配置 Gemini API Key，请在 Python AI 服务环境中填写。";
                    case "GEMINI_PERMISSION"->"Gemini 密钥或模型访问权限无效，请检查 AI Studio 配置。";
                    case "AI_DISABLED"->"AI 功能已关闭，普通仓库功能仍可使用。";
                    case "TOOL_SCOPE","TOOL_SIZE","CONTEXT_LIMIT","TOKEN_LIMIT","TOOL_LIMIT","ROUND_LIMIT"->"查询范围过大或超过调用上限，请指定物料、批次或缩小货位范围。";
                    case "TOOL_AUTH","INTERNAL_CONFIG"->"AI 内部认证未配置或已失效，请检查两端的内部 Token 并重新提问。";
                    case "INVALID_ARGUMENTS","INVALID_TOOL","MODEL_EMPTY"->"AI 未生成有效查询，请明确物料、批号或位置后重试。";
                    default->message;
                };
            }catch(java.io.IOException ignored){/* Keep the sanitized fallback. */}
            throw new BusinessException("AI_UNAVAILABLE",message,status);
        }catch(RestClientException ex){throw new BusinessException("AI_UNAVAILABLE","无法连接 AI 服务，普通仓库功能仍可使用。",HttpStatus.SERVICE_UNAVAILABLE);}
        finally{access.revoke(ticket);}
    }
}
