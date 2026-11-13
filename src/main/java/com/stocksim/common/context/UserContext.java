package com.stocksim.common.context;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static Integer role() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.role();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record LoginUser(Long userId, String username, Integer role) {
    }
}