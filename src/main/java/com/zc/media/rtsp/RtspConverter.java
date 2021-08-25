package com.zc.media.rtsp;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.zc.media.model.StreamInfo;
import com.zc.media.thread.AsyncServiceImpl;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.bytedeco.opencv.presets.opencv_core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.SimpleFormatter;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avformat.av_dump_format;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
/**
 * @program: media
 * @description:
 * @author: Claire
 * @create: 2021-06-30 15:27
 **/
public class RtspConverter extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(RtspConverter.class);
    public volatile boolean exit = false;//没有拉流关闭流

    //流地址
    public String url;

    //读流器
    public FFmpegFrameGrabber grabber;
    //转码器
    public FFmpegFrameRecorder recorder;
    public FFmpegFrameFilter filter;//叠加字符

    //转FLV格式的头信息<br/>
    //如果有第二个客户端播放首先要返回头信息
    private byte[] headers;

    //保存转换好的流
    public ByteArrayOutputStream stream;

    private byte[] outData;
    private long updateTime;

    volatile private RtspState rtspState;

    //开始拉流时间
    private DateTime startTime;

    public boolean capture;

    public int videoStream;
    public int audioStream;
    public RtspState getRtspState() {
        return rtspState;
    }

    public void setRtspState(RtspState rtspState) {
        this.rtspState = rtspState;
    }

    public RtspConverter(String url) {
        this.url = url;
        this.updateTime = System.currentTimeMillis();
//        avformat_network_init();
        exit = false;
        avutil.av_log_set_level(AV_LOG_ERROR);
        FFmpegLogCallback.set();
        this.rtspState = RtspState.INITIAL;
        setStartTime(new DateTime());//设置开始时间
        capture = false;
    }

    public byte[] getHeader() {
        if(headers == null || headers.length <= 0) {
            return null;
        }
        return headers;
    }

    public byte[] getOutData() {
        return outData;
    }

    public void setOutData(byte[] outData) {
        this.outData = outData;
    }

//    public void getOut(HttpServletResponse response) throws IOException, InterruptedException {
//        if(out != null && out.length > 0) {
//            response.getOutputStream().write(out);
//        }
//        Thread.sleep(100);
//        getOut(response);
//    }


    public DateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    private boolean createGrabber() {
        boolean res = false;
        try {
            grabber = FFmpegFrameGrabber.createDefault(url);// new FFmpegFrameGrabber(url);

            if("rtsp".equals(url.substring(0,4))) {
                //设置打开协议tcp / udp udp_multicast使用UDP多播作为较低的传输协议 http使用HTTP隧道作为较低的传输协议
                grabber.setOption("rtsp_transport", "tcp");
                //首选TCP进行RTP传输
                //filter_src：只接受来自协商的对等地址和端口的数据包。
                //listen：充当服务器，监听传入的连接。
                //prefer_tcp：如果TCP可用作RTSP RTP传输，请先尝试使用TCP进行RTP传输。
                grabber.setOption("rtsp_flags", "prefer_tcp");
                //初始暂停 如果设置为1，请不要立即开始播放流。默认值为0。
                //grabber.setOption("initial_pause", "0");
                //允许的媒体类型 vide audio data
//                grabber.setOption("allowed_media_types", "video");
                grabber.setOption("stimeout", "5000000");//5秒
            }
            // 设置缓存大小，提高画质、减少卡顿花屏
            grabber.setOption("buffer_size", "1024000");
            //设置视频比例
            //grabber.setAspectRatio(1.7777);
//            grabber.setPixelFormat(AV_PIX_FMT_YUV420P);
//            grabber.setOption("threads", "1");

            grabber.start();
            //grabber.flush();
            res = true;
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
            res = false;
        } finally {
            if(res == false) {
                try {
                    if(grabber != null) grabber.close();
                } catch (FrameGrabber.Exception e) {
                    e.printStackTrace();
                }
            }
            return res;
        }
    }

    //转换成flv
    public boolean createRecorder() {
        boolean res = false;
        try{
            stream = new ByteArrayOutputStream();
            //创建转码器
            recorder = new FFmpegFrameRecorder(stream, grabber.getImageWidth(), grabber.getImageHeight(),
                    grabber.getAudioChannels());
            recorder.setFormat("flv");//录制视频格式
            recorder.setVideoCodec(grabber.getVideoCodec());;// 直播流格式
            recorder.setFrameRate(25);// 帧数
            // 关键帧间隔，一般与帧率相同或者是视频帧率的两倍
            recorder.setGopSize(50);
            //百度翻译的比特率，默认400000，但是我400000贼模糊，调成800000比较合适
            recorder.setVideoBitrate(800000);

            // 降低编码延时
            //recorder.setVideoOption("tune", "zerolatency");
            // 提升编码速度
            //recorder.setVideoOption("preset", "ultrafast");
            /*
            if(false) {
                recorder.setInterleaved(false);
                recorder.setVideoOption("tune", "zerolatency");
                recorder.setVideoOption("preset", "ultrafast");
                recorder.setVideoOption("crf", "26");
                recorder.setVideoOption("threads", "1");
                //提供输出流封装格式
                recorder.setFormat("flv");
                // 视频帧率(保证视频质量的情况下最低25，低于25会出现闪屏
                recorder.setFrameRate(50);// 设置帧率
                // 关键帧间隔，一般与帧率相同或者是视频帧率的两倍
                recorder.setGopSize(50);// 设置gop
//		recorder.setVideoBitrate(500 * 1000);// 码率500kb/s
                recorder.setVideoCodecName("libx264");
//		recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
//		recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.setAudioCodecName("aac");
                recorder.setVideoOption("preset", "ultrafast");
                recorder.setAudioChannels(grabber.getAudioChannels());
            } else {
                recorder.setFormat("flv");
                recorder.setImageWidth(grabber.getImageWidth());
                recorder.setImageHeight(grabber.getImageHeight());
                //配置转码器
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setSampleRate(grabber.getSampleRate());
                if (grabber.getAudioChannels() > 0) {
                    recorder.setAudioChannels(grabber.getAudioChannels());
                    recorder.setAudioBitrate(grabber.getAudioBitrate());
                    recorder.setAudioCodec(grabber.getAudioCodec());
                    //设置视频比例
                    //recorder.setAspectRatio(grabber.getAspectRatio());
                }
                recorder.setVideoBitrate(grabber.getVideoBitrate());//码率
                recorder.setVideoCodec(grabber.getVideoCodec());
            } */
//            recorder.start(grabber.getFormatContext());
//            grabber.flush();

            recorder.start(grabber.getFormatContext());
            //recorder.start();
            //AVFormatContext的max_interleave_delta参数默认为10秒，在ff_interleave_packet_per_dts方法中会检查所有流是否都有数据，如果没有数据会默认等待10秒，因此造成推流延迟。
            // 解决音视频同步导致的延时问题
            Field field = recorder.getClass().getDeclaredField("oc");
            field.setAccessible(true);
            AVFormatContext oc = (AVFormatContext) field.get(recorder);
            oc.max_interleave_delta(100);

            //设置头信息
            this.headers = stream.toByteArray();
            stream.reset();

//            try(OutputStream out = new BufferedOutputStream(new FileOutputStream("2.flv", false))){
//                out.write(this.headers);
//            } catch (Exception ex) {
//                System.out.println(ex);
//            }

            res = true;
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
            res = false;
        } finally {
            if(res == false) {
                try {
                    if(recorder != null) recorder.close();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
            return res;
        }
    }
    int count = 0;
    public void toFlv() {
        if(!createGrabber()) {
            this.rtspState = RtspState.ERROR;
            logger.error("open stream failed " + this.url);
            add();
            return;
        }
        logger.info("open stream success " + this.url);
        if(!createRecorder()) {
            this.rtspState = RtspState.ERROR;
            logger.error("open record failed " + this.url);
            add();
            return;
        }
        logger.info("open record success " + this.url);
        this.rtspState = RtspState.OPEN;

        add();

        while (!exit) {
            //FFmpeg读流压缩
            AVPacket k = null;
            Frame frame = null;
            try {
                if(true) {
                    k = grabber.grabPacket();
                    if(k == null || k.size() == 0 || k.data() == null) {
                        System.out.println("AVPacket null");
                        continue;
                    }
                    // 过滤音频
                    if (k.stream_index() == 1) {
                        av_packet_unref(k);
                        continue;
                    }
                    if(k!= null) {
                        recorder.recordPacket(k);//转换器转换
//                        recorder.recordImage(recorder.getImageWidth(),recorder.getImageHeight(),
//                                recorder.dep)
                        System.out.println("recorder-----------------");
                    }

                } else {
                    // 每一秒第一帧的时间
                    long firstpkttime = System.currentTimeMillis();
                    int pktindex = 0;
                    // dts\pts从0开始累加
                    long dts = 0;
                    long pts = 0;

                    frame = grabber.grabFrame();

                    if(frame != null) {
//                        recorder.setTimestamp(frame.timestamp);
//                        Java2DFrameConverter converter =new Java2DFrameConverter();
////                        BufferedImage bi =converter.getBufferedImage(frame);
//                        String targetFileName = "1\\" + DateUtil.format(new Date(), "HH_mm_ss_SSS");
//                        targetFileName += ".jpg";
//                        File output =new File(targetFileName);
//                        try {
//                            ImageIO.write(bi,"jpg",output);
//                        }catch (IOException e) {
//                            e.printStackTrace();
//                        }


//                        BufferedImage bufferedImage = converter.convert(frame);
//                        //照片保存文件夹
//                        File targetFile = new File(targetFileName);
//                        //写入文件夹,以jpg图片格式
//                        if(bufferedImage != null) {
//                            ImageIO.write(bufferedImage, "jpg", targetFile);
//                        }

                        System.out.println("recorder-----------------");
                        recorder.record(frame);

                    }
                }

                if(stream.size() > 0) {
                    setOutData(stream.toByteArray());
//                    if(capture) {
//                        File zipFile=new File("d:\\1");
//                        FileOutputStream fos2 = new FileOutputStream(zipFile);
//                        if(count == 0) {
//                            fos2.write(headers);
//                        }
//                        fos2.write(stream.toByteArray());
//                        count++;
//                        if(count >= 100) {
//                            count = 0;
//                            capture = false;
//                        }
////                        stream.writeTo(fos2);
//                        fos2.close();
//                    }
                    stream.reset();
                    try(OutputStream out = new BufferedOutputStream(new FileOutputStream("2.flv", true))){
                        out.write(this.getOutData());
                    } catch (Exception ex) {
                        System.out.println(ex);
                    }
                    setRtspState(RtspState.RUN);

                    add();
                } else {
                    System.out.println("size < 0-------------------");
                }
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
                continue;
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
                continue;
            }  catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            if(grabber != null) grabber.close();
            if(recorder != null) recorder.close();
            if(stream != null) stream.close();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.rtspState = RtspState.CLOSE;
            logger.info("拉流结束" + this.url);
        }
    }

    //只转换
    public boolean startGrabber() {
        if(!createGrabber()) {
            this.rtspState = RtspState.ERROR;
            logger.error("open stream failed " + this.url);
            return false;
        }

        this.videoStream = this.grabber.getVideoStream();
        this.audioStream = this.grabber.getAudioStream();

        if(!createRecorder()) {
            this.rtspState = RtspState.ERROR;
            logger.error("open record failed " + this.url);
            return false;
        }

        this.rtspState = RtspState.OPEN;
        return true;
    }
    //叠加字符
    public boolean createFilter() {
        boolean res = false;
        try {
            filter = new FFmpegFrameFilter(
                    "scale=400x300,transpose=cclock_flip,format=gray",
                    "volume=0.5,aformat=sample_fmts=u8:channel_layouts=mono",
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            filter.setPixelFormat(grabber.getPixelFormat());
            filter.setSampleFormat(grabber.getSampleFormat());
            filter.setFrameRate(grabber.getFrameRate());
            filter.setSampleRate(grabber.getSampleRate());
            filter.start();
            res = true;
        } catch (FrameFilter.Exception e) {
            e.printStackTrace();
            res = false;
        } finally {
            if(!res) {
                if(filter != null) {
                    try {
                        filter.close();
                    } catch (FrameFilter.Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return res;
        }


    }
    public void close() {
        try {
            if(grabber != null) grabber.close();
            if(recorder != null) recorder.close();
            if(stream != null) stream.close();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.rtspState = RtspState.CLOSE;
            logger.info("拉流结束" + this.url);
        }
    }
    public void add() {
        if(AsyncServiceImpl.listStream.size() > AsyncServiceImpl.listRtsp.size() * 5) {
            AsyncServiceImpl.listStream.clear();
        }
        System.out.println("add--------------");
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setState(this.rtspState);
        streamInfo.setUrl(this.url);
        streamInfo.setHeaders(this.headers);
        streamInfo.setOutData(this.outData);
        AsyncServiceImpl.listStream.add(streamInfo);
    }
    @Override
    public void run() {
        toFlv();
    }
}
