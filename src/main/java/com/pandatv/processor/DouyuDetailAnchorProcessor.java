package com.pandatv.processor;

import com.jayway.jsonpath.JsonPath;
import com.pandatv.common.Const;
import com.pandatv.common.PandaProcessor;
import com.pandatv.downloader.credentials.PandaDownloader;
import com.pandatv.mail.SendMail;
import com.pandatv.pipeline.DouyuDetailAnchorPipeline;
import com.pandatv.pojo.DetailAnchor;
import com.pandatv.tools.CommonTools;
import com.pandatv.tools.DateTools;
import com.pandatv.tools.HiveJDBCConnect;
import com.pandatv.tools.IOTools;
import com.pandatv.work.MailTools;
import org.slf4j.Logger;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by likaiqing on 2016/11/15.
 */
public class DouyuDetailAnchorProcessor extends PandaProcessor {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(DouyuDetailAnchorProcessor.class);
    private static List<String> detailAnchors = new ArrayList<>();
    private static String thirdApi = "http://open.douyucdn.cn/api/RoomApi/room";
    private static String job = "";
    private static StringBuffer failedUrl = new StringBuffer("failedUrl:");
    private static StringBuffer timeOutUrl = new StringBuffer("timeOutUrl:");
    private static int exCnt;

    @Override
    public void process(Page page) {
        String curUrl = page.getUrl().toString();
        logger.info("url:" + curUrl);
        try {
            if (curUrl.equals("https://www.douyu.com/directory/all")) {
                String js = page.getHtml().getDocument().getElementsByAttributeValue("type", "text/javascript").get(3).toString();
                int endPage = Integer.parseInt(js.substring(js.indexOf("count:") + 8, js.lastIndexOf(',') - 1));
                for (int i = 1; i < endPage; i++) {
                    Request request = new Request("https://www.douyu.com/directory/all?isAjax=1&page=" + i).setPriority(1);
                    page.addTargetRequest(request);
                }
//            } else if (curUrl.equals("https://www.douyu.com/")) {
//                List<String> tuijian = page.getHtml().xpath("//div[@class='c-items']/ul/li/@data-id").all();
//                for (String rid : tuijian) {
//                    Request request = new Request(thirdApi + "/" + rid);
//                    Map<String, Object> map = new HashMap<>();
//                    map.put("job", "douyushouyetuijian");
//                    request.setExtras(map);
//                    page.addTargetRequest(request.setPriority(3));
//                }
            } else if (curUrl.startsWith("https://www.douyu.com/directory/all?isAjax=1&page=")) {
                List<String> detailUrls = page.getHtml().xpath("//body/li/a/@href").all();
                for (String url : detailUrls) {
                    Request request = new Request(thirdApi + url.substring(url.lastIndexOf("/"))).setPriority(3);
                    page.addTargetRequest(request);
                }
            } else {
                Object cycleTriedTimes = page.getRequest().getExtra("_cycle_tried_times");
                if (null !=cycleTriedTimes && (int)cycleTriedTimes >= Const.CYCLERETRYTIMES - 1) {
                    timeOutUrl.append(curUrl).append(";");
                }
                page.addTargetRequest("http://open.douyucdn.cn/api/RoomApi/room/747269?"+Math.random());
                String json = page.getJson().get();
                DetailAnchor detailAnchor = new DetailAnchor();
                String rid = JsonPath.read(json, "$.data.room_id");
                String name = JsonPath.read(json, "$.data.owner_name");
                String title = JsonPath.read(json, "$.data.room_name");
                String categorySec = JsonPath.read(json, "$.data.cate_name");
                int viewerStr = JsonPath.read(json, "$.data.online");
                String followerStr = JsonPath.read(json, "$.data.fans_num");
                String weightStr = JsonPath.read(json, "$.data.owner_weight");
                String lastStartTime = JsonPath.read(json, "$.data.start_time");
                detailAnchor.setRid(rid);
                detailAnchor.setName(name);
                detailAnchor.setTitle(title);
                detailAnchor.setCategorySec(categorySec);
                detailAnchor.setViewerNum(viewerStr);
                detailAnchor.setFollowerNum(Integer.parseInt(followerStr));
                detailAnchor.setWeightNum(CommonTools.getDouyuWeight(weightStr));
                detailAnchor.setUrl(curUrl);
                detailAnchor.setLastStartTime(lastStartTime);
                detailAnchor.setJob(job);
                detailAnchors.add(detailAnchor.toString());
            }
            page.setSkip(true);
        } catch (Exception e) {
            failedUrl.append(curUrl + ";  ");
            e.printStackTrace();
            if (exCnt++ > Const.EXTOTAL) {
                MailTools.sendAlarmmail(Const.DOUYUEXIT, "url: " + curUrl);
                System.exit(1);
            }

        }
    }

    @Override
    public Site getSite() {
//        return this.site;//seleniumdownloader时使用,不能使用代理
        return CommonTools.getAbuyunSite(site);//采用两种downloader均已成功,测试仓促,最好再测试一遍
//        return CommonTools.getMayiSite(site);//未测试通过
    }

    /**
     * 待测试,使用seleniumdownloader应该为使用代理
     *
     * @param args
     */
    public static void crawler(String[] args) {
        String from = DateTools.getCurDate();
        job = args[0];//douyuanchordetail
        String date = args[1];
        String hour = args[2];
        long s = System.currentTimeMillis();
//        String firstUrl = "http://1212.ip138.com/ic.asp";
        String firstUrl = "https://www.douyu.com/directory/all";
        HiveJDBCConnect hive = new HiveJDBCConnect();
        String hivePaht = Const.HIVEDIR + "panda_detail_anchor_crawler/" + date + hour;
        Spider.create(new DouyuDetailAnchorProcessor()).thread(4).addUrl(firstUrl).addPipeline(new DouyuDetailAnchorPipeline(detailAnchors, hive, hivePaht)).setDownloader(new PandaDownloader()).run();//.setDownloader(new SeleniumDownloader(Const.CHROMEDRIVER))//.setDownloader(new PandaDownloader())
        try {
            hive.write2(hivePaht, detailAnchors, job);
        } catch (Exception e) {
            e.printStackTrace();
            BufferedWriter bw = IOTools.getBW("/tmp/douyudetailanchorcrawler" + date + hour + DateTools.getCurMinute());
            IOTools.writeList(detailAnchors, bw);
            MailTools.sendAlarmmail("斗鱼hive.write异常",e.getMessage().toString());
        }
        long e = System.currentTimeMillis();
        long time = e - s;
        String to = DateTools.getCurDate();
        MailTools.sendTaskMail(Const.DOUYUFINISH+ date + hour,from + "<-->" + to,time + "毫秒;",detailAnchors.size(),timeOutUrl,failedUrl);
    }
}
