package com.pandatv.pojo;

import com.pandatv.common.Const;
import com.pandatv.tools.CommonTools;

/**
 * Created by likaiqing on 2017/3/22.
 */
public class TwitchDetailChannel extends TwitchChannel {
    private String teamName;
    private Integer videos;//视频数
    private Integer following;//正在关注数

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Integer getVideos() {
        return videos;
    }

    public void setVideos(Integer videos) {
        this.videos = videos;
    }

    public Integer getFollowing() {
        return following;
    }

    public void setFollowing(Integer following) {
        this.following = following;
    }

    @Override
    public String toString() {
        StringBuffer bf = new StringBuffer();
        return bf.append(super.toString()).append(Const.SEP).append(this.getTeamName()).append(Const.SEP).append(this.getVideos()).append(Const.SEP).append(this.getFollowing()).toString();
    }

    public static void main(String[] args) {
        TwitchDetailChannel c = new TwitchDetailChannel();
        c.setTeamName("a");
        c.setId(1);
        c.setVideos(1);
        System.out.println(c);
    }

}
