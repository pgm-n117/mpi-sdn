#!/bin/bash
#export DOCKER_IMAGE='ryu-controller-alpine:latest'
export RYU_APP='RYU-topomanager'

#when rebuilding images:
#docker compose down --remove-orphans

#-a <image_name>: attach to terminal
#-c <image_name>: launch container and execute sdn controller
#-b <image_name>: build container

version_regex='Version:\s*([0-9]*\.[0-9]*\.[0-9]*)'
docker_version=`docker version`
min_version="20.10.13"
#echo ${docker_version}

if [[ $docker_version =~ $version_regex ]]
then

    version="${BASH_REMATCH[1]}"
    #echo ${version}
fi

COMPARE="${version} ${min_version}"

echo ${COMPARE}


RESULT=($(printf "%s\n" $COMPARE | sort -V))
echo ${RESULT[0]}




launch_container () {
    if [ ${RESULT[0]} != ${min_version} ]
    then
        docker-compose up -d $DOCKER_SERVICE
    else
        echo "docker compose updated version"
        docker compose up -d $DOCKER_SERVICE
    fi
}



#docker-compose up -d $DOCKER_SERVICE &&
if [ $# -gt 1 ]
echo $1

export DOCKER_SERVICE=$2
if [ "$2" = "ryu-controller-alpine" ]
then
    export TERMINAL='sh'
fi

if [ "$2" = "ryu-controller" ]
then
    export TERMINAL='bash'
fi

then    
    if [ "$1" = "-c" ]
    then

        APP_PATH=/home/$USER/$RYU_APP
        launch_container
        echo "- LAUNCHING DASHBOARD"
        docker exec -w $APP_PATH -dt $DOCKER_SERVICE python flow_viewer/flow_viewer.py
        echo "- LAUNCHING $RYU_APP"
        docker exec -w $APP_PATH -it $DOCKER_SERVICE python launcher.py

    fi
    if [ "$1" = "-a" ]
    then
        launch_container
        echo "- LAUNCHING $TERMINAL"
        docker exec -it $DOCKER_SERVICE $TERMINAL
    fi
    if [ "$1" = "-b" ]
    then
        echo "- BUILDING $DOCKER_SERVICE"
        if [ ${RESULT[0]} != ${min_version} ]
        then

            docker-compose build $DOCKER_SERVICE
        else
            echo "docker compose updated version"
            docker compose build --no-cache $DOCKER_SERVICE
        fi
    fi

fi
