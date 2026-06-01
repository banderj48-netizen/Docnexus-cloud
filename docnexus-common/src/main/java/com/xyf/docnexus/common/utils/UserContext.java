package com.xyf.docnexus.common.utils;


public class UserContext {
    private static final ThreadLocal<Long> tl = new ThreadLocal<>();

    // 保存当前用户信息到 ThreadLocal
    public static void setUser(Long userId) {
        tl.set(userId);
    }

    // 获取当前用户信息
    public static Long getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }

}
