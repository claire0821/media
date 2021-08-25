package com.zc.media.netty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.unix.PreferredDirectByteBufAllocator;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:20
 **/


@Slf4j
@Component
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public void start(int port) {
        //new 一个主线程组 用于处理服务器端接收客户端连接
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        //new 一个工作线程组 进行网络通信（读写）
        EventLoopGroup workGroup = new NioEventLoopGroup(200);
        //辅助工具类，用于服务器通道的一系列配置
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workGroup)//绑定两个线程组
                .channel(NioServerSocketChannel.class)//指定NIO的模式
                //配置具体的数据处理方式
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        CorsConfig corsConfig = CorsConfigBuilder.forAnyOrigin().allowNullOrigin().allowCredentials().build();

                        socketChannel.pipeline()
                                .addLast(new HttpResponseEncoder())//编解码器
                                .addLast(new HttpRequestDecoder())//编解码器
                                .addLast(new ChunkedWriteHandler())
                                .addLast(new HttpObjectAggregator(64 * 1024))
                                .addLast(new CorsHandler(corsConfig))
                                .addLast(new ServerHandler());
                    }
                })
//                .localAddress(socketAddress)
                //设置TCP缓冲区
                .option(ChannelOption.SO_BACKLOG, 128)
//                .option(ChannelOption.SO_SNDBUF, 32 * 1024) //设置发送数据缓冲大小
//                .option(ChannelOption.SO_RCVBUF, 32 * 1024) //设置接受数据缓冲大小
                //首选直接内存
                .option(ChannelOption.ALLOCATOR, PreferredDirectByteBufAllocator.DEFAULT)
                //设置队列大小
//                .option(ChannelOption.SO_BACKLOG, 1024)
                // 两小时内没有数据的通信时,TCP会自动发送一个活动探测数据报文
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)//保持连接
                .childOption(ChannelOption.SO_RCVBUF, 128 * 1024)
                .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024 / 2, 1024 * 1024));
        //绑定端口,开始接收进来的连接
        try {
            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("netty启动开始监听端口: {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //关闭主线程组
            bossGroup.shutdownGracefully();
            //关闭工作线程组
            workGroup.shutdownGracefully();
        }
    }
}
