package com.pandatv.processor;

import com.jayway.jsonpath.JsonPath;
import com.pandatv.common.Const;
import com.pandatv.common.PandaProcessor;
import com.pandatv.downloader.credentials.PandaDownloader;
import com.pandatv.pojo.DetailAnchor;
import com.pandatv.tools.CommonTools;
import com.pandatv.tools.DateTools;
import com.pandatv.tools.HiveJDBCConnect;
import com.pandatv.tools.IOTools;
import com.pandatv.work.MailTools;
import net.minidev.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.yarn.util.RackResolver;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.ConsolePipeline;
import us.codecraft.webmagic.selector.Html;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by likaiqing on 2016/11/29.
 */
public class IndexRecProcessor extends PandaProcessor {
    private static List<String> douyuRecAnchors = new ArrayList<>();
    private static List<String> huyaRecAnchors = new ArrayList<>();
    private static StringBuffer failedUrl = new StringBuffer("failedUrl:");
    private static StringBuffer timeOutUrl = new StringBuffer("timeOutUrl:");
    private static String douyuDetailUrltmp = "http://open.douyucdn.cn/api/RoomApi/room/";
    private static String douyuIndex;
    private static String huyaIndex;
    private static int exCnt;

    @Override
    public void process(Page page) {
        String curUrl = page.getUrl().toString();
        try {
            if (curUrl.equals(douyuIndex)) {
                List<String> all = page.getHtml().xpath("//div[@class='c-items']/ul/li/@data-id").all();
                for (String rid : all) {
                    page.addTargetRequest(douyuDetailUrltmp + rid);
                }
            } else if (curUrl.equals(huyaIndex)) {
                String js = page.getHtml().getDocument().getElementsByAttributeValue("data-fixed", "true").get(5).toString();
                String recJson = js.substring(js.indexOf("var slides=") + 12, js.indexOf("var slideMainIndex")).trim().replace(";", "");
                JSONArray jsonArray = JsonPath.read(recJson, "$");
                for (Object rec : jsonArray) {
                    String rid = JsonPath.read(rec, "$.privateHost").toString();
                    Request request = new Request(huyaIndex + rid);
                    request.putExtra("rid",rid);
                    page.addTargetRequest(request);
                }

            } else if (curUrl.startsWith(douyuDetailUrltmp)) {
                Object cycleTriedTimes = page.getRequest().getExtra("_cycle_tried_times");
                if (null != cycleTriedTimes && (int) cycleTriedTimes >= Const.CYCLERETRYTIMES - 1) {
                    timeOutUrl.append(curUrl).append(";");
                }
                String json = page.getJson().get();
                String rid = JsonPath.read(json, "$.data.room_id");
                String name = JsonPath.read(json, "$.data.owner_name");
                String title = JsonPath.read(json, "$.data.room_name");
                String categorySec = JsonPath.read(json, "$.data.cate_name");
                int viewerStr = JsonPath.read(json, "$.data.online");
                String followerStr = JsonPath.read(json, "$.data.fans_num");
                String weightStr = JsonPath.read(json, "$.data.owner_weight");
                String lastStartTime = JsonPath.read(json, "$.data.start_time");
                DetailAnchor detailAnchor = new DetailAnchor();
                detailAnchor.setRid(rid);
                detailAnchor.setName(name);
                detailAnchor.setTitle(title);
                detailAnchor.setCategorySec(categorySec);
                detailAnchor.setViewerNum(viewerStr);
                detailAnchor.setFollowerNum(Integer.parseInt(followerStr));
                detailAnchor.setWeightNum(CommonTools.getDouyuWeight(weightStr));
                detailAnchor.setUrl(curUrl);
                detailAnchor.setLastStartTime(lastStartTime);
                detailAnchor.setJob(Const.DOUYUINDEXREC);
                douyuRecAnchors.add(detailAnchor.toString());
            } else if (curUrl.startsWith(huyaIndex) && !curUrl.endsWith("/")) {
                Object cycleTriedTimes = page.getRequest().getExtra("_cycle_tried_times");
                if (null != cycleTriedTimes && (int) cycleTriedTimes >= Const.CYCLERETRYTIMES - 1) {
                    timeOutUrl.append(curUrl).append(";");
                }
                Html html = page.getHtml();
                String rid = null == page.getRequest().getExtra("rid") ? "" : page.getRequest().getExtra("rid").toString();
                String name = html.xpath("//span[@class='host-name']/text()").get();
                String title = html.xpath("//h1[@class='host-title']/text()").get();
                String categoryFir = "";
                String categorySec = "";
                List<String> category = html.xpath("//span[@class='host-channel']/a/text()").all();
                if (category.size() == 2) {
                    categoryFir = category.get(0);
                    categorySec = category.get(1);
                } else if (category.size() == 1) {
                    categoryFir = category.get(0);
                    categorySec = category.get(0);
                }
                String viewerStr = html.xpath("//span[@class='host-spectator']/em/text()").get();
                if (!StringUtils.isEmpty(viewerStr) && viewerStr.contains(",")) {
                    viewerStr = viewerStr.replace(",", "");
                }
                String followerStr = html.xpath("//div[@id='activityCount']/text()").get();
                String tag = html.xpath("//span[@class='host-channel']/a/text()").all().toString();//逗号分隔
                String notice = html.xpath("//div[@class='notice-cont']/text()").get();
                DetailAnchor detailAnchor = new DetailAnchor();
                detailAnchor.setRid(rid);
                detailAnchor.setName(name);
                detailAnchor.setTitle(title);
                detailAnchor.setCategoryFir(categoryFir);
                detailAnchor.setCategorySec(categorySec);
                detailAnchor.setViewerNum(StringUtils.isEmpty(viewerStr) ? 0 : Integer.parseInt(viewerStr));
                detailAnchor.setFollowerNum(StringUtils.isEmpty(followerStr) ? 0 : Integer.parseInt(followerStr));
                detailAnchor.setTag(tag);
                detailAnchor.setNotice(notice);
                detailAnchor.setJob(Const.HUYAINDEXREC);
                detailAnchor.setUrl(curUrl);
                huyaRecAnchors.add(detailAnchor.toString());
            }
        } catch (Exception e) {
            failedUrl.append(curUrl + ";  ");
            e.printStackTrace();
            if (exCnt++ > Const.EXTOTAL) {
                MailTools.sendAlarmmail("斗鱼首页推荐", "url: " + curUrl);
                System.exit(1);
            }

        }
        page.setSkip(true);
    }

    @Override
    public Site getSite() {
//        return CommonTools.getAbuyunSite(site).setSleepTime(500);
        return this.site;
    }

    public static void crawler(String[] args) {
        String from = DateTools.getCurDate();
        String date = args[1];
        String hour = args[2];
        String curMinute = DateTools.getCurMinute();
        long s = System.currentTimeMillis();
        douyuIndex = "https://www.douyu.com/";
        huyaIndex = "http://www.huya.com/";
        HiveJDBCConnect hive = new HiveJDBCConnect();
        String hivePaht = Const.HIVEDIR + "panda_detail_anchor_crawler/" + date + hour;
        Spider.create(new IndexRecProcessor()).thread(1).addUrl(douyuIndex,huyaIndex).addPipeline(new ConsolePipeline()).run();
        try {
            if (douyuRecAnchors.size() > 0) {
                hive.write2(hivePaht, douyuRecAnchors, Const.DOUYUINDEXREC, curMinute);
            }
        } catch (Exception e) {
            e.printStackTrace();
            BufferedWriter bw = IOTools.getBW("/tmp/douyurecanchors" + date + hour + curMinute);
            IOTools.writeList(douyuRecAnchors, bw);
            MailTools.sendAlarmmail("斗鱼hive.write异常", e.getMessage().toString());
        }
        try {
            if (huyaRecAnchors.size() > 0) {
                hive.write2(hivePaht, huyaRecAnchors, Const.HUYAINDEXREC, curMinute);
            }
        } catch (Exception e) {
            e.printStackTrace();
            BufferedWriter bw = IOTools.getBW("/tmp/huyarecanchors" + date + hour + curMinute);
            IOTools.writeList(huyaRecAnchors, bw);
            MailTools.sendAlarmmail("斗鱼hive.write异常", e.getMessage().toString());
        }
        long e = System.currentTimeMillis();
        long time = e - s;
        String to = DateTools.getCurDate();
        MailTools.sendTaskMail(Const.INDEXRECEXIT + date + hour, from + "<-->" + to, time + "毫秒;", douyuRecAnchors.size() + huyaRecAnchors.size(), timeOutUrl, failedUrl);
    }
}