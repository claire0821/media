package com.zc.media.controller;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zc.media.netty.ServerHandler;
import com.zc.media.rtsp.RtspConverter;
import com.zc.media.rtsp.RtspState;
import com.zc.media.thread.AsyncService;
import com.zc.media.thread.MonitorThread;
import com.zc.media.thread.ThreadFFmepg;
import com.zc.media.thread.ThreadRtsp;
import com.zc.media.util.RedisUtil;
import com.zc.media.util.ResultUtil;
import org.bytedeco.javacv.*;
import org.opencv.osgi.OpenCVNativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;

import static com.zc.media.netty.ServerHandler.listThread;

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
    MonitorThread monitorThread;

    @Autowired
    RedisUtil redisUtil;

    @PostConstruct
    public void init() {
//        asyncService.sendFlv();
        loadFFmpeg();
        asyncService.startServer(18003);
//        asyncService.send();
//        asyncService.clearClient(1000 * 60 * 5, 1000 * 60 * 1);
        monitorThread = new MonitorThread(
                
        );
        monitorThread.start();

//        ThreadFFmepg threadFFmepg = new ThreadFFmepg("rtsp://10.20.30.176:554/PR0");
//        threadFFmepg.start();
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

    @RequestMapping(value = "/getRtspInfo", method = RequestMethod.GET)
    public JSONArray getRtspInfo() {
        JSONArray jsonArray = new JSONArray();
        Iterator iterator = listThread.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String key = (String)entry.getKey();
            ThreadRtsp value = (ThreadRtsp)entry.getValue();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",key);
            jsonObject.put("startTime", DateUtil.format(value.startTime,"yyyy-MM-dd HH:mm:ss"));
            jsonObject.put("lastTime",DateUtil.format(value.lastTime,"yyyy-MM-dd HH:mm:ss"));
            jsonObject.put("client",value.responseInfoList.size());
            jsonObject.put("state",value.rtspConverter.getRtspState());
            jsonObject.put("id",value.getId());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    @RequestMapping(value = "/stopStream",method = RequestMethod.GET)
    public void stopStream(@RequestParam(value = "id") long id) {
        Iterator iterator = listThread.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String key = (String)entry.getKey();
            ThreadRtsp value = (ThreadRtsp)entry.getValue();
            if(value.getId() == id) {
                value.rtspConverter.setRtspState(RtspState.CLOSE);
            }
        }
    }

    @RequestMapping(value = "/addStream",method = RequestMethod.POST)
    public JSONObject addStream(@RequestBody JSONObject jsonObject) {
        String url = jsonObject.getString("url");
        if(url == null) return ResultUtil.error("流地址不能为空");
        Integer index = jsonObject.getInteger("index");
        if(index == null) return ResultUtil.error("编号不能为空");

//        boolean res = redisUtil.lSet("stream",url);
//        if(!res) {
//            return ResultUtil.error("存储信息失败");
//        }
        Map<Object, Object> map = new HashMap<>();
        map.put("index",index);
        map.put("url",url);
        map.put("out","flv");
        map.put("startTime","-");
        map.put("lastTime", "-");
        map.put("client","-");
        boolean hmset = redisUtil.hmset(url, map);
        if(!hmset) {
            return ResultUtil.error("存储信息失败");
        }
        return ResultUtil.success();
    }

    @RequestMapping(value = "/getStreamInfo", method = RequestMethod.GET)
    public JSONObject getStreamInfo() {
        JSONArray jsonArray = new JSONArray();
//        for(int i = 0; i < 16; i++) {
//            Map<Object, Object> map = redisUtil.hmget(String.valueOf(i));
//            if(map != null && map.size() > 0) {
//                JSONObject jsonObject = new JSONObject();
//
//                for (Map.Entry<Object, Object> entry : map.entrySet()) {
//                    jsonObject.put(entry.getKey().toString(),entry.getValue());
//                }
//                jsonArray.add(jsonObject);
//            }
//        }

        Set<String> keys = redisUtil.getKeys("rtsp*");
        for(String key : keys) {
            Map<Object, Object> map = redisUtil.hmget(key);
            if(map != null && map.size() > 0) {
                JSONObject jsonObject = new JSONObject();
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    jsonObject.put(entry.getKey().toString(),entry.getValue());
                }
                jsonArray.add(jsonObject);
            }
        }
        if(jsonArray.size() <= 0) {
            return ResultUtil.error("列表为空");
        } else {
            return ResultUtil.success(jsonArray);
        }

    }

    //删除流信息，停止转发
    @RequestMapping(value = "/clearStreamInfo",method = RequestMethod.POST)
    public JSONObject clearStreamInfo(@RequestBody JSONObject jsonObject) {
        String url = jsonObject.getString("url");
        if(url == null) return ResultUtil.error("流地址不能为空");

        boolean del = redisUtil.del(url);

        ThreadRtsp threadRtsp = listThread.get(url);
        threadRtsp.rtspConverter.setRtspState(RtspState.CLEAR);

        return ResultUtil.success();
    }

//    @RequestMapping(value = "/",method = RequestMethod.GET)
//    public JSONObject capture(@RequestBody JSONObject jsonObject) {
//        String url = jsonObject.getString("url");
//        if(url == null) return ResultUtil.error("流地址不能为空");
//
//    }
}
