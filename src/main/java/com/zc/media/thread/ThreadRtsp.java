package com.zc.media.thread;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.annotation.JSONField;
import com.zc.media.model.BeanContext;
import com.zc.media.model.RectInfo;
import com.zc.media.model.ResponseInfo;
import com.zc.media.netty.ServerHandler;
import com.zc.media.rtsp.RtspConverter;
import com.zc.media.rtsp.RtspState;
import com.zc.media.util.RedisUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.util.CharsetUtil;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @program: media
 * @description: rtsp转发flv线程
 * @author: Claire
 * @create: 2021-07-01 16:40
 **/
public class ThreadRtsp extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(ThreadRtsp.class);

    @Autowired
    RedisUtil redisUtil;

//    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    public Date startTime;//开始转码时间
//    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    public Date lastTime;//最后观看时间
    public List<ResponseInfo> responseInfoList = new ArrayList<>();
    public RtspConverter rtspConverter = null;
    String url = "";
    int error;//错误次数

    public boolean bCapture = false;
    Java2DFrameConverter converter = new Java2DFrameConverter();
    //叠加矩形
    public List<RectInfo> rectInfoList;

    //是否osd叠加
    boolean osd;
    public ThreadRtsp(String url) {
        this.rectInfoList = new ArrayList<>();
        RectInfo rectInfo = new RectInfo();
        rectInfo.setX(10);
        rectInfo.setY(10);
        rectInfo.setWidth(100);
        rectInfo.setHeight(100);
        rectInfo.setThickness(5);
        rectInfo.setColor(Color.pink);
        this.rectInfoList.add(rectInfo);
        rectInfo = new RectInfo();
        rectInfo.setX(100);
        rectInfo.setY(100);
        rectInfo.setWidth(300);
        rectInfo.setHeight(300);
        rectInfo.setThickness(5);
        rectInfo.setColor(Color.pink);
        this.rectInfoList.add(rectInfo);

        this.redisUtil= BeanContext.getApplicationContext().getBean(RedisUtil.class);
        this.url = url;
        this.startTime = new Date();
        this.error = 0;
        //初始化osd不叠加
        this.osd = false;
        this.start();
    }

    int write = 0;
    @Override
    public void run() {
        rtspConverter = new RtspConverter(url);
        updateRedis();
        boolean b = rtspConverter.startGrabber();
        updateRedis();
        if(b == false) {
            //视频流打开失败
            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                    Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            for(ResponseInfo responseInfo : responseInfoList) {
//                final ChannelFuture f = responseInfo.getResponse().writeAndFlush(fullHttpResponse);
//                f.addListener(new ChannelFutureListener() {
//                    @Override
//                    public void operationComplete(ChannelFuture future) {
//                        responseInfo.getResponse().close();
//                    }
//                });
                responseInfo.getResponse().writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
            }
            logger.warn("打开流失败" + url);
            return;
        }
        logger.info("打开流成功" + url);
        updateRedis();
//        AVPacket：存储压缩数据（视频对应H.264等码流数据，音频对应AAC/MP3等码流数据）
//        AVFrame：存储非压缩的数据（视频对应RGB/YUV像素数据，音频对应PCM采样数据）
        AVPacket k = null;
        Frame frame = null;

        while (rtspConverter.getRtspState() != RtspState.CLOSE && rtspConverter.getRtspState() != RtspState.CLEAR) {
            try{
                if(!this.osd) {
                    k = rtspConverter.grabber.grabPacket();
                    if(k == null || k.size() == 0 || k.data() == null) {
                        continue;
                    }
                    // 过滤音频
                    if(rtspConverter.videoStream != k.stream_index()) {
                        av_packet_unref(k);
                        continue;
                    }
                    //转换器转换
                    rtspConverter.recorder.recordPacket(k);
                } else {
                    //只拿视频
                    frame = rtspConverter.grabber.grabImage();
                    if(frame != null) {
                        frame = drawRect(frame);
                        if(frame != null) {
                            frame = drawPoly(frame);
                            if(frame != null) {
                                rtspConverter.recorder.record(frame);
                            }
                        }
                    }
                }

                if(rtspConverter.stream.size() > 0 && responseInfoList.size() > 0) {
                    //最后更新时间
                    this.lastTime = new Date();
                    updateRedis();

                    for(ResponseInfo responseInfo : responseInfoList) {
                        //连接断开
                        if(responseInfo.getResponse().isRemoved()) {
                            responseInfoList.remove(responseInfo);
                        }

                        if(responseInfo.getIsHttp() == ResponseInfo.HTTP) {
                            if(!responseInfo.isSendHeader()) {
                                HttpResponse rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                rsp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                                        .set(HttpHeaderNames.CONTENT_TYPE, "video/x-flv").set(HttpHeaderNames.ACCEPT_RANGES, "bytes")
                                        .set(HttpHeaderNames.PRAGMA, "no-cache").set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                                        .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                                responseInfo.getResponse().writeAndFlush(rsp);
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
                    Thread.sleep(10);
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

        updateRedis();
        //发送消息关闭client
        for(ResponseInfo responseInfo : responseInfoList) {
            if(!responseInfo.getResponse().isRemoved()) {
                if(responseInfo.getIsHttp() == ResponseInfo.HTTP) {
                    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                            Unpooled.copiedBuffer("视频流异常: " + HttpResponseStatus.BAD_REQUEST + "\r\n", CharsetUtil.UTF_8));
                    fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                    responseInfo.getResponse().writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
                } else {
                    responseInfo.getResponse().writeAndFlush(new CloseWebSocketFrame());
                }
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

    public Frame drawRect(Frame frame) {
        Graphics2D graphics = null;
        BufferedImage bufferedImage = null;
        try{
            bufferedImage = converter.getBufferedImage(frame);
            graphics = bufferedImage.createGraphics();

            for(RectInfo rectInfo : rectInfoList) {
                graphics.setColor(rectInfo.getColor());

                graphics.drawRect(rectInfo.getX(), rectInfo.getY(), rectInfo.getWidth(), rectInfo.getHeight());
            }
        } catch (Exception ex) {

        } finally {
            if(graphics != null) {
                graphics.dispose();
            }
        }
        // 视频帧赋值，写入输出流
        return converter.getFrame(bufferedImage);
    }

    public Frame drawPoly(Frame frame) {
        Graphics2D graphics = null;
        BufferedImage bufferedImage = null;
        try{
            bufferedImage = converter.getBufferedImage(frame);
            graphics = bufferedImage.createGraphics();

            for(RectInfo rectInfo : rectInfoList) {
                graphics.setColor(rectInfo.getColor());
                int[] xPoints = new int[5];
                xPoints[0] = 10;
                xPoints[1] = 300;
                xPoints[2] = 300;
                xPoints[3] = 10;
                xPoints[4] = 20;

                int[] yPoints = new int[5];
                yPoints[0] = 10;
                yPoints[1] = 10;
                yPoints[2] = 300;
                yPoints[3] = 300;
                yPoints[4] = 350;

                graphics.drawPolygon(xPoints, yPoints, 5);
            }
        } catch (Exception ex) {

        } finally {
            if(graphics != null) {
                graphics.dispose();
            }
        }
        // 视频帧赋值，写入输出流
        return converter.getFrame(bufferedImage);
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

    public void updateRedis() {
        //移除不更新
        if(this.rtspConverter.getRtspState() == RtspState.CLEAR)return;
        Map<Object, Object> hmget = redisUtil.hmget(this.url);
        if(hmget != null && hmget.size() > 0) {
            hmget.put((Object) "startTime",DateUtil.format(this.startTime, "yyyy-MM-dd HH:mm:ss"));
            hmget.put((Object)"lastTime",DateUtil.format(this.lastTime, "yyyy-MM-dd HH:mm:ss"));
            hmget.put((Object)"client",this.responseInfoList.size());
            hmget.put((Object)"state",this.rtspConverter.getRtspState());
            redisUtil.hmset(this.url,hmget);
        }
    }

}
