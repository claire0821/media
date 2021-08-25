package com.zc.media.thread;

import com.zc.media.netty.ServerHandler;
import com.zc.media.rtsp.RtspState;
import com.zc.media.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * @program: media
 * @description: 监控线程
 * @author: Claire
 * @create: 2021-07-05 14:59
 **/
public class MonitorThread extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(MonitorThread.class);

    @Autowired
    RedisUtil redisUtil;

    @Override
    public void run() {
        try{
            logger.info("监控线程启动");
            setName("monitor");
            while (true) {
                Iterator iterator = ServerHandler.listThread.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    String key = (String)entry.getKey();
                    ThreadRtsp value = (ThreadRtsp)entry.getValue();
                    if(value.responseInfoList.size() <= 0) {
                        if(value.lastTime != null) {
                            Date now = new Date();
                            long diff = now.getTime() - value.lastTime.getTime();
                            //关闭视频流
                            if(diff > 1000 * 60 * 2) {
                                value.rtspConverter.setRtspState(RtspState.CLOSE);
                                logger.warn("准备关闭无人观看视频流" + key);
                            }
                        }
                    }
                    //移除线程
                    if(value.rtspConverter.getRtspState() == RtspState.CLOSE || value.rtspConverter.getRtspState() == RtspState.ERROR) {
                        ServerHandler.listThread.remove(key);
                        logger.warn("关闭视频流" + key);
                    }
                }
                Thread.sleep(1000 * 10);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
