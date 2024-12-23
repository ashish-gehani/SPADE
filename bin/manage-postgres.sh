#!/bin/bash

###
# GLOBALS
###
spade_home_path="$(cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd)"
cfg_path="${spade_home_path}/cfg/spade.storage.PostgreSQL.config"
os_current=""
psql_usr="postgres"
install_success=0
purge=0

###
# CONSTANTS
###
os_linux="Linux"
os_darwin="Darwin"
os_fedora="Fedora"

# Ubuntu
pkg_name_linux="postgresql-13"
pkg_name_suppl_linux="postgresql-client-13"
src_file_linux="/etc/apt/sources.list.d/pgdg.list"
key_name_linux="ACCC4CF8"
key_url_linux="https://www.postgresql.org/media/keys/${key_name_linux}.asc"

# Darwin
brew_version="Homebrew 3.2.11-63-g1e8e57c"
pkg_name_darwin="postgresql"
pkg_file_darwin="postgresql.rb"
pkg_url_darwin="https://raw.githubusercontent.com/Homebrew/homebrew-core/c626dd3c96afff9cc8f95e94dde76931181716ef/Formula/${pkg_file_darwin}"
data_dir_darwin="/usr/local/var/postgres"

# Fedora
pkg_name_fedora="postgresql-server-13.4"
data_dir_fedora="/var/lib/pgsql"
access_file_fedora="${data_dir_fedora}/data/pg_hba.conf"

config_key_db_name="database"
config_key_db_user="username"
config_key_db_pass="password"
###
# FUNCTIONS
###
print_help(){
  echo ""
  echo "Manage Postgres database"
  echo ""
  echo "Usage:"
  echo "  manage-postgres.sh install | uninstall | info"
  echo ""
  echo "'manage-postgres.sh install <arguments>'   : Install Postgres database. 'setup' command auto-runs if install successful"
  echo "'manage-postgres.sh uninstall <arguments>' : Uninstall Postgres database and delete ALL existing databases"
  echo "'manage-postgres.sh setup <arguments>'     : Setup Postgres user and database for SPADE"
  echo "'manage-postgres.sh info'                  : Display Postgres database info"
  echo "'manage-postgres.sh connect'               : Connect to Postgres database"
  echo "'manage-postgres.sh drop <arguments>'      : Drop database"
  echo "'manage-postgres.sh clear <arguments>'     : Clear database"
  echo ""
  echo "Arguments:"
  echo "  For 'install'|'setup'|'clear'|'drop' command:"
  echo "    -c|--config <file>: Path to SPADE PostgreSQL storage configuration. Default: '${cfg_path}'"
  echo "  For 'uninstall' command:"
  echo "    -p|--purge: Delete data on uninstall"
  echo ""
}

is_installed(){
  if [ "$os_current" = "$os_darwin" ]
  then
    brew list "$pkg_name_darwin" &>/dev/null
    error=$?
    [ "$error" -eq 1 ] && echo 0 || echo 1
  elif [ "$os_current" = "$os_fedora" ]
  then
    dnf list --installed "${pkg_name_fedora}" &>/dev/null && echo 1 || echo 0
  elif [ "$os_current" = "$os_linux" ]
  then
    apt list --installed 2>&1 | grep -q "$pkg_name_linux" && echo 1 || echo 0
  fi
}

get_value_for_key(){
  path="$1"
  key="$2"
  output=$(grep "^[[:space:]]*${key}[[:space:]]*=" "$path" | tail -1 | cut -d '=' -f 2- | xargs)
  echo $output
}

must_get_value_for_key(){
  from_cfg_path="$1"
  key="$2"
  [ ! -f "$from_cfg_path" ] && echo "Configuration file not found: '$from_cfg_path'" && exit 1
  value=$(get_value_for_key "$from_cfg_path" "$key")
  [ -z "$value" ] && echo "Missing '$key' key in configuration file: $from_cfg_path" && exit 1
  echo $value
}

get_psql_command_prefix(){
  db_manage_prefix=""

  if [ "$os_current" = "$os_darwin" ]
  then
    db_manage_prefix="psql -U $psql_usr"
  elif [ "$os_current" = "$os_fedora" ]
  then
    db_manage_prefix="sudo -u $psql_usr psql"
  elif [ "$os_current" = "$os_linux" ]
  then
    db_manage_prefix="sudo -u $psql_usr psql"
  fi
  echo "$db_manage_prefix"
}

exec_psql_command(){
  command="$1"
  db_manage_prefix=$(get_psql_command_prefix)

  eval "$db_manage_prefix $command"
}

is_user_present(){
  db_user="$1"
  output=$(exec_psql_command "--no-align --tuples-only --command \"select 1 from pg_roles where rolname='${db_user}';\"")
  if [ ! -z "$output" ]; then
    if [ "$output" -eq 1 ]; then
      echo 1
      return 1
    fi
  fi
  echo 0
  return 0
}

is_db_present(){
  db_name="$1"
  output=$(exec_psql_command "--no-align --tuples-only --command \"select 1 from pg_database where datname='${db_name}';\"")
  if [ ! -z "$output" ]; then
    if [ "$output" -eq 1 ]; then
      echo 1
      return 1
    fi
  fi
  echo 0
  return 0
}

must_get_db_name(){
  from_cfg_path="$1"
  output=$(must_get_value_for_key "$from_cfg_path" "$config_key_db_name")
  echo "$output"
}

must_get_db_user(){
  from_cfg_path="$1"
  output=$(must_get_value_for_key "$from_cfg_path" "$config_key_db_user")
  echo "$output"
}

must_get_db_pass(){
  from_cfg_path="$1"
  output=$(must_get_value_for_key "$from_cfg_path" "$config_key_db_pass")
  echo "$output"
}

###
# THERE MUST BE A COMMAND
###
cmd="$1"
[ -z "$cmd" ] && print_help && exit 1
shift
[[ "$cmd" != "install" ]] && \
  [[ "$cmd" != "uninstall" ]] && \
  [[ "$cmd" != "setup" ]] && \
  [[ "$cmd" != "info" ]] && \
  [[ "$cmd" != "connect" ]] && \
  [[ "$cmd" != "clear" ]] && \
  [[ "$cmd" != "drop" ]] && \
  print_help && exit 1

###
# PARSE ARGUMENTS
###
while [[ $# -gt 0 ]]
do
  key="$1"
  case $key in
    -c|--config)
      cfg_path="$2"
      shift
      shift
      ;;
    -p|--purge)
      purge=1
      shift
      ;;
    *)
      echo "Unknown or incorrectly positioned argument: $key"
      print_help
      exit 1
      ;;
  esac
done

###
# RESOLVE PLATFORM
###
os_name=$(uname)
if [ "$os_name" = "$os_darwin" ]
then
  os_current="$os_darwin"
elif [ "$os_name" = "$os_linux" ]
then
  os_release_file="/etc/os-release"
  os_id=$(grep "^ID=" "$os_release_file" | cut -d '=' -f 2)
  if [ "$os_id" = "fedora" ]
  then
    os_current="$os_fedora"
  elif [ "$os_id" = "ubuntu" ]
  then
    os_current="$os_linux"
  fi
fi

[ -z "$os_current" ] && echo "ERROR: Unknown platform. Allowed: ${os_linux}, ${os_darwin}, ${os_fedora}" && exit 1

###
# RESOLVE PLATFORM REQUIREMENTS
###
if [ "$os_current" = "$os_darwin" ]
then
  which_brew=$(which brew)
  error=$?
  [ "$error" -eq 1 ] && echo "ERROR: Missing 'brew' (${brew_version}). Required for PostgreSQL management" && exit 1
elif [ "$os_current" = "$os_fedora" ]
then
  which_dnf=$(which dnf)
  error=$?
  [ "$error" -eq 1 ] && echo "ERROR: Missing 'dnf'. Required for PostgreSQL management" && exit 1
elif [ "$os_current" = "$os_linux" ]
then
  which_apt_get=$(which apt-get)
  error=$?
  [ "$error" -eq 1 ] && echo "ERROR: Missing 'apt-get'. Required for PostgreSQL management" && exit 1
fi

###
#  CONNECT COMMAND
###
if [ "$cmd" = "connect" ]; then
  db_name=$(must_get_db_name "$cfg_path")
  db_manage_prefix=$(get_psql_command_prefix)
  $db_manage_prefix -d $db_name
  exit 0
fi

###
# INFO COMMAND
###
if [ "$cmd" = "info" ]
then
  installed=$(is_installed)
  [ "$installed" -eq 0 ] && echo "PostgreSQL package is not installed" && exit 0

  if [ "$os_current" = "$os_darwin" ]
  then
    brew info "$pkg_name_darwin"
  elif [ "$os_current" = "$os_fedora" ]
  then
    dnf list --installed "${pkg_name_fedora}"
  elif [ "$os_current" = "$os_linux" ]
  then
    apt list --installed 2>/dev/null | grep "$pkg_name_linux"
  fi

  echo ""

  db_name=$(must_get_db_name "$cfg_path")
  db_user=$(must_get_db_user "$cfg_path")

  output=$(is_user_present "$db_user")
  [ "$output" -eq 1 ] && echo "User exists: ${db_user}" || echo "User not found: ${db_user}"
  output=$(is_db_present "$db_name")
  [ "$output" -eq 1 ] && echo "Database exists: ${db_name}" || echo "Database not found: ${db_name}"

  exit 0
fi

###
# UNINSTALL COMMAND
###
if [ "$cmd" = "uninstall" ]
then
  installed=$(is_installed)
  [ "$installed" -eq 0 ] && echo "PostgreSQL package is not installed" && exit 0

  if [ "$os_current" = "$os_darwin" ]
  then
    brew unpin "${pkg_name_darwin}"
    brew services stop "${pkg_name_darwin}"
    brew remove "${pkg_name_darwin}"
    # Delete data dir
    [ "$purge" -eq 1 ] && rm -rf "${data_dir_darwin}"

  elif [ "$os_current" = "$os_fedora" ]
  then
    sudo dnf remove -y "${pkg_name_fedora}"
    # Delete data dir
    [ "$purge" -eq 1 ] && sudo rm -rf "${data_dir_fedora}"

  elif [ "$os_current" = "$os_linux" ]
  then
    uninstall_cmd=""
    # Delete data dir
    [ "$purge" -eq 1 ] && uninstall_cmd="purge" || uninstall_cmd="remove"
    sudo DEBIAN_FRONTEND=noninteractive apt-get $uninstall_cmd -y "$pkg_name_linux" "$pkg_name_suppl_linux"
    sudo apt-key del "${key_name_linux}"
    sudo rm -f "${src_file_linux}"

  fi
  exit 0
fi

###
# INSTALL COMMAND
###
if [ "$cmd" = "install" ]
then
  installed=$(is_installed)
  [ "$installed" -eq 1 ] && echo "PostgreSQL package is already installed" && exit 0

  if [ "$os_current" = "$os_darwin" ]
  then
    file_path="${pkg_file_darwin}"
    url="${pkg_url_darwin}"
    dst_dir_path=$(find `brew --repository` -name "Formula")
    [ ! -d "${dst_dir_path}" ] && echo "Failed to find brew Formula directory. Required brew version: $brew_version" && exit 1
    dst_path="${dst_dir_path}/${file_path}"
    curl -L -o "${file_path}" "${url}" && \
    mv "${file_path}" "${dst_path}" && \
    brew install "${pkg_name_darwin}" && \
    brew pin "${pkg_name_darwin}" && \
    brew services start "${pkg_name_darwin}" && \
    install_success=1

    [ "$install_success" -eq 1 ] && createuser -s "$psql_usr"

  elif [ "$os_current" = "$os_fedora" ]
  then
    sudo dnf install -y "${pkg_name_fedora}" && install_success=1 || install_success=0
    if [ "$install_success" -eq 1 ]
    then
      if sudo test ! -f "${access_file_fedora}"
      then
        sudo postgresql-setup --initdb --unit postgresql && install_success=1 || install_success=0
      fi
      if [ "$install_success" -eq 1 ]
      then
        allow_access_line="host all all 127.0.0.1/32 md5"
        access_file="${access_file_fedora}"
        sudo grep -q "${allow_access_line}" "${access_file}" || sudo sed -i -e "1i${allow_access_line}" "${access_file}"

        sudo systemctl start postgresql && \
        install_success=1
      fi
    fi

  elif [ "$os_current" = "$os_linux" ]
  then
    # /etc/postgresql/13/main/pg_hba.conf
    # Source: https://www.postgresql.org/download/linux/ubuntu/
    sudo apt-get update && \
    sudo apt-get install -y lsb-release gnupg && \
    release_name=$(lsb_release -cs) && \
    sudo sh -c "echo 'deb http://apt.postgresql.org/pub/repos/apt ${release_name}-pgdg main' > ${src_file_linux}" && \
    wget --quiet -O - "$key_url_linux" | sudo apt-key add - && \
    sudo apt-get update && \
    sudo DEBIAN_FRONTEND=noninteractive apt-get -y install "$pkg_name_linux" && \
    install_success=1

  fi

  if [ "$install_success" -eq 0 ]
  then
    exit 1
  fi
fi

###
# CLEAR & DROP COMMANDS
###
clear_success=0
if [[ "$cmd" = "clear" ]] || [[ "$cmd" = "drop" ]]; then
  db_name=$(must_get_db_name "$cfg_path")

  error=0

  exec_psql_command "--command \"drop database if exists $db_name;\"" &>/dev/null && \
    echo "Database $db_name dropped" || \
    error=1

  [ "$error" -eq 1 ] && echo "Failed to drop database $db_name" && exit 1

#  exec_psql_command "--command \"drop user if exists $db_user;\"" &>/dev/null && \
#    echo "User $db_user dropped" || \
#    error=1
#  [ "$error" -eq 1 ] && echo "Failed to drop user $db_user" && exit 1

  if [[ "$cmd" = "clear" ]]; then
    clear_success=1
  fi
fi

###
# SETUP COMMAND
###
if [[ "$install_success" -eq 1 ]] || [[ "$clear_success" -eq 1 ]] || [[ "$cmd" = "setup" ]]
then
  installed=$(is_installed)
  [ "$installed" -eq 0 ] && echo "PostgreSQL package is not installed" && exit 0

  db_name=$(must_get_db_name "$cfg_path")
  db_user=$(must_get_db_user "$cfg_path")
  db_pass=$(must_get_db_pass "$cfg_path")

  error=0
  
  # if inside a docker container
  if [ -f /.dockerenv ]
  then
    printf '#!/bin/sh\nexit 0' > /usr/sbin/policy-rc.d
    echo "Updated policy-rc.d for container"
  fi

  # Create user if not present
  output=$(is_user_present "$db_user")
  if [ "$output" -eq 0 ]
  then
    exec_psql_command "--command \"create user $db_user with password '$db_pass';\"" &>/dev/null && \
      echo "User $db_user created" || \
      error=1
  else
    exec_psql_command "--command \"alter user $db_user with password '$db_pass';\"" &>/dev/null && \
      echo "User $db_user updated" || \
      error=1
  fi
  [ "$error" -eq 1 ] && echo "Failed to setup user $db_user" && exit 1

  # Update user
  exec_psql_command "--command \"alter user $db_user with superuser;\"" &>/dev/null && \
    echo "User $db_user update successful" || \
    echo "User $db_user update failed"

  # Create db if not present
  output=$(is_db_present "$db_name")
  if [ "$output" -eq 0 ]
  then
    exec_psql_command "--command \"create database $db_name;\"" &>/dev/null && \
      echo "Database $db_name created" || \
      error=1
  else
    echo "Database $db_name exists"
  fi
  [ "$error" -eq 1 ] && echo "Failed to setup database $db_name" && exit 1

  # Update db
  exec_psql_command "--command \"grant all privileges on database $db_name to $db_user;\"" &>/dev/null && \
    echo "Database $db_name update successful" || \
    echo "Database $db_name update failed"

  exit 0
fi
