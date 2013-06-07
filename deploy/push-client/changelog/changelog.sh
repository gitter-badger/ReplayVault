#!/bin/bash
ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CHANGELOG=$ROOT/../../../changelog.txt
url="https://chiselapp.com/user/Arg/repository/ReplayVault/rptview?tablist=1&rn=1"
sep='|'
curl $url 2>/dev/null | sed 1d | tr '\t' $sep | tr -s $sep | awk -F$sep -f $ROOT/parser.awk | tac >> $ROOT/changes.tmp
cat $ROOT/changes.tmp >> $ROOT/newChangelog.tmp
cat $CHANGELOG >> $ROOT/newChangelog.tmp
cat $ROOT/newChangelog.tmp | tac | awk '!_[$0]++' | tac > $ROOT/changelog.tmp

old=`wc -l $CHANGELOG | tr -s ' ' | cut -d\  -f1`
new=`wc -l $ROOT/changelog.tmp | tr -s ' ' | cut -d\  -f1`

if [ $new -gt $old ] ; then # check if there are some changes between proposed changelog and current changelog
	echo "Generating new changelog!"
        date -u > $CHANGELOG
        cat $ROOT/changelog.tmp >> $CHANGELOG
fi

rm $ROOT/*.tmp
