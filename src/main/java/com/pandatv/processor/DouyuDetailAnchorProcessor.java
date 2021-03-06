package com.pandatv.processor;

import com.jayway.jsonpath.JsonPath;
import com.pandatv.common.Const;
import com.pandatv.common.PandaProcessor;
import com.pandatv.downloader.credentials.PandaDownloader;
import com.pandatv.pojo.DetailAnchor;
import com.pandatv.pojo.GiftInfo;
import com.pandatv.tools.CommonTools;
import com.pandatv.tools.HttpUtil;
import com.pandatv.tools.MailTools;
import net.minidev.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.ConsolePipeline;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by likaiqing on 2016/11/15.
 */
public class DouyuDetailAnchorProcessor extends PandaProcessor {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(DouyuDetailAnchorProcessor.class);
    private static String thirdApi = "http://open.douyucdn.cn/api/RoomApi/room/";
    private static String listPre = "https://www.douyu.com/gapi/rkc/directory/0_0/";
    private static int exCnt;
    private static Pattern showStatus = Pattern.compile("\"show_status\":(\\d*),");

    @Override
    public void process(Page page) {
        requests++;
        String curUrl = page.getUrl().toString();
        logger.info("url:" + curUrl);
        try {
            if (curUrl.equals("https://www.douyu.com/directory/all")) {
                Elements elements = page.getHtml().getDocument().getElementsByAttributeValue("type", "text/javascript");
                int endPage = 1;
                for (int i = 0; i < elements.size(); i++) {
                    String element = elements.get(i).toString();
                    if (element.contains("count:")) {
                        endPage = Integer.parseInt(element.substring(element.indexOf("count:") + 8, element.lastIndexOf(',') - 1));
                        break;
                    }
                }
                endPage = endPage > 150 ? 150 : endPage;
                for (int i = 1; i < endPage; i++) {
                    Request request = new Request(listPre + i).setPriority(1);
                    page.addTargetRequest(request);
                }
            } else if (curUrl.startsWith(listPre)) {
//                List<String> detailUrls = page.getHtml().xpath("//body/li/a/@href").all();
//                for (String url : detailUrls) {
//                    Request request = new Request(thirdApi + url.substring(url.lastIndexOf("/"))).setPriority(3);
//                    page.addTargetRequest(request);
//                }
                String json = page.getJson().get();
                JSONArray list = JsonPath.read(json,"$.data.rl");
                for (int i=0;i<list.size();i++){
                    String room = list.get(i).toString();
                    Request request = new Request(thirdApi + JsonPath.read(room,"$.rid").toString()).setPriority(3);
                    page.addTargetRequest(request);
                }
            } else {
                Object cycleTriedTimes = page.getRequest().getExtra("_cycle_tried_times");
                if (null != cycleTriedTimes && (int) cycleTriedTimes >= Const.CYCLERETRYTIMES - 1) {
                    timeOutUrl.append(curUrl).append(";");
                }
                String json = page.getJson().get();
                Integer error = JsonPath.read(json, "$.error");
                if (error != 0) {
                    Integer exRetry = (Integer) page.getRequest().getExtra("exRetry");
                    if (exRetry < 4) {
                        Request request = new Request(curUrl);
                        request.putExtra("exRetry", null == exRetry ? 1 : ++exRetry);
                    } else {
                        failedUrl.append(curUrl + ";  ");
                    }
                    page.setSkip(true);
                    return;
                }
                int online = JsonPath.read(json, "$.data.online");
                if (online == 0) {
                    return;
                }
                DetailAnchor detailAnchor = new DetailAnchor();
                String rid = JsonPath.read(json, "$.data.room_id");
                String name = JsonPath.read(json, "$.data.owner_name");
                String title = JsonPath.read(json, "$.data.room_name");
                String categorySec = JsonPath.read(json, "$.data.cate_name");
                int viewers = JsonPath.read(json, "$.data.online");
                String followerStr = JsonPath.read(json, "$.data.fans_num");
                String weightStr = JsonPath.read(json, "$.data.owner_weight");
                String lastStartTime = JsonPath.read(json, "$.data.start_time");
//                HttpUtil.sendGet(new StringBuffer(Const.DDPUNCHDOMAIN).append(Const.DETAILANCHOREVENT)
//                        .append("&par_d=").append(date)
//                        .append("&rid=").append(rid)
//                        .append("&nm=").append(CommonTools.getFormatStr(name))
//                        .append("&tt=").append(CommonTools.getFormatStr(title))
//                        .append("&cate_fir=&cate_sec=").append(categorySec)
//                        .append("&on_num=").append(viewers)
//                        .append("&fol_num=").append(StringUtils.isEmpty(followerStr) ? 0 : Integer.parseInt(followerStr))
//                        .append("&task=").append(job)
//                        .append("&rank=&w_str=&w_num=").append(CommonTools.getDouyuWeight(weightStr))
//                        .append("&tag=&url=").append(curUrl)
//                        .append("&c_time=").append(createTimeFormat.format(new Date()))
//                        .append("&notice=&last_s_t=").append(lastStartTime)
//                        .append("&t_ran=").append(PandaProcessor.getRandomStr()).toString());
                detailAnchor.setRid(rid);
                detailAnchor.setName(name);
                detailAnchor.setTitle(title);
                detailAnchor.setCategorySec(categorySec);
                detailAnchor.setViewerNum(viewers);
                detailAnchor.setFollowerNum(Integer.parseInt(followerStr));
                detailAnchor.setWeightNum(CommonTools.getDouyuWeight(weightStr));
                detailAnchor.setUrl(curUrl);
                detailAnchor.setLastStartTime(lastStartTime);
                detailAnchor.setJob(job);
//                HttpUtil.sendGet(new StringBuffer(Const.DDPUNCHDOMAIN).append(Const.DETAILANCHOREVENT)
//                        .append("&par_d=").append(date).append(detailAnchor.toString()).toString());
                detailAnchorObjs.add(detailAnchor);
//                if (douyuGiftHours.contains(hour)) {
                JSONArray gifts = JsonPath.read(json, "$.data.gift");
                for (int i = 0; i < gifts.size(); i++) {
                    String gift = gifts.get(i).toString();
                    String gId = JsonPath.read(gift, "$.id");
                    String gName = JsonPath.read(gift, "$.name");
                    String gType = JsonPath.read(gift, "$.type");
                    double gPrice = 0.0;
                    try {
                        int priceInt = JsonPath.read(gift, "$.pc");
                        gPrice = (double) priceInt;
                    } catch (ClassCastException e) {
                        gPrice = JsonPath.read(gift, "$.pc");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int gExp = JsonPath.read(gift, "$.gx");
                    GiftInfo douyuGift = new GiftInfo();
                    douyuGift.setPlat(Const.DOUYU);
                    douyuGift.setrId(rid);
                    douyuGift.setCategory(categorySec);
                    douyuGift.setGiftId(gId);
                    douyuGift.setName(gName);
                    try {
                        douyuGift.setType(Integer.parseInt(gType));
                        douyuGift.setPrice(gPrice);
                        douyuGift.setExp(gExp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
//                    new Thread(new Runnable() {
//                        @Override
//                        public void run() {
//                            HttpUtil.sendGet(new StringBuffer(Const.DDPUNCHDOMAIN).append(Const.DOUYUGIFTIDEVENT)
//                                    .append("&par_d=").append(date).append(douyuGift.toString()).toString());
//                        }
//                    }).start();
//                    Thread.sleep(5);
//                    HttpUtil.sendGet(new StringBuffer(Const.DDPUNCHDOMAIN).append(Const.DOUYUGIFTIDEVENT)
//                            .append("&par_d=").append(date)
//                            .append("&plat=").append(Const.DOUYU)
//                            .append("&cate=").append(categorySec)
//                            .append("&rid=").append(rid)
//                            .append("&g_id=").append(gId)
//                            .append("&g_nm=").append(gName)
//                            .append("&g_ty=").append(gType)
//                            .append("&price=").append(gPrice)
//                            .append("&exp=").append(gExp)
//                            .append("&t_ran=").append(getRandomStr())
//                            .append("&c_time=").append(createTimeFormat.format(new Date())).toString());
                    douyuGiftObjs.add(douyuGift);

                }
            }
//            }
            page.setSkip(true);
        } catch (Exception e) {
            failedUrl.append(curUrl + ";  ");
            logger.info("process exception,url:{}" + curUrl);
            e.printStackTrace();
            if (++exCnt % 600==0) {
                MailTools.sendAlarmmail("douyudetailanchor 异常请求个数过多", "url: " + failedUrl.toString());
//                System.exit(1);
            }

        }
    }

    @Override
    public Site getSite() {
        if (useProxy){
            site.setHttpProxy(new HttpHost(Const.ABUYUNPHOST, Const.ABUYUNPORT));
        }
        return this.site.setSleepTime(500);
    }

    /**
     * 待测试,使用seleniumdownloader应该为使用代理
     *
     * @param args
     */
    public static void crawler(String[] args) {
        job = args[0];//douyuanchordetail
        date = args[1];
        hour = args[2];
        thread = 24;
        initParam(args);
        Const.GENERATORKEY = "H05972909IM78TAP";
        Const.GENERATORPASS = "36F7B5D8703A39C5";
        String firstUrl = "https://www.douyu.com/directory/all";
        String hivePaht = Const.COMPETITORDIR + "crawler_detail_anchor/" + date;
        Runtime.getRuntime().addShutdownHook(new Thread(new DouyuDetailShutDownHook()));
        long start = System.currentTimeMillis();
        Spider.create(new DouyuDetailAnchorProcessor()).thread(thread).addUrl(firstUrl).addPipeline(new ConsolePipeline()).setDownloader(new PandaDownloader()).run();//.setDownloader(new SeleniumDownloader(Const.CHROMEDRIVER))
        long end = System.currentTimeMillis();
        long secs = (end - start) / 1000 + 1;
        logger.info(job + ",用时:" + end + "-" + start + "=" + secs + "秒," + "请求数:" + requests + ",qps:" + (requests / secs));

//        CommonTools.writeAndMail(hivePaht, Const.DOUYUFINISHDETAIL, detailAnchors);
//        String giftIdPath = Const.COMPETITORDIR + "crawler_gift_id/" + date;

//        CommonTools.writeAndMail(giftIdPath, Const.DOUYUGIFTIDFINISHDETAIL, douyuGifts);

        executeMapResults();
    }

    private static void executeMapResults() {
        for (DetailAnchor detailAnchor : detailAnchorObjs) {
            detailAnchors.add(detailAnchor.toString());
        }
        for (GiftInfo giftInfo : douyuGiftObjs) {
            douyuGifts.add(giftInfo.toString());
        }
        String dirFile1 = new StringBuffer(Const.CRAWLER_DATA_DIR).append(date).append("/").append(hour).append("/").append(job).append("_").append(date).append("_").append(hour).append(randomStr).toString();
        CommonTools.write2Local(dirFile1,detailAnchors);
        String dirFile2 = new StringBuffer(Const.CRAWLER_DATA_DIR).append(date).append("/").append(hour).append("/").append("douyugiftid").append("_").append(date).append("_").append(hour).append(randomStr).toString();
        CommonTools.write2Local(dirFile2,douyuGifts);
    }

    private static class DouyuDetailShutDownHook implements Runnable {
        @Override
        public void run() {
            logger.info("writeSuccess:"+writeSuccess);
            if (!writeSuccess){
                executeMapResults();
            }
        }
    }
}
