#!/usr/bin/env bash
set -euo pipefail

kb() {
  kubectl -n observability exec -i deployment/kibana -- \
    curl -s -o /dev/stdout -w '\n%{http_code}' -H 'kbn-xsrf: true' "$@"
}

echo "looking up the data view"
dv=$(kb localhost:5601/api/data_views | head -n -1 | grep -o '"id":"[^"]*","namespaces":\[[^]]*\],"title":"eventus-logs-\*"' | cut -d'"' -f4)
[ -n "${dv}" ] || dv=$(kb localhost:5601/api/data_views | head -n -1 | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(next(v['id'] for v in d['data_view'] if v['title']=='eventus-logs-*'))")
echo "data view id: ${dv}"

sed "s/DATAVIEW_ID/${dv}/g" objects.json > /tmp/objects-resolved.json

echo "creating saved objects"
body=$(python3 -c "
import json,io
print(json.dumps(json.load(io.open('/tmp/objects-resolved.json',encoding='utf-8'))))")

out=$(kubectl -n observability exec -i deployment/kibana -- \
  curl -s -o /dev/stdout -w '\n%{http_code}' -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -X POST 'localhost:5601/api/saved_objects/_bulk_create?overwrite=true' -d @- <<< "${body}")

code=$(echo "${out}" | tail -1)
echo "http ${code}"
echo "${out}" | head -n -1 | python3 -c "
import json,sys
d=json.load(sys.stdin)
for o in d.get('saved_objects',[]):
    e=o.get('error')
    print(('FAIL ' if e else 'ok   ')+o['id'], e.get('message','') if e else '')
" || echo "${out}" | head -n -1

echo
echo "open: http://kibana.local/app/dashboards#/view/eventus-demo"
