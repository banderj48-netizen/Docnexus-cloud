package com.xyf.docnexus.user.Mapper;

import com.xyf.docnexus.user.entity.UserSession;
import com.xyf.docnexus.user.entity.UserSessionLogoutParam;
import com.xyf.docnexus.user.entity.UserSessionQueryParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionMapper {
    Integer insertSession(UserSession session);

    UserSession selectActiveBySessionId(UserSessionQueryParam param);

    UserSession selectActiveByAccessJti(UserSessionQueryParam param);

    UserSession selectActiveByUserIdAndDeviceId(UserSessionQueryParam param);

    /**
     * 查询同一用户、同一客户端 IP 下仍然有效的会话。
     *
     * <p>用于同设备接管的历史兼容：旧版本 deviceId 可能把 Edge 和 Chrome 算成两个设备，
     * 登录时会先按新 deviceId 精确查找，找不到时再按 IP + 操作系统族兜底接管。</p>
     */
    List<UserSession> selectActiveByUserIdAndClientIp(UserSessionQueryParam param);

    List<UserSession> selectActiveByUserId(UserSessionQueryParam param);

    List<UserSession> selectExpiredActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Long countActiveByUserId(UserSessionQueryParam param);

    List<UserSession> selectActivePageByUserId(UserSessionQueryParam param);

    LocalDateTime selectLatestLoginAtByUserId(UserSessionQueryParam param);

    Integer rotateSessionToken(UserSession session);

    Integer updateTakeoverSessionToken(UserSession session);

    Integer logoutByAccessJti(UserSessionLogoutParam param);

    Integer logoutBySessionId(UserSessionLogoutParam param);

    Integer logoutAllByUserId(UserSessionLogoutParam param);

    Integer expireActiveByUserId(UserSessionLogoutParam param);

    /**
     * 把指定会话标记为 EXPIRED。
     *
     * <p>该方法由 RocketMQ Consumer 或 MQ 失败兜底逻辑调用。
     * SQL 必须幂等，重复调用时 rows=0 也属于正常情况。</p>
     */
    Integer expireBySessionId(UserSessionLogoutParam param);

    Integer markOfflineBySessionId(UserSessionLogoutParam param);

}
