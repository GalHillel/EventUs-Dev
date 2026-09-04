#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
NS=eventus
DEPLOY=eventus-api
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null | awk '{print $1}' || true)
BASE=${BASE:-http://${NODE_IP:-localhost}}

usage() {
  cat <<'USAGE'
usage: ./eventus.sh <command>

  up          provision the host, push the seed images, apply terraform
  jenkins     build and run the Jenkins container
  kubeconfig  write the scoped kubeconfig Jenkins uses
  kibana      create the eventus-logs data view
  status      one screen with the state of everything
  traffic     send steady traffic until Ctrl+C
  break       push the deliberate regression
  reset       revert it and clear the healer cooldown
USAGE
}

kb() {
  kubectl -n observability exec -i deployment/kibana -- curl -s -o /dev/stdout -w '\n%{http_code}' "$@"
}

es_count() {
  kubectl -n observability exec -i -c elasticsearch statefulset/elasticsearch -- \
    curl -s -XPOST "localhost:9200/eventus-logs-*/_count" -H 'Content-Type: application/json' -d @- <<< "$1" |
    grep -o '"count":[0-9]*' | cut -d: -f2
}

cmd_up() {
  : "${DOCKER_USER:?set DOCKER_USER to your Docker Hub account}"
  command -v ansible-playbook >/dev/null || { echo "install ansible first: sudo apt-get install -y ansible && ansible-galaxy collection install ansible.posix" >&2; exit 1; }

  local become=(); sudo -n true 2>/dev/null || become=(--ask-become-pass)
  ansible-playbook "${become[@]}" -i "${ROOT}/infra/ansible/inventory.ini" "${ROOT}/infra/ansible/site.yml"

  local sha
  sha=$(git -C "${ROOT}" rev-parse --short HEAD)
  docker build --build-arg "APP_VERSION=${sha}" -t "${DOCKER_USER}/eventus-api:seed" "${ROOT}/backend/event-us"
  docker push "${DOCKER_USER}/eventus-api:seed"
  docker build -t "${DOCKER_USER}/eventus-healer:1.0.0" "${ROOT}/infra/healer"
  docker push "${DOCKER_USER}/eventus-healer:1.0.0"

  cd "${ROOT}/infra/terraform"
  if [ ! -f terraform.tfvars ]; then
    sed "s|galhillel|${DOCKER_USER}|g; s|CHANGE_ME_BEFORE_APPLY|$(openssl rand -hex 16)|" \
      terraform.tfvars.example > terraform.tfvars
  fi
  terraform init
  terraform apply -auto-approve

  cmd_kubeconfig
  cmd_kibana
  echo "api ${BASE}/   kibana http://kibana.local/   next: ./eventus.sh jenkins"
}

cmd_jenkins() {
  local gid
  gid=$(getent group docker 2>/dev/null | cut -d: -f3 || true)
  [ -n "${gid}" ] || { echo "no docker group, run ./eventus.sh up first" >&2; exit 1; }

  docker build -t eventus-jenkins:1.0 "${ROOT}/infra/jenkins"
  docker volume create jenkins_home >/dev/null
  docker rm -f jenkins >/dev/null 2>&1 || true
  docker run -d --name jenkins --restart unless-stopped --network host --group-add "${gid}" \
    -v jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock \
    -e JAVA_OPTS=-Xmx1g eventus-jenkins:1.0

  echo "jenkins on http://localhost:8080, waiting for the admin password"
  for _ in $(seq 1 30); do
    if docker exec jenkins test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
      docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
      return 0
    fi
    sleep 5
  done
  echo "timed out, check: docker logs jenkins" >&2
}

cmd_kubeconfig() {
  local out token ca
  out="${ROOT}/infra/jenkins/kubeconfig-jenkins.yaml"
  token=$(kubectl -n "${NS}" get secret jenkins-deployer-token -o jsonpath='{.data.token}' | base64 -d)
  ca=$(kubectl -n "${NS}" get secret jenkins-deployer-token -o jsonpath='{.data.ca\.crt}')
  [ -n "${token}" ] || { echo "token is empty, wait a few seconds and retry" >&2; exit 1; }

  cat > "${out}" <<EOF
apiVersion: v1
kind: Config
clusters: [{name: eventus, cluster: {server: "https://127.0.0.1:6443", certificate-authority-data: ${ca}}}]
contexts: [{name: jenkins, context: {cluster: eventus, namespace: ${NS}, user: jenkins}}]
current-context: jenkins
users: [{name: jenkins, user: {token: ${token}}}]
EOF
  chmod 600 "${out}"
  echo "wrote ${out}"
  for verb in "patch deployments" "delete deployments" "get secrets"; do
    printf '  %-20s %s\n' "${verb}" "$(KUBECONFIG=${out} kubectl auth can-i ${verb} -n ${NS})"
  done
}

cmd_kibana() {
  local state body code
  echo "waiting for kibana"
  for _ in $(seq 1 60); do
    state=$(kb localhost:5601/api/status | tail -1)
    [ "${state}" = "200" ] && break
    sleep 5
  done
  [ "${state:-}" = "200" ] || { echo "kibana never came up" >&2; exit 1; }

  body=$(kb -X POST localhost:5601/api/data_views/data_view -H 'Content-Type: application/json' -H 'kbn-xsrf: true' \
    -d '{"data_view":{"title":"eventus-logs-*","name":"eventus-logs","timeFieldName":"@timestamp"}}')
  code=$(echo "${body}" | tail -1)
  case "${code}" in
    200) echo "created the eventus-logs data view" ;;
    400) echo "data view already exists" ;;
    *)   echo "kibana returned ${code}" >&2; exit 1 ;;
  esac
}

cmd_status() {
  kubectl get nodes --no-headers
  kubectl -n "${NS}" get deploy,pods --no-headers
  kubectl -n observability get pods --no-headers
  kubectl -n platform get cronjob,jobs --no-headers
  echo
  echo "image   $(kubectl -n ${NS} get deploy ${DEPLOY} -o jsonpath='{.spec.template.spec.containers[0].image}')"
  echo "ready   $(curl -s -o /dev/null -w '%{http_code}' ${BASE}/health/ready)"
  echo "chaos   $(curl -s ${BASE}/chaos/status)"
  echo
  kubectl -n "${NS}" rollout history "deployment/${DEPLOY}"
  echo
  local total errs
  total=$(es_count '{"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-5m"}}},{"exists":{"field":"statusCode"}}]}}}')
  errs=$(es_count '{"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-5m"}}},{"range":{"statusCode":{"gte":500}}}]}}}')
  echo "last 5m: requests=${total:-0} errors=${errs:-0}"
}

cmd_traffic() {
  local paths=("/events/search?name=Wedding" "/events/search?location=TelAviv" "/users/search?name=gal" /messages/search)
  local ok=0 err=0
  trap 'printf "\n\nok=%s err=%s\n" "${ok}" "${err}"; exit 0' INT
  echo "sending traffic to ${BASE}, Ctrl+C to stop"
  while true; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "${BASE}${paths[$((RANDOM % ${#paths[@]}))]}")
    if [ "${code}" -ge 500 ] 2>/dev/null; then
      err=$((err + 1)); printf '\033[31m%s\033[0m ' "${code}"
    else
      ok=$((ok + 1)); printf '\033[32m%s\033[0m ' "${code}"
    fi
    sleep 0.25
  done
}

cmd_break() {
  local file="${ROOT}/backend/event-us/src/common/platform.ts"
  sed -i "s/^const DEFAULT_ERROR_RATE = .*/const DEFAULT_ERROR_RATE = ${RATE:-0.45};/" "${file}"
  git -C "${ROOT}" --no-pager diff --unified=1 -- "${file}"
  git -C "${ROOT}" commit -am "feat: new caching layer for event search"
  git -C "${ROOT}" push origin "$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
  echo "pushed, jenkins picks it up within two minutes"
}

cmd_reset() {
  git -C "${ROOT}" revert --no-edit HEAD
  git -C "${ROOT}" push origin "$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
  kubectl -n "${NS}" annotate "deployment/${DEPLOY}" eventus.io/last-rollback- 2>/dev/null || true
  kubectl -n platform delete jobs --all 2>/dev/null || true
  echo "reverted and cleared the cooldown"
}

case "${1:-}" in
  up)         cmd_up ;;
  jenkins)    cmd_jenkins ;;
  kubeconfig) cmd_kubeconfig ;;
  kibana)     cmd_kibana ;;
  status)     cmd_status ;;
  traffic)    cmd_traffic ;;
  break)      cmd_break ;;
  reset)      cmd_reset ;;
  *)          usage; exit 1 ;;
esac
