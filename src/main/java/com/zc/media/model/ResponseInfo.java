package com.zc.media.model;
import cn.hutool.core.date.DateTime;
import io.netty.channel.ChannelHandlerContext;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:24
 **/

public class ResponseInfo {
    public static final int OPEN = 1;
    public static final int RUN = 2;
    public static final int CLOSE = 3;

    public static final int HTTP = 1;
    public static final int WS = 2;
    //    public String key;
    private String url;
    private int state;
    //    private HttpServletResponse response;
    private ChannelHandlerContext response;
    //    private ChannelHandlerContext wsRes;
    private boolean sendHeader;//是否已发送头
    private int isHttp;
    private DateTime lastTime;//最后更新时间
    private boolean overtime;//是否超时标志位
    //开始拉流时间
    private DateTime startTime;
//    public String getKey() {
//        return key;
//    }
//
//    public void setKey(String key) {
//        this.key = key;
//    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

//    public HttpServletResponse getResponse() {
//        return response;
//    }
//
//    public void setResponse(HttpServletResponse response) {
//        this.response = response;
//    }

    public ChannelHandlerContext getResponse() {
        return response;
    }

    public void setResponse(ChannelHandlerContext response) {
        this.response = response;
    }

//    public ChannelHandlerContext getWsRes() {
//        return wsRes;
//    }
//
//    public void setWsRes(ChannelHandlerContext wsRes) {
//        this.wsRes = wsRes;
//    }

    public boolean isSendHeader() {
        return sendHeader;
    }

    public void setSendHeader(boolean sendHeader) {
        this.sendHeader = sendHeader;
    }

    public int getIsHttp() {
        return isHttp;
    }

    public void setIsHttp(int isHttp) {
        this.isHttp = isHttp;
    }

    public DateTime getLastTime() {
        return lastTime;
    }

    public void setLastTime(DateTime lastTime) {
        this.lastTime = lastTime;
    }

    public boolean isOvertime() {
        return overtime;
    }

    public void setOvertime(boolean overtime) {
        this.overtime = overtime;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }
}
