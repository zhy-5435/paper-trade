package com.stocksim.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stocksim.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 操作审计落库（Q9）。插入在业务事务之外（控制器层 @AfterReturning / 登录无事务，独立连接自动提交），
 * 失败仅告警日志、不得标记业务回滚。detail=请求摘要 JSON（密码脱敏），ip=remoteAddr。
 */
@Service
public class OperationAuditService {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditService.class);

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationAuditService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /** admin 写操作审计：操作人取 UserContext。 */
    public void record(OperationType type, String targetType, String targetId, Object[] args) {
        UserContext.LoginUser user = UserContext.get();
        Long operatorId = user == null ? null : user.userId();
        String operatorName = user == null ? null : user.username();
        insert(type, operatorId, operatorName, targetType, targetId, args);
    }

    /** 登录审计：操作人=登录用户（登录接口不走 JWT 拦截器，UserContext 尚未设置）。 */
    public void recordLogin(Long operatorId, String operatorName, Object[] args) {
        insert(OperationType.LOGIN, operatorId, operatorName, "user",
                operatorId == null ? null : String.valueOf(operatorId), args);
    }

    private void insert(OperationType type, Long operatorId, String operatorName,
                        String targetType, String targetId, Object[] args) {
        try {
            OperationLog entity = new OperationLog();
            entity.setOperatorId(operatorId);
            entity.setOperatorName(operatorName);
            entity.setOperationType(type.getCode());
            entity.setTargetType(targetType);
            entity.setTargetId(targetId);
            entity.setDetail(buildDetail(args));
            entity.setIp(currentIp());
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("操作审计落库失败，不影响业务, type={}, operatorId={}", type, operatorId, e);
        }
    }

    private String buildDetail(Object[] args) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            if (args != null) {
                for (Object arg : args) {
                    if (arg == null || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                        continue;
                    }
                    JsonNode node = objectMapper.valueToTree(arg);
                    maskPassword(node);
                    arr.add(node);
                }
            }
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void maskPassword(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> toMask = new ArrayList<>();
            Iterator<String> names = obj.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (name.equalsIgnoreCase("password")) {
                    toMask.add(name);
                } else {
                    maskPassword(obj.get(name));
                }
            }
            for (String name : toMask) {
                obj.put(name, "***");
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                maskPassword(child);
            }
        }
    }

    private String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return null;
    }
}