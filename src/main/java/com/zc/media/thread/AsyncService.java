package com.zc.media.thread;
import com.zc.media.model.ResponseInfo;
import io.netty.channel.ChannelHandlerContext;
/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:25
 **/


import javax.servlet.http.HttpServletResponse;

public interface AsyncService {
    //rtsp
    //开启流服务器
    void startServer(int port);
    //发送流数据
    void send();

    String open(String url, HttpServletResponse response);

    void clearClient(long sleep, long timeOut);//定时清除连接

    void openTest(String url, ChannelHandlerContext response);
}
