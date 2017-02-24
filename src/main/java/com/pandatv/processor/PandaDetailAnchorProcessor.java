package com.pandatv.processor;

import com.jayway.jsonpath.JsonPath;
import com.pandatv.common.Const;
import com.pandatv.common.PandaProcessor;
import com.pandatv.downloader.credentials.PandaDownloader;
import com.pandatv.pojo.DetailAnchor;
import com.pandatv.tools.CommonTools;
import net.minidev.json.JSONArray;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.ConsolePipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by likaiqing on 2016/12/14.
 */
public class PandaDetailAnchorProcessor extends PandaProcessor {
    private static final Map<String, DetailAnchor> map = new HashMap<>();
    private static final String detailUrlTmp = "http://www.panda.tv/";
    private static final String followJsonPrefex = "http://www.panda.tv/room_followinfo?roomid=";
    private static final String v2DetailJsonPrefex = "http://www.panda.tv/api_room_v2?roomid=";
    private static final Logger logger = LoggerFactory.getLogger(HuyaDetailAnchorProcessor.class);

    @Override
    public void process(Page page) {
        String curUrl = page.getUrl().get();
        logger.info("process url:{}", curUrl);
        try {
            if (curUrl.startsWith("http://www.panda.tv/live_lists?status=2&order=person_num&pagenum=120&pageno=")) {
                JSONArray items = JsonPath.read(page.getJson().get(), "$.data.items");
                if (items.size() > 0) {
                    int equalIndex = curUrl.lastIndexOf("=");
                    int curPage = Integer.parseInt(curUrl.substring(equalIndex + 1));
                    page.addTargetRequest(curUrl.substring(0, equalIndex) + "=" + (curPage + 1));
                    for (Object obj : items) {
                        String rid = JsonPath.read(obj, "$.id");
                        String name = JsonPath.read(obj, "$.userinfo.nickName");
                        String title = JsonPath.read(obj, "$.name");
                        String category = JsonPath.read(obj, "$.classification.cname");
                        String popularitiyStr = JsonPath.read(obj, "$.person_num");
                        int popularitiyNum = Integer.parseInt(popularitiyStr);
                        DetailAnchor detailAnchor = new DetailAnchor();
                        detailAnchor.setRid(rid);
                        detailAnchor.setName(name);
                        detailAnchor.setTitle(title);
                        detailAnchor.setCategorySec(category);
                        detailAnchor.setViewerNum(popularitiyNum);
                        detailAnchor.setJob(job);
                        detailAnchor.setUrl(curUrl);
                        map.put(rid, detailAnchor);
                        page.addTargetRequest(new Request(detailUrlTmp + rid).putExtra("rid", rid));
                    }
                }
            } else if (curUrl.startsWith(followJsonPrefex)) {//获取订阅数
                String rid = page.getRequest().getExtra("rid").toString();
                DetailAnchor detailAnchor = map.get(rid);
                int follow = JsonPath.read(page.getJson().toString(), "$.data.fans");
                detailAnchor.setFollowerNum(follow);
            } else if (curUrl.startsWith(v2DetailJsonPrefex)) {
                String jsonStr = page.getJson().get();
                String rid = page.getRequest().getExtra("rid").toString();
                String bambooStr = JsonPath.read(jsonStr, "$.data.hostinfo.bamboos").toString();
                DetailAnchor detailAnchor = map.get(rid);
                detailAnchor.setWeightNum(Long.parseLong(bambooStr));
            } else if (curUrl.startsWith(detailUrlTmp)) {//设置体重window._config_roominfo bamboos
                String rid = page.getRequest().getExtra("rid").toString();
                DetailAnchor detailAnchor = map.get(rid);
                Elements scripts = page.getHtml().getDocument().getElementsByTag("script");
                boolean has = false;
                for (Element script : scripts) {
                    if (script.toString().contains("window._config_roominfo")) {
                        String scrStr = script.toString();
                        int bamStart = scrStr.indexOf("\"bamboos\":\"") + 11;
                        int bamEnd = scrStr.indexOf("\"}", bamStart);
                        String weightStr = scrStr.substring(bamStart, bamEnd);
                        detailAnchor.setWeightNum(Long.parseLong(weightStr));
                        has = true;
                    }
                }
                if (!has) {//源码没有window._config_roominfo信息
                    page.addTargetRequest(new Request(v2DetailJsonPrefex + rid).putExtra("rid", rid));
                }
                page.addTargetRequest(new Request(followJsonPrefex + rid).putExtra("rid", rid));
            }
            page.setSkip(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Site getSite() {
        return this.site.setHttpProxy(null);
    }

    public static void crawler(String[] args) {
        String firUrl = "http://www.panda.tv/live_lists?status=2&order=person_num&pagenum=120&pageno=1";
        job = args[0];//pandaanchor
        date = args[1];//20161114
        hour = args[2];//10
        if (args.length == 4 && args[3].contains(",")) {
            mailHours = args[3];
        }
        String hivePaht = Const.COMPETITORDIR + "crawler_detail_anchor/" + date;
        Spider.create(new PandaDetailAnchorProcessor()).thread(20).addUrl(firUrl).addPipeline(new ConsolePipeline()).setDownloader(new PandaDownloader()).run();
        for (Map.Entry<String, DetailAnchor> entry : map.entrySet()) {
            detailAnchors.add(entry.getValue().toString());
        }
        CommonTools.writeAndMail(hivePaht, Const.PANDAANCHORFINISHDETAIL, detailAnchors);
    }
}
