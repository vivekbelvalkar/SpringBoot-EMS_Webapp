#!/bin/bash
nohup java -Xms64m -Xmx128m -jar /opt/app/app.jar > /opt/app/app.log 2>&1 &