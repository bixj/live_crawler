package com.pandatv.processor;

import com.pandatv.common.Const;
import com.pandatv.common.PandaProcessor;
import com.pandatv.downloader.credentials.PandaDownloader;
import com.pandatv.pojo.LianJiaLouPan;
import com.pandatv.tools.CommonTools;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.ConsolePipeline;
import us.codecraft.webmagic.selector.Html;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by likaiqing on 2017/8/18.
 */
public class LianJiaProcessor extends PandaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(LianJiaProcessor.class);
    private static final String firUrl = "http://bj.fang.lianjia.com";
    private static final String firListUrlEndTmp = "/loupan/";
    private static final String otherListUrlTmp = "/loupan/pg";
    private static final String cityKeyParam = "city";
    private static final String indexKeyParam = "index";
    private static final String pageKeyParam = "page";
    private static Set<String> lianJiaList = new HashSet<>();
    private static int exCnt;

    private static final DateTimeFormatter stf = DateTimeFormat.forPattern("MMdd");

    public static void crawler(String[] args) {
        job = args[0];//lianjia
        date = args[1];//20161114
        hour = args[2];
        Const.GENERATORKEY = "H05972909IM78TAP";
        Const.GENERATORPASS = "36F7B5D8703A39C5";
        long start = System.currentTimeMillis();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                logger.info("writeSuccess:" + writeSuccess);
                if (!writeSuccess) {
                    executeMapResults();
                }
            }
        }));
        Spider.create(new LianJiaProcessor()).thread(1).addUrl(firUrl).addPipeline(new ConsolePipeline()).setDownloader(new PandaDownloader()).run();
        long end = System.currentTimeMillis();
        long secs = (end - start) / 1000;
        logger.info(job + ",用时:" + end + "-" + start + "=" + secs + "秒," + "请求数:" + requests + ",qps:" + (requests / secs) + ",异常个数:" + exCnt + ",fialedurl:" + failedUrl.toString());
        executeMapResults();
    }

    @Override
    public void process(Page page) {
        String curUrl = page.getUrl().get();
        logger.info("process url:{}", curUrl);
        try {

            if (curUrl.equals(firUrl)) {
                /**
                 * 获取所有城市第一页url放入队列
                 */
                List<String> all = page.getHtml().xpath("//div[@class='fc-main clear']//div[@class='city-enum fl']/html()").all();
                for (String a : all) {
                    Html aHtml = new Html(a);
                    List<String> hrefs = aHtml.xpath("//a/@href").all();
                    List<String> citys = aHtml.xpath("//a/text()").all();
                    for (int i = 0; i < hrefs.size(); i++) {
                        page.addTargetRequest(new Request(hrefs.get(i) + firListUrlEndTmp).putExtra(cityKeyParam, citys.get(i)));
                    }
                }
            } else if (curUrl.endsWith(firListUrlEndTmp)) {
                /**
                 * 处理第一页列表页
                 */
                Html html = page.getHtml();
                int totalPage = 2;
                try {
                    String pageData = html.xpath("//div[@class='page-box house-lst-page-box']/@page-data").get();
                    String pageJson = pageData.substring(pageData.indexOf("{"), pageData.lastIndexOf("}") + 1);
                    JSONObject jsonObject = new JSONObject(pageJson);
                    totalPage = (int) jsonObject.get("totalPage");
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.info("解析页数出错,url:" + curUrl);
                    totalPage = Integer.parseInt(html.xpath("//div[@id='list-pagination']/@data-totalPage").get());
                }
                //将其他页url放入队列
                for (int i = 2; i <= totalPage; i++) {
                    page.addTargetRequest(new Request(new StringBuffer(curUrl).append("pg").append(i).append("/").toString()).putExtra(cityKeyParam, page.getRequest().getExtra(cityKeyParam)));
                }
                //获取详情页url放入队列
                parseListPage(curUrl, page);
            } else if (curUrl.contains(otherListUrlTmp)) {
                /**
                 * 处理非第一页的列表页
                 */
                //列表页获取详情页url放入队列
                parseListPage(curUrl, page);
            } else {
                /**
                 * 解析详情页数据
                 */
                Request request = page.getRequest();
                String id = curUrl.substring(curUrl.indexOf("/", curUrl.indexOf("loupan")) + 1, curUrl.lastIndexOf("/"));
                String city = request.getExtra(cityKeyParam).toString();
                String index = request.getExtra(indexKeyParam).toString();
                String pageNo = request.getExtra(pageKeyParam).toString();
                Html html = page.getHtml();
                List<String> as = html.xpath("//div[@class='breadcrumbs']/a/text()").all();
                String district = as.size() == 4 ? as.get(3).trim() : "";//区
                String status = html.xpath("//div[@class='box-left']/div[@class='box-left-top']/div[@class='name-box']/div[@class='state-div']/span[@class='state']/text()").get();
                String name = html.xpath("//div[@class='box-left']/div[@class='box-left-top']/div[@class='name-box']/a[@class='clear']/@title").get();
                int price = 0;
                try {
                    price = Integer.parseInt(html.xpath("//div[@class='box-left']/div[@class='box-left-top']/p[@class='jiage']/span[@class='junjia']/text()").get().trim());
                } catch (Exception e) {
                    logger.info("解析价格出错,url:" + curUrl);
                    e.printStackTrace();
                }
                String unitStr = html.xpath("//div[@class='box-left']/div[@class='box-left-top']/p[@class='jiage']/span[@class='yuan']/text()").get();
                String unit = "square";
                if (StringUtils.isNotEmpty(unitStr) && unitStr.contains("万")) {
                    price = price * 10000;
                    unit = "suite";
                }
                if (StringUtils.isNotEmpty(unitStr) && unitStr.contains("套")) {
                    unit = "suite";
                }
                String otherName = html.xpath("//div[@class='box-left']/div[@class='box-left-top']/p[@class='jiage']/span[@class='other-name']/text()").get();
                otherName = StringUtils.isNotEmpty(otherName) ? otherName.trim() : "";
                String updateTimeStr = html.xpath("//div[@class='box-left']/div[@class='box-left-top']/p[@class='update']/span/text()").get();
                int daysAgo = 1;
                if (StringUtils.isNotEmpty(updateTimeStr) && updateTimeStr.contains("月")) {
                    String trim = updateTimeStr.substring(updateTimeStr.indexOf("：") + 1).replaceAll("年|月|日", "").trim();
                    if (trim.length() == 4) {
                        updateTimeStr = stf.print(stf.parseDateTime(trim));
                        daysAgo = (new DateTime().dayOfYear().get()) - (stf.parseDateTime(trim).dayOfYear().get());
                    }
                } else if (StringUtils.isNotEmpty(updateTimeStr) && updateTimeStr.contains("天前")) {
                    daysAgo = Integer.parseInt(updateTimeStr.substring(updateTimeStr.indexOf("：") + 1).replace("天前", "").trim());
                    updateTimeStr = stf.print(new DateTime().minusDays(daysAgo));
                }
                String hourseType = "";
                List<String> all = html.xpath("//div[@class='bottom-info']/p[@class='wu-type manager']/span/text()").all();
                if (null != all && all.size() == 2) {
                    hourseType = all.get(1);
                } else {
                    hourseType = html.xpath("//div[@class='bottom-info']/p[@class='wu-type ']/span/text()").all().get(1);
                }
                String location = html.xpath("//div[@class='bottom-info']/p[@class='where manager']/span/@title").get();
                if (StringUtils.isEmpty(location)) {
                    location = html.xpath("//div[@class='bottom-info']/p[@class='where ']/span/@title").get();
                }
                String openDate = "";
                try {
                    List<String> whenAll = html.xpath("//div[@class='bottom-info']/p[@class='when manager']/span/text()").all();
                    if (null != whenAll && whenAll.size() == 2) {
                        openDate = whenAll.get(1).replaceAll("年|月|日", "").trim();
                    } else {
                        openDate = html.xpath("//div[@class='bottom-info']/p[@class='when ']/span/text()").all().get(1).replaceAll("年|月|日", "").trim();
                    }
                } catch (Exception e) {
                    location.indexOf("解析开盘日期出错,url:" + curUrl);
                    e.printStackTrace();
                }
                String lastActionTime = "";
                String lastActionTitle = "";
                String lastActionContent = "";
                try {
                    String dynamic = html.xpath("//div[@class='dynamic-wrap-left pull-left']/div[@class='dynamic-wrap-block clearfix']/div[@class='dynamic-block-detail pull-right']/html()").get();
                    Html dynamicHtml = new Html(dynamic);
                    lastActionTitle = dynamicHtml.xpath("//div[@class='dongtai-title']/text()").get();
                    lastActionContent = dynamicHtml.xpath("//a/text()").get().replace(" ", "").trim();
                    lastActionTime = dynamicHtml.xpath("//div[@class='dynamic-detail-time']/span/text()").get().replaceAll("年|月|日", "").trim();
                } catch (Exception e) {
                    location.indexOf("解析动态报错,url:" + curUrl);
                    e.printStackTrace();
                }
                LianJiaLouPan lianJiaLouPan = new LianJiaLouPan();
                lianJiaLouPan.setId(id);
                lianJiaLouPan.setCity(city);
                lianJiaLouPan.setDistrict(district);
                lianJiaLouPan.setIndex(index);
                lianJiaLouPan.setPageNo(pageNo);
                lianJiaLouPan.setName(name);
                lianJiaLouPan.setStatus(status);
                lianJiaLouPan.setPriceText("");
                lianJiaLouPan.setPriceStr("");
                lianJiaLouPan.setOtherPriceStr("");
                lianJiaLouPan.setUnit(unit);
                lianJiaLouPan.setIntPrice(price);
                lianJiaLouPan.setIntOtherPrice(0);
                lianJiaLouPan.setAroundPriceStr("");
                lianJiaLouPan.setIntAroundPrice(0);
                lianJiaLouPan.setAdvantage("");
                lianJiaLouPan.setAjust("");
                lianJiaLouPan.setLocation(location);
                lianJiaLouPan.setOpenDate(openDate);
                lianJiaLouPan.setOpenDateFormat(openDate);
                lianJiaLouPan.setCloseDate("");
                lianJiaLouPan.setCloseDateFormat("");
                lianJiaLouPan.setLastActionTime(lastActionTime);
                lianJiaLouPan.setLastActionTitle(lastActionTitle);
                lianJiaLouPan.setLastActionContent(lastActionContent);
                lianJiaLouPan.setUrl(curUrl);
                lianJiaLouPan.setOtherName(otherName);
                lianJiaLouPan.setUpdateTimeStr(updateTimeStr);
                lianJiaLouPan.setDaysAgo(daysAgo);
                lianJiaLouPan.setHourseType(hourseType);
                lianJiaList.add(lianJiaLouPan.toString());
            }
            page.setSkip(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析列表页,获取详情url加入队列
     *
     * @param curUrl
     * @param page
     */
    private void parseListPage(String curUrl, Page page) {
        int curPageNo = 1;
        if (curUrl.contains("pg")) {
            curPageNo = Integer.parseInt(curUrl.substring(curUrl.lastIndexOf("pg") + 2, curUrl.lastIndexOf("/")));
        }
        List<String> all = page.getHtml().xpath("//div[@class='list-wrap']/ul/li/div[@class='pic-panel']/html()").all();
        for (String a : all) {
            Html aHtml = new Html(a);
            String detailUrl = aHtml.xpath("//a/@href").get();
            String index = aHtml.xpath("//a/@data-index").get();
            page.addTargetRequest(new Request(detailUrl).putExtra(cityKeyParam, page.getRequest().getExtra(cityKeyParam)).putExtra(indexKeyParam, index).putExtra(pageKeyParam, curPageNo));
        }

    }

    @Override
    public Site getSite() {
        return this.site.setSleepTime(80);
    }

    private static void executeMapResults() {
        String dirFile = new StringBuffer("/home/likaiqing/data/lianjia/").append(date).append("_").append(hour).append("/").append(job).append("_").append(date).append("_").append(hour).append(randomStr).toString();
        CommonTools.write2Local(dirFile, lianJiaList);
    }
}
