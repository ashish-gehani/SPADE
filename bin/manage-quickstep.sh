#!/bin/bash

spade_home_path="$(cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd)"
quickstep_binary_path="${spade_home_path}/bin/quickstep"

### FUNCTIONS

print_help(){
  echo ""
  echo "Manage quickstep database"
  echo ""
  echo "Usage:"
  echo "  manage-quickstep start [arguments] | stop | status | install"
  echo ""
  echo "'manage-quickstep start'  : Start quickstep database"
  echo "'manage-quickstep stop'   : Stop quickstep database"
  echo "'manage-quickstep status' : Show quickstep database status"
  echo "'manage-quickstep install' : Build & install quickstep database"
  echo ""
  echo "Arguments:"
  echo "  For 'start' command:"
  echo "    -p|--path <dir>: Path to valid database directory. Created if it does not exist. Default: '${spade_home_path}/db/quickstep'"
  echo "    -m|--memory <MBs>: Memory in MBs to use for the database. For more details see Quickstep database help"
  echo "    -c|--cli: Connect to database CLI. By default starts database to listen for socket connections in the background"
  echo "    -P|--port <number>: Port to start database at (in non-cli mode). Default: 3000"
  echo ""
}

get_quickstep_pids(){
  pids=""
  while IFS= read -r line
  do
    pid=$(echo "$line" | xargs | cut -d ' ' -f 2)
    pids="$pids $pid"
  done < <(ps aux | grep "[b]in/quickstep")
  echo $pids
}

### THERE MUST BE A COMMAND

cmd="$1"
[ -z "$cmd" ] && print_help && exit 1
shift

### INSTALL COMMAND

if [ "$cmd" = "install" ]
then
  "${spade_home_path}/bin/installQuickstep"
  exit 0
fi

### VERIFY EXECUTABLE PRESENCE

if [ ! -f "$quickstep_binary_path" ]
then
  echo "Error: No Quickstep database binary at path: ${quickstep_binary_path}."
  echo "Please build Quickstep using the command './bin/installQuickstep' if not already done so."
  exit 1
fi

### STATUS COMMAND

if [ "$cmd" = "status" ]
then
  pids=$(get_quickstep_pids)
  [ ! -z "$pids" ] && echo "Running" || echo "Stopped"
  exit 1
fi

### STOP COMMAND

if [ "$cmd" = "stop" ]
then
  pids=$(get_quickstep_pids)
  [ ! -z "$pids" ] && echo "Sending stop signal to quickstep process(es): '$pids'" && kill -9 $pids
  exit 1
fi

### ALL OTHER COMMANDS

if [ "$cmd" = "start" ]
then
  :
else
  echo "Unknown command: '$cmd'"
  print_help
  exit 1
fi

### PARSE GLOBAL VARIABLES

db_path="${spade_home_path}/db/quickstep"
db_memory=
db_mode=socket
db_port=3000

while [[ $# -gt 0 ]]
do
  key="$1"
  case $key in
    -p|--path)
      db_path="$2"
      shift
      shift
      ;;
    -m|--memory)
      db_memory="$2"
      shift
      shift
      ;;
    -c|--cli)
      db_mode=local
      shift
      ;;
    -P|--port)
      db_port="$2"
      shift
      shift
      ;;
    *)
      echo "Unknown or incorrectly positioned argument: $key"
      print_help
      exit 1
      ;;
  esac
done

### CREATE EXECUTABLE ARGUMENTS

buffer_pool_arg=
if [ ! -z "$db_memory" ]
then
  if [[ "$db_memory" =~ ^[0-9]+$ ]]
  then
    number_of_two_mbs=$(($db_memory / 2))
    buffer_pool_arg="-buffer_pool_slots=$number_of_two_mbs"
  else
    echo "Non-numeric '-m|--memory' value"
    exit 1
  fi
fi

### CREATE DATABASE IF IT DOES NOT EXIST

if [ ! -e "$db_path" ];
then
  echo "Initializing empty Quickstep storage directory ..."
  "$quickstep_binary_path" -initialize_db -storage_path="$db_path" <<< "" 1>/dev/null
  if [ ! -d "$db_path" ]
  then
    echo "Error: Failed to initialize Quickstep storage directory"
    exit 1
  fi
fi

### START DATABASE IN CLI MODE

if [ "$db_mode" = local ]
then
  "$quickstep_binary_path" -storage_path="$db_path" -mode=$db_mode $buffer_pool_arg -cli_socket_port="$db_port" -display_timing=false
  exit 0
fi

### START DATABASE IN SERVER/SOCKET MODE

nohup "$quickstep_binary_path" -storage_path="$db_path" -mode=$db_mode $buffer_pool_arg -cli_socket_port="$db_port" -display_timing=false 2>&1 &
