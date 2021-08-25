package com.zc.media.util;

public enum ResultCode {

    /* 成功状态码*/
    SUCCESS(1,"成功"),
    /* 失败状态码*/
    ERROR(2,"失败"),
    /* 参数错误码 1001-1999*/
    PARAM_ERROR(1001,"参数未定义错误"),
    E_90003(1002, "缺少必填参数"),
    LOGINEXPIRED(1003, "登陆已过期,请重新登陆"),
    LOGINERROR(1004, "请重新登录"),
    /* 用户状态码 2001-2999*/
    USER_ERROR(2001,"用户未定义错误"),
    USER_NOT_EXIST(2002,"用户不存在"),
    USER_PSW_ERROR(2003,"密码错误"),
    USER_MAILERROR(2004,"邮件发送错误"),
    USER_ROLE_DELLET_ERROR(2005,"角色删除失败，尚有用户属于此角色"),
    USER_IS_EXIST(2006,"用户已存在"),
    USER_DELETE_EOORO(2007,"用户删除失败"),
    /* 设备错误码 3001-3999*/
    DEVICE_NOT_DEFIND(3001,"设备未定义错误"),


    UNDEFINED_ERROR(4000,"未定义错误");
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private Integer code;
    private String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
