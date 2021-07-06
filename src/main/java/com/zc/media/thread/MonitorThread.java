package com.zc.media.thread;

import com.zc.media.netty.ServerHandler;
import com.zc.media.rtsp.RtspState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @Override
    public void run() {
        try{
            logger.info("监控线程启动");
            while (true) {
                Iterator iterator = ServerHandler.listThread.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    String key = (String)entry.getKey();
                    ThreadRtsp value = (ThreadRtsp)entry.getValue();
                    if(value.rtspConverter.getRtspState() == RtspState.CLOSE) {//移除线程
                        ServerHandler.listThread.remove(key);
                    }
                    if(value.responseInfoList.size() <= 0) {
                        Date now = new Date();
                        long diff = now.getTime() - value.lastTime.getTime();
                        if(diff > 1000 * 30) {//关闭视频流
                            value.rtspConverter.setRtspState(RtspState.CLOSE);
                        }
                    }
                }
                Thread.sleep(1000 * 60);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
