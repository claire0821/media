package com.zc.media.thread;

import cn.hutool.core.date.DateTime;
import com.zc.media.model.ResponseInfo;
import com.zc.media.model.StreamInfo;
import com.zc.media.netty.Server;
import com.zc.media.rtsp.RtspConverter;
import com.zc.media.rtsp.RtspState;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.CharsetUtil;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:25
 **/


@Service
public class AsyncServiceImpl implements AsyncService{
    @Autowired
    Server server;

    //rtsp
    public static Map<String, RtspConverter> listRtsp = new HashMap<>();
    Map<String, List<HttpServletResponse>> listClient = new HashMap<>();
    //    public static Map<String, ResponseInfo> responseInfos = new HashMap<>();
    public static Map<ChannelId, ResponseInfo> listRes = new HashMap<>();

    public static List<StreamInfo> listStream = new ArrayList<>();
    @Override
    public String open(String url, HttpServletResponse response){
        //设置响应头
        response.setContentType("video/x-flv");
        response.setHeader("Connection", "keep-alive");
        response.setStatus(HttpServletResponse.SC_OK);
        //写出缓冲信息，并清空
        try {
            response.flushBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        }

        RtspConverter rtsp = listRtsp.get(url);
        if(rtsp == null) {
            rtsp = new RtspConverter(url);
            listRtsp.put(url,rtsp);
            ExecutorConfig.executor.execute(rtsp);
        }

        ResponseInfo responseInfo = new ResponseInfo();
        responseInfo.setState(ResponseInfo.OPEN);
        responseInfo.setUrl(url);
//        responseInfo.setResponse(response);
        String key = UUID.randomUUID().toString();

        return key;
    }

    //添加客户端
    public static void addRes(ResponseInfo responseInfo) {
        RtspConverter rtsp = listRtsp.get(responseInfo.getUrl());
        //判读流是否已经存在
        if(rtsp == null) {
            rtsp = new RtspConverter(responseInfo.getUrl());
            listRtsp.put(responseInfo.getUrl(),rtsp);
            ExecutorConfig.executor.execute(rtsp);
        }
        responseInfo.setStartTime(new DateTime());
        responseInfo.setLastTime(new DateTime());
        ChannelId id = responseInfo.getResponse().channel().id();
        listRes.put(id,responseInfo);

//        responseInfo.getResponse().executor().schedule(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    if(responseInfo.getResponse().channel().isWritable()) {
//                        responseInfo.getResponse().writeAndFlush(new PingWebSocketFrame());//定时发送ping
//                    }
//                    try {
//                        Thread.sleep(1000 * 10);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//            }
//        }, 10, TimeUnit.SECONDS);
    }

    //发送解码后的流数据
    @Override
    @Async("asyncServiceExecutor")
    public void send() {
        Thread.currentThread().setName("async-service-sendStream");
        while (true) {
//            System.out.println("send stream");
            try{
                if(listStream.size() <= 0) {
                    Thread.sleep(100);
                    continue;
                }
                StreamInfo streamInfo = listStream.get(0);
                if(streamInfo == null) continue;
                listStream.remove(0);

                //查找所有相同观看流地址的客户端
                List<ResponseInfo> list = new ArrayList<>();
                for (Map.Entry<ChannelId, ResponseInfo> entry : listRes.entrySet()) {
                    ResponseInfo responseInfo = entry.getValue();
                    if(responseInfo.getUrl().equals(streamInfo.getUrl())) {
                        list.add(responseInfo);
                    }
                }

                //没有客户端观看，关闭流
                if(list.size() <= 0) {
                    RtspConverter rtspConverter = listRtsp.get(streamInfo.getUrl());
                    if(rtspConverter != null) {
                        rtspConverter.exit = true;
                        listRtsp.remove(streamInfo.getUrl());
                    }
                }

                if(streamInfo.getState() == RtspState.ERROR) {//流打开失败
                    for (ResponseInfo responseInfo : list) {
                        if(responseInfo.getUrl().equals(streamInfo.getUrl())) {
                            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                                    Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
                            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                            responseInfo.getResponse().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                            listRes.remove(responseInfo.getResponse().channel().id());
                        }
                    }
                } else if(streamInfo.getState() == RtspState.CLOSE){//流关闭
                    for (ResponseInfo responseInfo : list) {
                        if(responseInfo.getUrl().equals(streamInfo.getUrl())) {
                            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                                    Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
                            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                            responseInfo.getResponse().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                            listRes.remove(responseInfo.getResponse().channel().id());
                        }
                    }
                } else if(streamInfo.getState() == RtspState.RUN) {
                    for(ResponseInfo responseInfo : list) {
                        if(responseInfo.getUrl().equals(streamInfo.getUrl())) {
                            if(!responseInfo.isSendHeader()) {//先发送头
                                if(responseInfo.getIsHttp() == ResponseInfo.HTTP) {
                                    responseInfo.getResponse().writeAndFlush(Unpooled.copiedBuffer(streamInfo.getHeaders()));
                                } else {
                                    responseInfo.getResponse().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(streamInfo.getHeaders())));
                                }
                                responseInfo.setSendHeader(true);

                                try(OutputStream out = new BufferedOutputStream(new FileOutputStream("1111.flv", false))){
                                    out.write(streamInfo.getHeaders());
                                } catch (Exception ex) {
                                    System.out.println(ex);
                                }

                            }
                            if(streamInfo.getOutData() != null) {
                                if(responseInfo.getIsHttp() == ResponseInfo.HTTP) {
                                    responseInfo.getResponse().writeAndFlush(Unpooled.copiedBuffer(streamInfo.getOutData()));
                                    System.out.println("send--------------------");
                                } else {
                                    responseInfo.getResponse().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(streamInfo.getOutData())));
                                    System.out.println("send--------------------");
                                }
                                try(OutputStream out = new BufferedOutputStream(new FileOutputStream("1111.flv", true))){
                                    out.write(streamInfo.getOutData());
                                } catch (Exception ex) {
                                    System.out.println(ex);
                                }
                            }
                        }
                    }
                }

                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

    }

    //开启netty服务器
    @Override
    @Async("asyncServiceExecutor")
    public void startServer(int port) {
        Thread.currentThread().setName("async-service-startServer");
        server.start(port);
    }

    //定时清除连接
    @Override
    @Async("asyncServiceExecutor")
    public void clearClient(long sleep, long timeOut) {

    }

    @Override
    public void openTest(String url, ChannelHandlerContext response) {
//        //设置响应头
//        response.setContentType("video/x-flv");
//        response.setHeader("Connection", "keep-alive");
//        response.setStatus(HttpServletResponse.SC_OK);
//        //写出缓冲信息，并清空
//        try {
//            response.flushBuffer();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        RtspConverter rtsp = new RtspConverter(url);

        boolean b = rtsp.startGrabber();
        if(b == false) {
            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                    Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        System.out.println("ready to recorder");
//        response.writeAndFlush(Unpooled.copiedBuffer(rtsp.getHeader()));
        response.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(rtsp.getHeader())));
        AVPacket k = null;
        while (rtsp.getRtspState() != RtspState.CLOSE) {
            try{
                k = rtsp.grabber.grabPacket();
                if(k == null || k.size() == 0 || k.data() == null) {
                    System.out.println("AVPacket null");
                    continue;
                }
                // 过滤音频
                if (k.stream_index() == 1) {
                    av_packet_unref(k);
                    continue;
                }
                rtsp.recorder.recordPacket(k);//转换器转换

                if(rtsp.stream.size() > 0) {
//                    response.writeAndFlush(Unpooled.copiedBuffer(rtsp.stream.toByteArray()));
                    response.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(rtsp.stream.toByteArray())));
                }
                rtsp.stream.reset();
                rtsp.setRtspState(RtspState.RUN);
            } catch (FrameGrabber.Exception | FrameRecorder.Exception e) {
                e.printStackTrace();
            }

        }

    }

}
