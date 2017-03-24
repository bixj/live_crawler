#!/bin/bash

java=$(which java)
jar=/home/likaiqing/hive-tool/live_twitch_crawler.jar
date=`date +%Y%m%d`
hour=`date +%H`
task=twitchcateandlist
$java -jar $jar ${task} ${date} ${hour}