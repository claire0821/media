package com.zc.media.util;

import com.alibaba.fastjson.JSONObject;

import java.io.Serializable;

/**
 * @program: media
 * @description: 返回结果
 * @author: Claire
 * @create: 2021-07-12 10:28
 **/
public class ResultUtil implements Serializable {

    private int code;
    private String msg;
    private Object data;

    public ResultUtil() {

    }


    protected ResultUtil(int code, String msg, String data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    protected ResultUtil(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    protected ResultUtil(ResultCode resultCode, Object data) {
        this.code = resultCode.getCode();
        this.msg = resultCode.getMessage();
        this.data = data;
    }

    public static JSONObject success() {
        JSONObject json = new JSONObject();
        json.put("code",ResultCode.SUCCESS.getCode());
        json.put("msg",ResultCode.SUCCESS.getMessage());
        return json;
    }

    public static JSONObject success(String msg) {
        JSONObject json = new JSONObject();
        json.put("code",ResultCode.SUCCESS.getCode());
        json.put("msg",msg);
        return json;
    }

    public static JSONObject success(String msg,Object data) {
        JSONObject json = new JSONObject();
        json.put("code", ResultCode.SUCCESS.getCode());
        json.put("msg", msg);
        json.put("data", data);
        return json;
    }

    public static JSONObject success(Object data) {
        JSONObject json = new JSONObject();
        json.put("code",ResultCode.SUCCESS.getCode());
        json.put("msg",ResultCode.SUCCESS.getMessage());
        json.put("data",data);
        return json;
    }

    public static JSONObject errorDevice() {
        JSONObject json = new JSONObject();
        json.put("code",ResultCode.DEVICE_NOT_DEFIND.getCode());
        json.put("msg",ResultCode.DEVICE_NOT_DEFIND.getMessage());
        return json;
    }

    public static JSONObject error(String msg) {
        JSONObject json = new JSONObject();
        json.put("code",ResultCode.ERROR.getCode());
        json.put("msg",msg);
        return json;
    }

    public static JSONObject error(ResultCode resultCode) {
        JSONObject json = new JSONObject();
        json.put("code",resultCode.getCode());
        json.put("msg",resultCode.getMessage());
        return json;
    }

    public static JSONObject error(ResultCode resultCode, Object data) {
        JSONObject json = new JSONObject();
        json.put("code",resultCode.getCode());
        json.put("msg",resultCode.getMessage());
        json.put("data",data);
        return json;
    }

    public static JSONObject error(Integer resultCode, String msg) {
        JSONObject json = new JSONObject();
        json.put("code",resultCode);
        json.put("msg",msg);
        return json;
    }
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("code",this.code);
        json.put("msg",this.msg);
        json.put("data",this.data);
        return json.toJSONString();
    }
}
