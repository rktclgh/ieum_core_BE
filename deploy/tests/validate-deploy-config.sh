#!/usr/bin/env bash

set -uo pipefail

readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPOSITORY_ROOT"

failures=0

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

require_file() {
  local file="$1"

  if [[ ! -f "$file" ]]; then
    fail "missing required file: $file"
    return 1
  fi
}

require_contains() {
  local file="$1"
  local expected="$2"
  local description="$3"

  [[ -f "$file" ]] || return 0
  if ! grep -Fq -- "$expected" "$file"; then
    fail "$file: $description (expected literal: $expected)"
  fi
}

forbid() {
  local file="$1"
  local forbidden="$2"
  local description="$3"

  [[ -f "$file" ]] || return 0
  if grep -Fq -- "$forbidden" "$file"; then
    fail "$file: $description (forbidden literal: $forbidden)"
  fi
}

logical_lines() {
  local file="$1"
  local line
  local logical=''

  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ -n "$logical" ]]; then
      line="${line#"${line%%[![:space:]]*}"}"
      logical="$logical $line"
    else
      logical="$line"
    fi

    if [[ "$logical" == *\\ ]]; then
      logical="${logical%\\}"
      continue
    fi

    printf '%s\n' "$logical"
    logical=''
  done < "$file"

  [[ -z "$logical" ]] || printf '%s\n' "$logical"
}

trim() {
  local value="$1"

  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

require_exact_concurrency_group() {
  local file="$1"
  local expected="$2"
  local line
  local actual=''
  local remaining=0
  local cancel_seen=0

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'concurrency:'* ]]; then
      remaining=10
    elif ((remaining > 0)); then
      remaining=$((remaining - 1))
    else
      continue
    fi

    if [[ "$line" == *'group:'* ]]; then
      actual="$(trim "${line#*group:}")"
      actual="${actual%%#*}"
      actual="$(trim "$actual")"
      actual="${actual#\"}"
      actual="${actual%\"}"
      actual="${actual#\'}"
      actual="${actual%\'}"
    fi
    [[ "$line" == *'cancel-in-progress: false'* ]] && cancel_seen=1
    if [[ -n "$actual" ]] && ((cancel_seen == 1)); then
      break
    fi
  done < "$file"

  if [[ "$actual" != "$expected" ]]; then
    fail "$file: concurrency group must be exactly $expected (found: ${actual:-<empty>})"
  fi
  ((cancel_seen == 1)) || fail "$file: $expected concurrency must set cancel-in-progress: false"
}

require_frontend_export_gate() {
  local file="$1"
  local line

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'frontend/out/index.html'* && "$line" == *'-f'* ]]; then
      case "$line" in
        *'test '*|*'[[ '*|*'[ '*) return 0 ;;
      esac
    fi
  done < <(logical_lines "$file")

  fail "$file: must gate the release with a file test for frontend/out/index.html"
}

line_has_frontend_sha_output() {
  local line="$1"
  local selector

  [[ "$line" == *'steps.'* && "$line" == *'.outputs.'* && "$line" == *'sha'* ]] || return 1
  if [[ "$line" == *'.outputs.fe_sha'* || "$line" == *'.outputs.frontend_sha'* ]]; then
    return 0
  fi
  selector="${line#*steps.}"
  selector="${selector%%.outputs.*}"
  case "$selector" in
    *frontend*|fe-*|fe_*|*'-fe-'*|*'_fe_'*|*-fe|*_fe) return 0 ;;
  esac
  return 1
}

require_immutable_tag_contract() {
  local file="$1"
  local image="$2"
  local require_frontend_sha="$3"
  local line

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    case "$line" in
      *'tags:'*|*'IMAGE_TAG:'*|*'IMAGE_TAG='*)
        [[ "$line" == *"$image"* ]] || continue
        [[ "$line" == *'${{ github.sha }}'* ]] || continue
        if [[ "$require_frontend_sha" == 'true' ]]; then
          [[ "$line" == *'-fe-'* ]] || continue
          line_has_frontend_sha_output "$line" || continue
        fi
        return 0
        ;;
    esac
  done < <(logical_lines "$file")

  if [[ "$require_frontend_sha" == 'true' ]]; then
    fail "$file: tags:/IMAGE_TAG: must directly combine $image, \${{ github.sha }}, and a frontend SHA step output"
  else
    fail "$file: tags:/IMAGE_TAG: must directly combine $image and \${{ github.sha }}"
  fi
}

require_dispatch_type() {
  local file="$1"
  local line
  local remaining=0
  local types_seen=0

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'repository_dispatch:'* ]]; then
      if [[ "$line" == *'types:'* && "$line" == *'frontend-updated'* ]]; then
        return 0
      fi
      remaining=10
      types_seen=0
      continue
    fi
    ((remaining > 0)) || continue
    remaining=$((remaining - 1))
    if [[ "$line" == *'types:'* ]]; then
      types_seen=1
      [[ "$line" == *'frontend-updated'* ]] && return 0
      continue
    fi
    if ((types_seen == 1)) && [[ "$line" == *'frontend-updated'* ]]; then
      return 0
    fi
  done < "$file"

  fail "$file: repository_dispatch types must include frontend-updated"
}

require_frontend_checkout_ref() {
  local file="$1"
  local line
  local remaining=0

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'rktclgh/Vivisa_Plus_FE'* ]]; then
      remaining=10
      continue
    fi
    ((remaining > 0)) || continue
    remaining=$((remaining - 1))
    [[ "$line" == *'ref:'* ]] || continue
    if [[ "$line" == *'fe_sha'* ]]; then
      return 0
    fi
    if line_has_frontend_sha_output "$line"; then
      return 0
    fi
  done < "$file"

  fail "$file: frontend checkout ref must use client_payload.fe_sha or the frontend SHA selector output"
}

line_has_pinned_known_hosts_path() {
  case "$1" in
    *'$HOME/.ssh/known_hosts'*|*'${HOME}/.ssh/known_hosts'*|*'~/.ssh/known_hosts'*) return 0 ;;
  esac
  return 1
}

require_secure_transport_contract() {
  local file="$1"
  local line
  local known_hosts_written=0
  local ssh_paths=0
  local secure_ssh_paths=0
  local scp_paths=0
  local secure_scp_paths=0

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'SSH_KNOWN_HOSTS'* ]] && line_has_pinned_known_hosts_path "$line"; then
      case "$line" in
        *'>'*|*'tee '*|*'install '*) known_hosts_written=1 ;;
      esac
    fi
    if line_invokes_command "$line" ssh; then
      ssh_paths=$((ssh_paths + 1))
      if [[ "$line" == *'StrictHostKeyChecking=yes'* && "$line" == *'UserKnownHostsFile='* ]] && line_has_pinned_known_hosts_path "$line"; then
        secure_ssh_paths=$((secure_ssh_paths + 1))
      fi
    fi
    if line_invokes_command "$line" scp; then
      scp_paths=$((scp_paths + 1))
      if [[ "$line" == *'StrictHostKeyChecking=yes'* && "$line" == *'UserKnownHostsFile='* ]] && line_has_pinned_known_hosts_path "$line"; then
        secure_scp_paths=$((secure_scp_paths + 1))
      fi
    fi
  done < <(logical_lines "$file")

  ((known_hosts_written == 1)) || fail "$file: SSH_KNOWN_HOSTS must be written directly to the pinned known_hosts file"
  ((ssh_paths > 0 && ssh_paths == secure_ssh_paths)) || fail "$file: every SSH path must set StrictHostKeyChecking=yes and a pinned UserKnownHostsFile"
  ((scp_paths > 0 && scp_paths == secure_scp_paths)) || fail "$file: every SCP path must set StrictHostKeyChecking=yes and a pinned UserKnownHostsFile"
}

require_runtime_file_mode() {
  local file="$1"
  local line

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    [[ "$line" == *'.env.runtime'* ]] || continue
    if [[ "$line" == *'chmod '* && ("$line" == *' 600'* || "$line" == *' 0600'*) ]]; then
      return 0
    fi
    if [[ "$line" == *'install '* && "$line" == *'-m'* && ("$line" == *'600'* || "$line" == *'0600'*) ]]; then
      return 0
    fi
  done < <(logical_lines "$file")

  fail "$file: .env.runtime must be directly protected by chmod 600/0600 or install -m 0600"
}

line_has_prior_image_var() {
  case "$1" in
    *previous_image*|*PREVIOUS_IMAGE*|*prior_image*|*PRIOR_IMAGE*|*current_image*|*CURRENT_IMAGE*) return 0 ;;
  esac
  return 1
}

line_invokes_command() {
  local line

  line=" $(trim "$1") "
  [[ "$line" == *" $2 "* ]]
}

require_rollback_contract() {
  local file="$1"
  local line
  local rollback_body=''
  local in_rollback=0
  local in_health_branch=0
  local health_condition_is_failure=0
  local health_else_seen=0
  local prior_capture=0
  local health_failure_calls_rollback=0
  local prior_branch=0
  local prior_restore_binding=0
  local prior_restore_up=0
  local no_prior_else=0
  local no_prior_stop=0
  local in_prior_branch=0
  local in_no_prior_branch=0

  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == *'docker inspect'* && "$line" == *'='* ]] && line_has_prior_image_var "$line"; then
      prior_capture=1
    fi

    if [[ "$line" == *'if '* && "$line" == *'health'* ]]; then
      in_health_branch=1
      health_else_seen=0
      health_condition_is_failure=0
      if [[ "$line" == *'!'* || "$line" == *'!='* ]]; then
        health_condition_is_failure=1
      fi
    fi
    if ((in_health_branch == 1)) && [[ "$line" == *'else'* ]]; then
      health_else_seen=1
    fi
    if ((in_health_branch == 1)) && ((health_condition_is_failure == 1 || health_else_seen == 1)) && [[ "$line" == *'rollback'* && "$line" != *'()'* ]]; then
      health_failure_calls_rollback=1
    fi
    if ((in_health_branch == 1)) && [[ "$line" == *'fi'* ]]; then
      in_health_branch=0
      health_condition_is_failure=0
      health_else_seen=0
    fi

    if ((in_rollback == 0)) && [[ "$line" == *'rollback'* && "$line" == *'()'* && "$line" == *'{'* ]]; then
      in_rollback=1
      continue
    fi
    if ((in_rollback == 1)); then
      if [[ "$(trim "$line")" == '}' ]]; then
        in_rollback=0
        continue
      fi
      rollback_body="$rollback_body"$'\n'"$line"
    fi
  done < <(logical_lines "$file")

  if [[ -n "$rollback_body" ]]; then
    while IFS= read -r line; do
      if [[ "$line" == *'if '* ]] && line_has_prior_image_var "$line" && ([[ "$line" == *'-n'* ]] || [[ "$line" == *'!='* ]]); then
        prior_branch=1
        in_prior_branch=1
        in_no_prior_branch=0
      fi
      if ((in_prior_branch == 1)) && line_has_prior_image_var "$line" && ([[ "$line" == *'export '* ]] || [[ "$line" == *'IMAGE='* ]] || [[ "$line" == *' image='* ]] || [[ "$line" == *'image_variable'* ]]); then
        prior_restore_binding=1
      fi
      if ((in_prior_branch == 1)) && [[ "$line" == *'docker compose up -d'* ]]; then
        prior_restore_up=1
      fi
      if ((in_prior_branch == 1)) && [[ "$line" == *'else'* ]]; then
        no_prior_else=1
        in_prior_branch=0
        in_no_prior_branch=1
      fi
      if ((in_no_prior_branch == 1)) && ([[ "$line" == *'docker compose down'* ]] || [[ "$line" == *'docker compose rm'* ]] || [[ "$line" == *'docker rm '* ]]); then
        no_prior_stop=1
      fi
      if ((in_no_prior_branch == 1)) && [[ "$line" == *'fi'* ]]; then
        in_no_prior_branch=0
      fi
    done <<< "$rollback_body"
  fi

  ((prior_capture == 1)) || fail "$file: prior image variable must be assigned directly from docker inspect"
  [[ -n "$rollback_body" ]] || fail "$file: must define an explicit rollback function"
  ((health_failure_calls_rollback == 1)) || fail "$file: the failed health-check branch must call rollback"
  ((prior_branch == 1)) || fail "$file: rollback must branch on a non-empty prior image"
  ((prior_restore_binding == 1)) || fail "$file: rollback must bind the prior image back to the Compose image variable"
  ((prior_restore_up == 1)) || fail "$file: rollback must bring the prior image up with Docker Compose"
  ((no_prior_else == 1 && no_prior_stop == 1)) || fail "$file: rollback must stop/remove the failed deployment when no prior image exists"
}

require_tree_excludes() {
  local forbidden="$1"
  local description="$2"
  local directory
  local file

  for directory in .github/workflows deploy; do
    [[ -d "$directory" ]] || continue
    while IFS= read -r -d '' file; do
      forbid "$file" "$forbidden" "$description"
    done < <(find "$directory" -type f -print0)
  done
}

readonly APP_MAIN_WORKFLOW='.github/workflows/deploy-app-main.yml'
readonly APP_AI_WORKFLOW='.github/workflows/deploy-app-ai.yml'
readonly APP_MAIN_DOCKERFILE='deploy/app-main/Dockerfile'
readonly APP_AI_DOCKERFILE='deploy/app-ai/Dockerfile'
readonly APP_MAIN_COMPOSE='deploy/app-main/compose.yml'
readonly APP_AI_COMPOSE='deploy/app-ai/compose.yml'
readonly APP_MAIN_ENV='deploy/env/app-main.env.example'
readonly APP_AI_ENV='deploy/env/app-ai.env.example'
readonly BOOTSTRAP_SCRIPT='deploy/scripts/bootstrap-docker.sh'
readonly DEPLOY_SCRIPT='deploy/scripts/deploy-compose.sh'

readonly -a required_files=(
  "$APP_MAIN_DOCKERFILE"
  "$APP_AI_DOCKERFILE"
  "$APP_MAIN_COMPOSE"
  "$APP_AI_COMPOSE"
  "$APP_MAIN_ENV"
  "$APP_AI_ENV"
  "$BOOTSTRAP_SCRIPT"
  "$DEPLOY_SCRIPT"
  'deploy/GITHUB-CONFIG.md'
  "$APP_MAIN_WORKFLOW"
  "$APP_AI_WORKFLOW"
  'app-main/build.gradle.kts'
)

for file in "${required_files[@]}"; do
  require_file "$file" || true
done

require_contains "$BOOTSTRAP_SCRIPT" 'download.docker.com/linux/ubuntu' "must use Docker's official Ubuntu repository"
require_contains "$BOOTSTRAP_SCRIPT" 'docker-ce' 'must install Docker Engine'
require_contains "$BOOTSTRAP_SCRIPT" 'docker-ce-cli' 'must install the Docker CLI'
require_contains "$BOOTSTRAP_SCRIPT" 'containerd.io' 'must install containerd'
require_contains "$BOOTSTRAP_SCRIPT" 'docker-buildx-plugin' 'must install Buildx'
require_contains "$BOOTSTRAP_SCRIPT" 'docker-compose-plugin' 'must install the Compose plugin'
require_contains "$BOOTSTRAP_SCRIPT" 'docker compose version' 'must verify Docker Compose'
require_contains "$BOOTSTRAP_SCRIPT" 'systemctl' 'must enable and start Docker'

require_contains "$APP_MAIN_DOCKERFILE" '21' 'must use Java 21'
require_contains "$APP_MAIN_DOCKERFILE" 'jre' 'must use a JRE base image'
require_contains "$APP_MAIN_DOCKERFILE" 'app-main/build/libs/' 'must copy only the app-main boot jar'
require_contains "$APP_MAIN_DOCKERFILE" 'USER 10001' 'must run as numeric user 10001'
require_contains "$APP_MAIN_DOCKERFILE" 'EXPOSE 8080' 'must expose app-main port 8080'
require_contains "$APP_MAIN_DOCKERFILE" 'java' 'must execute Java'
require_contains "$APP_MAIN_DOCKERFILE" '-jar' 'must execute the boot jar'
require_contains "$APP_MAIN_DOCKERFILE" '/app/app.jar' 'must install the boot jar at the fixed runtime path'

require_contains "$APP_AI_DOCKERFILE" '21' 'must use Java 21'
require_contains "$APP_AI_DOCKERFILE" 'jre' 'must use a JRE base image'
require_contains "$APP_AI_DOCKERFILE" 'app-ai/build/libs/' 'must copy only the app-ai boot jar'
require_contains "$APP_AI_DOCKERFILE" 'USER 10001' 'must run as numeric user 10001'
require_contains "$APP_AI_DOCKERFILE" 'EXPOSE 8081' 'must expose app-ai port 8081'
require_contains "$APP_AI_DOCKERFILE" 'java' 'must execute Java'
require_contains "$APP_AI_DOCKERFILE" '-jar' 'must execute the boot jar'
require_contains "$APP_AI_DOCKERFILE" '/app/app.jar' 'must install the boot jar at the fixed runtime path'

require_contains "$APP_MAIN_ENV" 'REDIS_HOST=redis' 'must address the private Compose Redis service'
require_contains "$APP_MAIN_ENV" 'APP_AI_QUESTION_ANSWER_DISPATCH_BASE_URL=http://172.31.33.42:8081' 'must use the private app-ai origin'
require_contains "$APP_MAIN_ENV" 'APP_AI_INTERNAL_CALLBACK_TOKEN=' 'must declare the shared callback token key'
require_contains "$APP_AI_ENV" 'SERVER_PORT=8081' 'must bind app-ai to its service port'
require_contains "$APP_AI_ENV" 'APP_AI_BEDROCK_REGION=ap-southeast-2' 'must retain the verified Bedrock region'
require_contains "$APP_AI_ENV" 'APP_AI_QUESTION_CALLBACK_BASE_ORIGIN=http://172.31.38.97:8080' 'must use the private app-main origin'
require_contains "$APP_AI_ENV" 'APP_AI_INTERNAL_CALLBACK_TOKEN=' 'must declare the shared callback token key'

require_contains "$APP_MAIN_WORKFLOW" 'push:' 'must deploy app-main changes from backend pushes'
require_contains "$APP_MAIN_WORKFLOW" 'branches:' 'must restrict push deployments to a release branch'
require_contains "$APP_MAIN_WORKFLOW" 'workflow_dispatch:' 'must support a manual deployment'
require_contains "$APP_MAIN_WORKFLOW" 'frontend_ref:' 'must expose the manual frontend selector'
require_contains "$APP_MAIN_WORKFLOW" 'github.event.client_payload.fe_sha' 'must select the exact dispatched frontend SHA'
require_contains "$APP_MAIN_WORKFLOW" 'rktclgh/Vivisa_Plus_FE' 'must check out the designated frontend repository'
require_contains "$APP_MAIN_WORKFLOW" 'app-main/**' 'must react to app-main changes'
require_contains "$APP_MAIN_WORKFLOW" 'common/**' 'must react to common changes'
require_dispatch_type "$APP_MAIN_WORKFLOW"
require_frontend_checkout_ref "$APP_MAIN_WORKFLOW"
require_frontend_export_gate "$APP_MAIN_WORKFLOW"
forbid "$APP_MAIN_WORKFLOW" '.next' 'must never package a Next runtime build as static output'
require_contains "$APP_MAIN_WORKFLOW" ':app-main:test :app-main:bootJar' 'must test and build only the app-main boot jar'
require_immutable_tag_contract "$APP_MAIN_WORKFLOW" 'ghcr.io/rktclgh/ieum-app-main:' true
require_contains "$APP_MAIN_WORKFLOW" 'app-main-production' 'must deploy through the app-main environment'
require_contains "$APP_MAIN_WORKFLOW" 'http://127.0.0.1:8080/actuator/health' 'must deploy against the app-main health endpoint'

require_contains "$APP_AI_WORKFLOW" 'push:' 'must deploy app-ai changes from backend pushes'
require_contains "$APP_AI_WORKFLOW" 'branches:' 'must restrict push deployments to a release branch'
require_contains "$APP_AI_WORKFLOW" 'workflow_dispatch:' 'must support a manual deployment'
require_contains "$APP_AI_WORKFLOW" 'app-ai/**' 'must react to app-ai changes'
require_contains "$APP_AI_WORKFLOW" 'common/**' 'must react to common changes'
require_contains "$APP_AI_WORKFLOW" ':app-ai:test :app-ai:bootJar' 'must test and build only the app-ai boot jar'
require_immutable_tag_contract "$APP_AI_WORKFLOW" 'ghcr.io/rktclgh/ieum-app-ai:' false
require_contains "$APP_AI_WORKFLOW" 'app-ai-production' 'must deploy through the app-ai environment'
require_contains "$APP_AI_WORKFLOW" 'http://127.0.0.1:8081/actuator/health' 'must deploy against the app-ai health endpoint'
forbid "$APP_AI_WORKFLOW" 'repository_dispatch:' 'must not accept frontend dispatches'
forbid "$APP_AI_WORKFLOW" 'rktclgh/Vivisa_Plus_FE' 'must not check out the frontend repository'
forbid "$APP_AI_WORKFLOW" 'frontend' 'must not contain frontend checkout or build markers'
forbid "$APP_AI_WORKFLOW" 'frontend-updated' 'must not accept the frontend event'
forbid "$APP_AI_WORKFLOW" 'fe_sha' 'must not consume a frontend SHA'
forbid "$APP_AI_WORKFLOW" 'fe_repo' 'must not consume a frontend repository selector'
forbid "$APP_AI_WORKFLOW" 'frontend_ref' 'must not consume a frontend ref'
forbid "$APP_AI_WORKFLOW" 'path: frontend' 'must not create a frontend checkout path'
forbid "$APP_AI_WORKFLOW" 'actions/setup-node' 'must not set up the frontend Node runtime'
forbid "$APP_AI_WORKFLOW" 'node-version:' 'must not configure a frontend Node version'
forbid "$APP_AI_WORKFLOW" 'pnpm' 'must not run frontend package tasks'
forbid "$APP_AI_WORKFLOW" 'npm ' 'must not run frontend package tasks'
forbid "$APP_AI_WORKFLOW" 'yarn ' 'must not run frontend package tasks'

for workflow in "$APP_MAIN_WORKFLOW" "$APP_AI_WORKFLOW"; do
  require_contains "$workflow" 'concurrency:' 'must declare deployment concurrency'
  require_contains "$workflow" 'cancel-in-progress: false' 'must not cancel an in-flight deployment'
  require_secure_transport_contract "$workflow"
  require_runtime_file_mode "$workflow"
done
require_exact_concurrency_group "$APP_MAIN_WORKFLOW" 'deploy-app-main-production'
require_exact_concurrency_group "$APP_AI_WORKFLOW" 'deploy-app-ai-production'

for compose_file in "$APP_MAIN_COMPOSE" "$APP_AI_COMPOSE"; do
  require_contains "$compose_file" 'healthcheck:' 'must define a container health check'
  require_contains "$compose_file" 'restart:' 'must define a restart policy'
  require_contains "$compose_file" '.env.runtime' 'must load service secrets from the runtime environment file'
done
require_contains "$APP_MAIN_COMPOSE" 'APP_MAIN_IMAGE' 'must consume the immutable app-main image'
require_contains "$APP_MAIN_COMPOSE" 'APP_MAIN_PORT' 'must expose a configurable app-main port'
require_contains "$APP_MAIN_COMPOSE" '/actuator/health' 'must check the app-main Actuator endpoint'
require_contains "$APP_MAIN_COMPOSE" 'redis:7' 'must run Redis 7 privately'
require_contains "$APP_MAIN_COMPOSE" 'appendonly' 'must enable Redis AOF persistence'
require_contains "$APP_MAIN_COMPOSE" 'volumes:' 'must retain Redis data'
require_contains "$APP_AI_COMPOSE" 'APP_AI_IMAGE' 'must consume the immutable app-ai image'
require_contains "$APP_AI_COMPOSE" 'APP_AI_BIND_ADDRESS' 'must bind app-ai to a configurable private address'
require_contains "$APP_AI_COMPOSE" '/actuator/health' 'must check the app-ai Actuator endpoint'

require_contains "$DEPLOY_SCRIPT" '.env.runtime' 'must consume the runtime environment file'
require_contains "$DEPLOY_SCRIPT" '--password-stdin' 'must pass the registry token without exposing it as an argument'
require_contains "$DEPLOY_SCRIPT" 'health_url' 'must consume the service-specific health URL'
require_contains "$DEPLOY_SCRIPT" '120' 'must bound health polling at 120 seconds'
require_contains "$DEPLOY_SCRIPT" 'docker compose up -d --remove-orphans' 'must replace the Compose project deterministically'
require_rollback_contract "$DEPLOY_SCRIPT"

require_contains 'app-main/build.gradle.kts' 'spring-boot-starter-actuator' 'must package the Actuator health endpoint'

readonly unsafe_ssh_option='StrictHostKeyChecking'"=no"
readonly mutable_image_tag=':'"latest"
readonly generic_private_key_header='-----BEGIN '"PRIVATE KEY-----"
readonly rsa_private_key_header='-----BEGIN RSA '"PRIVATE KEY-----"
readonly openssh_private_key_header='-----BEGIN OPENSSH '"PRIVATE KEY-----"
readonly ec_private_key_header='-----BEGIN EC '"PRIVATE KEY-----"
readonly dsa_private_key_header='-----BEGIN DSA '"PRIVATE KEY-----"
readonly encrypted_private_key_header='-----BEGIN ENCRYPTED '"PRIVATE KEY-----"

require_tree_excludes "$unsafe_ssh_option" 'must not disable SSH host-key verification'
require_tree_excludes "$mutable_image_tag" 'must not use a mutable image tag'
require_tree_excludes "$generic_private_key_header" 'must not contain a PEM private key'
require_tree_excludes "$rsa_private_key_header" 'must not contain an RSA PEM private key'
require_tree_excludes "$openssh_private_key_header" 'must not contain an OpenSSH private key'
require_tree_excludes "$ec_private_key_header" 'must not contain an EC PEM private key'
require_tree_excludes "$dsa_private_key_header" 'must not contain a DSA PEM private key'
require_tree_excludes "$encrypted_private_key_header" 'must not contain an encrypted PEM private key'

if ((failures > 0)); then
  printf 'Deployment contract validation failed with %d issue(s).\n' "$failures" >&2
  exit 1
fi

printf 'Deployment contract validation passed.\n'
