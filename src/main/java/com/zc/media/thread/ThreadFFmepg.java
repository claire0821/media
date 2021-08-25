package com.zc.media.thread;

import com.alibaba.fastjson.annotation.JSONField;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

/**
 * @program: media
 * @description: ffmpeg
 * @author: Claire
 * @create: 2021-07-09 13:17
 **/
public class ThreadFFmepg extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(ThreadFFmepg.class);

    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    public Date startTime;//开始转码时间
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    public Date lastTime;//最后观看时间
    String url = "";

    AVFormatContext ictx = new AVFormatContext(null);
    int videoIndex;
    AVCodecContext octx;//一个描述编解码器上下文的数据结构
    public ThreadFFmepg(String url) {
        this.url = url;
        this.startTime = new Date();
    }

    public boolean init() {
        try {
            av_log_set_level(AV_LOG_ERROR);
            AVDictionary inOptions = new AVDictionary();
//		AVInputFormat inputFormat = av_find_input_format(format);
            AVDictionary dict = new AVDictionary();

            //打开文件，解封文件头
            int ret = avformat_open_input(ictx, url, null, dict);
            if (ret < 0) {
                byte[] msg = new byte[1024];
                av_strerror(ret,msg,1024);
                logger.error("avformat_open_input error " + ret + " msg:" + new String(msg));
                return false;
            }
            logger.info("open stream success " + url);
            //读取一部分视音频数据并且获得一些相关的信息
            ret = avformat_find_stream_info(ictx, (PointerPointer) null);
            if (ret < 0) {
                return false;
            }
            //打印关于输入或输出格式的详细信息
            av_dump_format(ictx, 0, url, 0);

            //查找视频编码索引
            AVStream video_st = null;
            AVStream audio_st = null;
            AVCodecParameters video_par = null, audio_par = null;
            int nb_streams = ictx.nb_streams();
            for (int i = 0; i < nb_streams; i++) {
                AVStream st = ictx.streams(i);
                // Get a pointer to the codec context for the video or audio stream
                AVCodecParameters par = st.codecpar();
                if (video_st == null && par.codec_type() == AVMEDIA_TYPE_VIDEO) {
                    video_st = st;
                    video_par = par;
                    videoIndex = i;
                } else if (audio_st == null && par.codec_type() == AVMEDIA_TYPE_AUDIO) {
                    audio_st = st;
                    audio_par = par;
                }
            }
            if(videoIndex == -1 || video_st == null) {
                logger.error("find video error " + url);
                return false;
            }

            //查找解码器
            AVCodec codec = avcodec_find_decoder(video_par.codec_id());//找出对应视频的解码器（H264，MPEG2）
            if (codec == null) {
                logger.error("avcodec_find_decoder error");
                return false;
            }
            // 申请AVCodecContext空间
            if ((octx = avcodec_alloc_context3(codec)) == null) {
                logger.error("avcodec_alloc_context3 error");
            }
            //该函数用于将流里面的参数，也就是AVStream里面的参数直接复制到AVCodecContext的上下文当中
            ret = avcodec_parameters_to_context(octx,video_par);
            if (ret < 0) {
              logger.error("avcodec_parameters_to_context error");
            }

            // 打开视频解码器
            AVDictionary options = new AVDictionary();
            ret = avcodec_open2(octx, codec, options);
            if (ret < 0) {
                logger.error("avcodec_open2 error");
            }
            av_dict_free(options);

        } catch (Exception ex) {
            return false;
        } finally {
            if(ictx != null) {
                ictx.close();
            }
            if(octx != null) {
                octx.close();
            }
        }
        return true;

    }

    @Override
    public void run() {
        init();
        AVPacket packet = av_packet_alloc();
        while (true) {
            //从输入流获取一个数据包
            int ret = av_read_frame(ictx,packet);
            if(ret < 0) {
                av_packet_unref(packet);
                continue;
            }
            if(packet.stream_index() != videoIndex) {
                av_packet_unref(packet);
                continue;
            }

            ret = avcodec_send_packet(octx,packet);

        }
    }
}
