#!/bin/sh

RETVAL=0

stop_docker() {
  docker-compose down
  echo ""
  echo "-------------------"
  echo ""
}

start_docker() {
  docker-compose up -d
  echo ""
  echo "-------------------"
  echo ""
}

restart_docker() {
  stop_docker
  start_docker
}

case "$1" in
    start)
	start_docker
	;;
    stop)
	stop_docker
	;;
    restart)
	restart_docker
	;;
    *)
	echo $"Usage: $prog {start|stop|restart}"
	exit 1
esac

exit $RETVAL
