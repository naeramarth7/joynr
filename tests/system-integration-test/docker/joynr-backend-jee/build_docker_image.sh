#!/bin/bash

if [ -d target ]; then
	rm -Rf target
fi
mkdir target

WAR_FILE=../../../../java/backend-services/discovery-directory-jee/target/discovery-directory-jee*.war
if [ ! -f $WAR_FILE ]; then
    echo "Missing $WAR_FILE build artifact. Can't proceed."
    exit -1
fi
cp $WAR_FILE target/discovery-directory-jee.war

if [ -z "$(docker version 2>/dev/null)" ]; then
	echo "The docker command seems to be unavailable."
	exit -1
fi

docker build -t joynr-backend-jee:latest .
docker images | grep '<none' | awk '{print $3}' | xargs docker rmi -f 2>/dev/null
rm -Rf target