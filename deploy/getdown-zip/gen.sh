#!/bin/sh
ROOT=~/workspace/replayvault-deploy/getdown-zip


cp "$ROOT"/../getdown.txt $ROOT
cd $ROOT
zip uploader-loader.zip getdown.txt uploader-loader.exe
zip uploader-loader-unix.zip getdown.txt getdown-1.1.jar unix-run.sh

s3cmd put --acl-public --guess-mime-type uploader-loader.zip s3://replayvault/uploader-loader.zip
s3cmd put --acl-public --guess-mime-type uploader-loader-unix.zip s3://replayvault/uploader-loader-unix.zip
s3cmd put --acl-public --guess-mime-type Replay+Vault+launcher.dmg s3://replayvault/Replay+Vault+launcher.dmg

