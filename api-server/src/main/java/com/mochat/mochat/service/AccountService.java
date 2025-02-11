package com.mochat.mochat.service;

import com.mochat.mochat.common.em.RespErrCodeEnum;
import com.mochat.mochat.common.util.JwtUtil;
import com.mochat.mochat.common.util.RedisUtil;
import com.mochat.mochat.config.ex.CommonException;
import com.mochat.mochat.config.ex.ParamException;
import com.mochat.mochat.dao.entity.UserEntity;
import com.mochat.mochat.dao.entity.WorkEmployeeEntity;
import com.mochat.mochat.service.impl.ISubSystemService;
import com.mochat.mochat.service.emp.IWorkEmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author zhaojinjian
 * @ClassName AccountService.java
 * @Description TODO
 * @createTime 2020/12/26 10:42
 */
@Service
public class AccountService {

    private static final String TYPE_CORP_ID = "corpId";
    private static final String TYPE_EMPLOYEE_ID = "employeeId";
    private static final String REDIS_PREFIX_CORP = "mc:user.%d" + ".corp";
    private static final String REDIS_PREFIX_EMP = "mc:user.%d" + ".emp";

    /**
     * 用户对应租户 id
     */
    private static final String REDIS_PREFIX_TENANT = "mc:user.%d" + ".tenant";

    private static IWorkEmployeeService employeeService;

    private static ISubSystemService subSystemService;

    @Autowired
    public void setEmployeeService(IWorkEmployeeService employeeService) {
        AccountService.employeeService = employeeService;
    }

    @Autowired
    public void setSubSystemService(ISubSystemService subSystemService) {
        AccountService.subSystemService = subSystemService;
    }

    /**
     * @description 获取当前企业的id
     * @author zhaojinjian
     * @createTime 2020/12/25 15:14
     */
    public static Integer getCorpId() {
        int userId = getUserId();
        Integer corpId = (Integer) RedisUtil.get(String.format(REDIS_PREFIX_CORP, userId));
        if (corpId == null || corpId < 1) {
            corpId = getNewId(userId, TYPE_CORP_ID);
        }
        return corpId;
    }

    /**
     * @description 获取当前企业成员的id
     * @author zhaojinjian
     * @createTime 2020/12/25 15:14
     */
    public static Integer getEmpId() {
        int userId = getUserId();
        Integer empId = (Integer) RedisUtil.get(String.format(REDIS_PREFIX_EMP, userId));
        if (empId == null || empId < 1) {
            empId = getNewId(userId, TYPE_EMPLOYEE_ID);
        }
        return empId;
    }

    /**
     * @description 获取当前子账户Id
     * @author zhaojinjian
     * @createTime 2020/12/28 15:39
     */
    public static Integer getUserId() {
        Integer userId = (Integer) JwtUtil.parseJWT(getToken()).get("userId");
        if (userId == null || userId < 1) {
            throw new CommonException(RespErrCodeEnum.AUTH_TOKEN_INVALID);
        }
        return userId;
    }

    /**
     * @description 获取当前企业id和成员id
     * @author zhaojinjian
     * @createTime 2020/12/25 18:13
     */
    public static Map<String, Integer> getCorpIdAndEmpIdMap() {
        Map<String, Integer> resultMap = new HashMap<>(3);
        resultMap.put("userId", getUserId());
        resultMap.put("corpId", getCorpId());
        resultMap.put("empId", getEmpId());
        return resultMap;
    }

    /**
     * @description: 判断是否登出
     * @return:
     * @author: Huayu
     * @time: 2020/12/29 18:27
     */
    public static String isLoginOut() {
        Map<String, Integer> resultMap = new HashMap<>();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            String token = request.getHeader("Authorization");
            token = token.substring(token.indexOf(" ") + 1);
            if (token != null && !token.isEmpty()) {
                String value = (String) RedisUtil.get("mc:user.token" + token);
                if (value == null) {
                    return "0";
                }
            }
        }
        return "1";
    }

    public static String getToken() {
        String token = null;
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            token = request.getHeader("Authorization");
        }
        if (token == null || token.isEmpty()) {
            throw new CommonException(RespErrCodeEnum.AUTH_TOKEN_INVALID);
        }
        return token;
    }

    private static int getNewId(int userId, String type) {
        List<WorkEmployeeEntity> employeeList = employeeService.getWorkEmployeeByLogUserId(userId);
        if (employeeList == null || employeeList.isEmpty()) {
            throw new ParamException("未找到所属企业信息");
        }

        WorkEmployeeEntity entity = employeeList.get(0);
        RedisUtil.set(String.format(REDIS_PREFIX_CORP, userId), entity.getCorpId());
        RedisUtil.set(String.format(REDIS_PREFIX_EMP, userId), entity.getId());
        switch (type) {
            case TYPE_CORP_ID:
                return entity.getCorpId();
            case TYPE_EMPLOYEE_ID:
                return entity.getId();
            default:
                return 0;
        }
    }

    public static void updateCorpIdAndEmployeeId(int userId, int copId, int employeeId) {
        RedisUtil.set(String.format(REDIS_PREFIX_CORP, userId), copId);
        RedisUtil.set(String.format(REDIS_PREFIX_EMP, userId), employeeId);
    }

    public static void updateCorpId(int copId) {
        RedisUtil.set(String.format(REDIS_PREFIX_CORP, getUserId()), copId);
    }

    /**
     * @author: yangpengwei
     * @time: 2021/3/12 2:52 下午
     * @description 获取用户的租户 id
     */
    public static int getTenantId() {
        int userId = getUserId();
        Integer tenantId = (Integer) RedisUtil.get(String.format(REDIS_PREFIX_TENANT, userId));
        if (tenantId == null || tenantId < 1) {
            tenantId = getNewTenantId(userId);
            RedisUtil.set(String.format(REDIS_PREFIX_TENANT, userId), tenantId);
        }
        return tenantId;
    }

    private static int getNewTenantId(int userId) {
        UserEntity userEntity = subSystemService.getById(userId);
        if (Objects.isNull(userEntity)) {
            throw new CommonException("用户不存在");
        }
        RedisUtil.set(String.format(REDIS_PREFIX_TENANT, userId), userEntity.getTenantId());
        return userEntity.getTenantId();
    }

}
