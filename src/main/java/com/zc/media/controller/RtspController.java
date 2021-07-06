package com.zc.media.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.zc.media.netty.ServerHandler;
import com.zc.media.rtsp.RtspConverter;
import com.zc.media.rtsp.RtspState;
import com.zc.media.thread.AsyncService;
import com.zc.media.thread.MonitorThread;
import com.zc.media.thread.ThreadRtsp;
import org.bytedeco.javacv.*;
import org.opencv.osgi.OpenCVNativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:32
 **/
@RestController
@RequestMapping("/rtsp")
@CrossOrigin
public class RtspController {
    private static final Logger logger = LoggerFactory.getLogger(RtspController.class);
    @Autowired
    AsyncService asyncService;

    @PostConstruct
    public void init() {
//        asyncService.sendFlv();
        loadFFmpeg();
        asyncService.startServer(18003);
//        asyncService.send();
//        asyncService.clearClient(1000 * 60 * 5, 1000 * 60 * 1);
        MonitorThread monitorThread = new MonitorThread();
        monitorThread.start();
        //rtsp://admin:abcd1234@10.20.30.99:554/h264/ch1/main/av_stream
    }

    //加载ffmpeg dll
    public void loadFFmpeg() {
        try {
            logger.info("ffmpeg正在初始化资源，请稍等...");
            FFmpegFrameGrabber.tryLoad();
            FFmpegFrameRecorder.tryLoad();
//            OpenCVFrameGrabber.tryLoad();
//            OpenCVFrameRecorder.tryLoad();
            logger.info("ffmpeg初始化成功");
        } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
            e.printStackTrace();
        } catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/getRtspInfo")
    public JSONArray getRtspInfo() {
        JSONArray jsonArray = new JSONArray();
        Iterator iterator = ServerHandler.listThread.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String key = (String)entry.getKey();
            ThreadRtsp value = (ThreadRtsp)entry.getValue();
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("url",key);
            jsonObject.set("startTime",value.startTime);
            jsonObject.set("lastTime",value.lastTime);
            jsonObject.set("client",value.responseInfoList.size());
            jsonObject.set("state",value.rtspConverter.getRtspState());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }
}
