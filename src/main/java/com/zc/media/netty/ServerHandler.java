package com.zc.media.netty;
import cn.hutool.core.date.DateTime;
import com.zc.media.model.ResponseInfo;
import com.zc.media.rtsp.RtspState;
import com.zc.media.thread.AsyncService;
import com.zc.media.thread.AsyncServiceImpl;
import com.zc.media.thread.ThreadRtsp;
import com.zc.media.util.RedisUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:20
 **/
@Component
public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    public static ConcurrentHashMap<String, ThreadRtsp> listThread = new ConcurrentHashMap<>();//存放解码线程

    private WebSocketServerHandshaker handshaker;
    @Autowired
    AsyncService service;

//    @Resource
//    private RedisUtil redisUtil;

    private static ServerHandler serverHandler;
    @PostConstruct
    public void init() {
        serverHandler = this;
        serverHandler.service = this.service;
//        serverHandler.redisUtil = this.redisUtil;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof FullHttpRequest) {
            FullHttpRequest req = (FullHttpRequest) msg;
            String uri = req.uri();

            String streamUrl = "";//流地址
            ResponseInfo responseInfo = null;
            if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
                // http请求
                QueryStringDecoder decoder = new QueryStringDecoder(uri);
                Map<String, List<String>> parameters = decoder.parameters();
                List<String> stream = parameters.get("stream");
                if(stream != null && stream.size() == 1) {//判断是否带有流地址
                    System.out.println(stream.get(0));
                    responseInfo = new ResponseInfo();
                    responseInfo.setResponse(ctx);
                    responseInfo.setIsHttp(ResponseInfo.HTTP);
                    responseInfo.setUrl(stream.get(0));
                    responseInfo.setSendHeader(false);

                    streamUrl = stream.get(0);
                    sendFlvReqHeader(ctx);
//                    AsyncServiceImpl.addRes(responseInfo);
//                    serverHandler.service.openTest(stream.get(0),ctx);
                } else {
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                }

            } else {
                int startIndex = uri.indexOf("stream=");
                if(startIndex <= 0) {
                    return;
                }
                startIndex += 7;
                String stream = uri.substring(startIndex);
                String location = "ws://" + req.headers().get(HttpHeaderNames.HOST) + req.uri();

                // websocket握手，请求升级
                // 参数分别是ws地址，子协议，是否扩展，最大frame长度
                WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                        location, null, true, 5 * 1024 * 1024);
                handshaker = factory.newHandshaker(req);
                if (handshaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    handshaker.handshake(ctx.channel(), req);
//                    mediaService.playForWs(camera, ctx, autoClose);
                }

                responseInfo = new ResponseInfo();
                responseInfo.setResponse(ctx);
                responseInfo.setIsHttp(ResponseInfo.WS);
                responseInfo.setUrl(stream);
                responseInfo.setSendHeader(false);

                streamUrl = stream;
//
//                AsyncServiceImpl.addRes(responseInfo);
//                serverHandler.service.openTest(stream,ctx);
            }

            //添加线程
            if(streamUrl.length() > 0 && responseInfo != null) {
                ThreadRtsp thread = listThread.get(streamUrl);
                if(thread == null) {
                    thread = new ThreadRtsp(streamUrl);
                    thread.add(responseInfo);
                    thread.start();
                } else {
                    thread.add(responseInfo);
                }
                listThread.put(streamUrl,thread);
//                serverHandler.redisUtil.set(responseInfo.getResponse().channel().id().toString(), streamUrl,100);
            } else {//断开无效连接

            }
        } else if(msg instanceof WebSocketFrame) {
            handleWebSocketRequest(ctx, (WebSocketFrame) msg);
        }

    }

    /**
     * 错误请求响应
     *
     * @param ctx
     * @param status
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("请求地址有误: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * websocket处理
     *
     * @param ctx
     * @param frame
     */
    private void handleWebSocketRequest(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 关闭
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }

        ResponseInfo responseInfo = AsyncServiceImpl.listRes.get(ctx.channel());
        responseInfo.setLastTime(new DateTime());//更新时间
        // 握手PING/PONG
        if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
            System.out.println("ping");
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            System.out.println("pong");
            return;
        }

        // 文本
        if (frame instanceof TextWebSocketFrame) {
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            return;
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        System.out.println("client close" + ctx.channel().remoteAddress().toString());

        //请求退出
//        AsyncServiceImpl.listRes.remove(ctx.channel().id());
    }

    /**
     * 发送req header，告知浏览器是flv格式
     *
     * @param ctx
     */
    private void sendFlvReqHeader(ChannelHandlerContext ctx) {
        HttpResponse rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        rsp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                .set(HttpHeaderNames.CONTENT_TYPE, "video/x-flv").set(HttpHeaderNames.ACCEPT_RANGES, "bytes")
                .set(HttpHeaderNames.PRAGMA, "no-cache").set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED).set(HttpHeaderNames.SERVER, "zhang");
        ctx.writeAndFlush(rsp);
    }

    private String getWebSocketLocation(FullHttpRequest request) {
        System.out.println(request.uri());
        String location = request.headers().get(HttpHeaderNames.HOST) + request.uri();
        return "ws://" + location;
    }

    public static void sendPing(String expiredKey) {
//        String o = (String) serverHandler.redisUtil.get(expiredKey);
//        ThreadRtsp threadRtsp = listThread.get(o);
//        threadRtsp.sendPing(expiredKey);
    }

    public static void close(String expiredKey) {
        ThreadRtsp threadRtsp = listThread.get(expiredKey);
        if(threadRtsp.responseInfoList.size() > 0) return;
        if(threadRtsp.isAlive()) {
            threadRtsp.rtspConverter.setRtspState(RtspState.CLOSE);//关闭线程
        }
        listThread.remove(expiredKey);
    }
}