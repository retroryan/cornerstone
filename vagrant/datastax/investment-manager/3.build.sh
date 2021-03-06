#!/usr/bin/env bash
set -x # echo on

SEEDS='127.0.0.1'

while [[ $# > 1 ]]
do
key="$1"
case $key in
    -s|--seeds)
    SEEDS="$2"
    shift
    ;;
    *)
    # unknown option
    ;;
esac
shift
done

SINGLE_SEED="${SEEDS%%,*}"
CFG=/cornerstone/web/datastax/investment-manager/StockData/src/Util.java
sed -i -e "s/NODE = .*/NODE = \"${SINGLE_SEED}\";/" ${CFG}

cd /cornerstone/web/datastax/investment-manager/StockData
mvn clean package
mvn install:install-file -DgroupId=StockData -DartifactId=StockData \
    -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar \
    -Dfile=/cornerstone/web/datastax/investment-manager/StockData/target/StockData-0.0.1-SNAPSHOT.jar

cd /cornerstone/web/datastax/investment-manager/Portfolio
mvn clean package

rm -rf /cache/installed/apache-tomcat-8.0.20/webapps/Portfolio
cp /cornerstone/web/datastax/investment-manager/Portfolio/target/Portfolio-0.0.1-SNAPSHOT.war /cache/installed/apache-tomcat-8.0.20/webapps/Portfolio.war


cqlsh $SINGLE_SEED -f /cornerstone/cql/datastax/investment-manager/stocks.cql
