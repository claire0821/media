package com.zc.media.thread;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.zc.media.model.ResponseInfo;
import com.zc.media.rtsp.RtspConverter;
import com.zc.media.rtsp.RtspState;
import com.zc.media.util.RedisUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.util.CharsetUtil;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @program: media
 * @description: rtsp转发flv线程
 * @author: Claire
 * @create: 2021-07-01 16:40
 **/
public class ThreadRtsp extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(ThreadRtsp.class);
//    @Resource
//    private RedisUtil redisUtil;

    public Date startTime;//开始转码时间
    public Date lastTime;//最后观看时间
    public List<ResponseInfo> responseInfoList = new ArrayList<>();
    public RtspConverter rtspConverter = null;
    String url = "";
    public ThreadRtsp(String url) {
        this.url = url;
        this.startTime = new Date();
    }

    int write = 0;
    @Override
    public void run() {
        rtspConverter = new RtspConverter(url);

        boolean b = rtspConverter.startGrabber();
        if(b == false) {
            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                    Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            for(ResponseInfo responseInfo : responseInfoList) {
                responseInfo.getResponse().writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
            }
            System.out.println("打开流失败" + url);
            return;
        }
        System.out.println("打开流成功" + url);
        AVPacket k = null;
        Frame frame = null;
        while (rtspConverter.getRtspState() != RtspState.CLOSE) {
            try{
                if(true) {
                    k = rtspConverter.grabber.grabPacket();
                    if(k == null || k.size() == 0 || k.data() == null) {
                        continue;
                    }
                    // 过滤音频
                    if (k.stream_index() == 1) {
                        av_packet_unref(k);
                        continue;
                    }
                    rtspConverter.recorder.recordPacket(k);//转换器转换
                } else {
                    frame = rtspConverter.grabber.grabFrame();
                    rtspConverter.recorder.record(frame);
                }

                if(rtspConverter.stream.size() > 0 && responseInfoList.size() > 0) {
                    this.lastTime = new Date();//最后更新时间
                    for(ResponseInfo responseInfo : responseInfoList) {
                        if(responseInfo.getResponse().isRemoved()) {//连接断开
                            responseInfoList.remove(responseInfo);
                            System.out.println(responseInfo.getResponse().channel().id());
                        }

                        if(responseInfo.getIsHttp() == ResponseInfo.HTTP) {
                            if(!responseInfo.isSendHeader()) {
                                responseInfo.getResponse().writeAndFlush(Unpooled.copiedBuffer(rtspConverter.getHeader()));
                                responseInfo.setSendHeader(true);
                            }
                            responseInfo.getResponse().writeAndFlush(Unpooled.copiedBuffer(rtspConverter.stream.toByteArray()));
                        } else {
                            if(!responseInfo.isSendHeader()) {
                                responseInfo.getResponse().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(rtspConverter.getHeader())));
                                responseInfo.setSendHeader(true);
                            }
                            responseInfo.getResponse().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(rtspConverter.stream.toByteArray())));
                        }
                    }
                }
                rtspConverter.stream.reset();
                rtspConverter.setRtspState(RtspState.RUN);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (FrameGrabber.Exception | FrameRecorder.Exception e) {
                e.printStackTrace();
                continue;
            } catch (Exception ex) {
                continue;
            }
        }

        if(rtspConverter != null) {
            rtspConverter.close();
        }
        if(k != null) {
            k.close();
        }

        logger.warn("无人观看，断开视频流" + url);
    }

    public void add(ResponseInfo responseInfo) {
        responseInfoList.add(responseInfo);
    }

    public void sendPing(String id) {
        Optional<ResponseInfo> responseInfo = responseInfoList.stream().findFirst().filter(item -> item.getResponse().channel().id().toString().equals(id));
        if(responseInfo != null) {
            responseInfo.get().getResponse().writeAndFlush(new PingWebSocketFrame());
        }
    }


}
