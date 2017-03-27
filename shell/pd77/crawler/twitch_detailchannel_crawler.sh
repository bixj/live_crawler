#!/bin/bash

java=$(which java)
jar=/home/likaiqing/hive-tool/live_crawler.jar
date=`date +%Y%m%d`
hour=`date +%H`
task=twitchdetailchannel
$java -jar $jar ${task} ${date} ${hour}