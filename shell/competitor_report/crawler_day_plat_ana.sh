#!/bin/bash

date=$1
date=${date:=`date -d 'yesterday' +%Y%m%d`}

hive -e "
insert overwrite table panda_competitor.crawler_day_plat_analyse partition(par_date)
SELECT
  coalesce(ana.plat, rec.plat)            plat,
  coalesce(ana.category, rec.category)    category,
  coalesce(ana.max_pcu, rec.max_pcu)              max_pcu,
  coalesce(ana.live_times, rec.rec_times) live_times,
  coalesce(ana.duration, rec.duration)    duraion,
  coalesce(ana.weight, rec.weight)        weight,
  coalesce(ana.followers, rec.followers)  followers,
  coalesce(rec.rec_times, 0)              rec_times,
  '$date'
FROM
  (
    SELECT
      coalesce(dur.plat, pcu.plat)             plat,
      coalesce(dur.category, pcu.category)     category,
      coalesce(dur.live_times, pcu.live_times) live_times,
      coalesce(dur.duration, pcu.duration)     duration,
      coalesce(dur.max_pcu, pcu.max_pcu)       max_pcu,
      coalesce(pcu.weight, 0)                  weight,
      coalesce(pcu.followers, 0)               followers
    FROM
      (
        SELECT
          plat,
          category,
          sum(anchors)          live_times,
          round(sum(anchors) / 60 * 5,2) duration,
          max(pcu)              max_pcu
        FROM
          (
            SELECT
              task_random,
              split(task, 'anchor') [0] plat,
              category,
              count(DISTINCT rid)       anchors,
              sum(populary_num)         pcu
            FROM panda_competitor.crawler_anchor
            WHERE par_date = '$date'  and task like '%anchor'
            GROUP BY task_random, split(task, 'anchor') [0], category
          ) t_r
        GROUP BY plat, category
      ) dur
      FULL JOIN
      (
        SELECT
          plat,
          category,
          sum(anchors)                   live_times,
          CASE WHEN plat = 'douyu'
            THEN round(sum(anchors) / 60,2)
          ELSE round(sum(anchors) / 60 * 5,2) END duration,
          max(pcu)                       max_pcu,
          max(weight)                    weight,
          max(followers)                 followers
        FROM
          (
            SELECT
              task_random,
              split(task, 'detailanchor') [0] plat,
              category_sec              category,
              count(DISTINCT rid)       anchors,
              sum(online_num)           pcu,
              sum(weight_num)           weight,
              sum(follower_num)         followers
            FROM
              panda_competitor.crawler_detail_anchor
            WHERE par_date = '$date' and task like '%detailanchor'
            GROUP BY task_random, split(task, 'detailanchor') [0], category_sec
          ) d
        GROUP BY plat, category
      ) pcu
        ON dur.plat = pcu.plat AND dur.category = pcu.category
  ) ana
  FULL JOIN
  (
    SELECT
      plat,
      category,
      sum(anchors)          rec_times,
      round(sum(anchors) / 60 * 5,2) duration,
      max(max_pcu)          max_pcu,
      max(weight)           weight,
      max(followers)        followers
    FROM
      (
        SELECT
          task_random,
          split(task, 'index') [0] plat,
          category_sec             category,
          count(DISTINCT rid)      anchors,
          max(online_num)          max_pcu,
          max(weight_num)          weight,
          max(follower_num)        followers
        FROM
          panda_competitor.crawler_indexrec_detail_anchor
        WHERE par_date = '$date' and task like '%indexrec'
        GROUP BY task_random, split(task, 'index') [0], category_sec
      ) rec_tr
    GROUP BY plat, category
  ) rec
    ON ana.plat = rec.plat AND ana.category = rec.category;
"