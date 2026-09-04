#!/usr/bin/env bash
set -euo pipefail

NS="${1}"
DEPLOY="${2}"
REASON="${3}"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo "before: $(kubectl -n "${NS}" get deployment "${DEPLOY}" -o jsonpath='{.spec.template.spec.containers[0].image}')"
kubectl -n "${NS}" rollout undo "deployment/${DEPLOY}"
kubectl -n "${NS}" rollout status "deployment/${DEPLOY}" --timeout=180s
kubectl -n "${NS}" annotate "deployment/${DEPLOY}" \
  "eventus.io/last-rollback=${NOW}" "kubernetes.io/change-cause=self-heal: ${REASON}" --overwrite >/dev/null
echo "after: $(kubectl -n "${NS}" get deployment "${DEPLOY}" -o jsonpath='{.spec.template.spec.containers[0].image}')"

kubectl -n "${NS}" create -f - <<EOF >/dev/null
apiVersion: v1
kind: Event
metadata: {generateName: eventus-healer-, namespace: ${NS}}
involvedObject: {apiVersion: apps/v1, kind: Deployment, name: ${DEPLOY}, namespace: ${NS}}
reason: SelfHealRollback
message: "${REASON}"
type: Warning
firstTimestamp: "${NOW}"
lastTimestamp: "${NOW}"
count: 1
EOF
echo "event recorded"
