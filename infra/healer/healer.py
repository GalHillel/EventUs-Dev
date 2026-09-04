import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

ES_URL = os.environ.get('ES_URL', 'http://elasticsearch.observability.svc.cluster.local:9200')
ES_INDEX = os.environ.get('ES_INDEX', 'eventus-logs-*')
SERVICE = os.environ.get('SERVICE_NAME', 'eventus-api')
NAMESPACE = os.environ.get('TARGET_NAMESPACE', 'eventus')
DEPLOYMENT = os.environ.get('TARGET_DEPLOY', 'eventus-api')
THRESHOLD = int(os.environ.get('ERROR_THRESHOLD', '10'))
MIN_RATIO = float(os.environ.get('MIN_ERROR_RATIO', '0.25'))
WINDOW = int(os.environ.get('WINDOW_MINUTES', '5'))
COOLDOWN = int(os.environ.get('COOLDOWN_MINUTES', '10'))
DRY_RUN = os.environ.get('DRY_RUN', 'false').lower() == 'true'
ANNOTATION = 'eventus.io/last-rollback'


def log(level, msg, **extra):
    now = datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
    print(json.dumps({'time': now, 'level': level, 'service': 'eventus-healer', 'msg': msg, **extra},
                     separators=(',', ':')), flush=True)


def es(path, body):
    req = urllib.request.Request(f'{ES_URL}/{ES_INDEX}/{path}', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(req, timeout=15) as res:
        return json.loads(res.read())


def query(extra, aggs=None):
    body = {'query': {'bool': {'filter': [
        {'term': {'service.keyword': SERVICE}},
        {'range': {'@timestamp': {'gte': f'now-{WINDOW}m'}}},
    ] + extra}}}
    if aggs:
        body.update({'size': 0, 'aggs': aggs})
        return es('_search', body)
    return es('_count', body)['count']


def kubectl(args):
    r = subprocess.run(['kubectl', '-n', NAMESPACE] + args, capture_output=True, text=True, timeout=30)
    if r.returncode:
        raise RuntimeError(r.stderr.strip())
    return r.stdout.strip()


def minutes_since_rollback():
    try:
        raw = kubectl(['get', 'deployment', DEPLOYMENT, '-o', 'jsonpath={.metadata.annotations}'])
        stamp = json.loads(raw).get(ANNOTATION) if raw else None
    except (RuntimeError, json.JSONDecodeError) as err:
        log('warn', 'cannot read annotations', error=str(err))
        return None
    if not stamp:
        return None
    last = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
    return (datetime.now(timezone.utc) - last).total_seconds() / 60


def has_previous_revision():
    try:
        out = kubectl(['rollout', 'history', f'deployment/{DEPLOYMENT}'])
    except RuntimeError as err:
        log('warn', 'cannot read rollout history', error=str(err))
        return False
    return len([l for l in out.splitlines() if l.strip()[:1].isdigit()]) >= 2


def main():
    try:
        errors = query([{'range': {'statusCode': {'gte': 500}}}])
        total = query([{'exists': {'field': 'statusCode'}}])
    except (urllib.error.URLError, OSError) as err:
        log('error', 'elasticsearch unreachable', error=str(err), url=ES_URL)
        return 1

    ratio = errors / total if total else 0.0
    log('info', 'window evaluated', errors=errors, total=total, ratio=round(ratio, 4),
        threshold=THRESHOLD, minRatio=MIN_RATIO, windowMinutes=WINDOW)

    if errors < THRESHOLD or ratio < MIN_RATIO:
        log('info', 'healthy, no action')
        return 0

    elapsed = minutes_since_rollback()
    if elapsed is not None and elapsed < COOLDOWN:
        log('warn', 'breached but in cooldown', minutesSinceRollback=round(elapsed, 1), cooldownMinutes=COOLDOWN)
        return 0

    if not has_previous_revision():
        log('error', 'breached but there is no previous revision to roll back to')
        return 0

    try:
        buckets = query([{'range': {'statusCode': {'gte': 500}}}],
                        {'by_version': {'terms': {'field': 'version.keyword', 'size': 5}}})
        versions = {b['key']: b['doc_count'] for b in buckets['aggregations']['by_version']['buckets']}
    except Exception:
        versions = {}

    if DRY_RUN:
        log('warn', 'dry run, would have rolled back', errors=errors, versions=versions)
        return 0

    log('error', 'breached, rolling back', errors=errors, total=total, versions=versions)
    reason = f'{errors} server errors out of {total} requests in the last {WINDOW}m'
    r = subprocess.run(['bash', '/opt/healer/rollback.sh', NAMESPACE, DEPLOYMENT, reason],
                       capture_output=True, text=True, timeout=300)
    for line in r.stdout.splitlines():
        log('info', 'rollback', step=line)
    if r.returncode:
        log('error', 'rollback failed', stderr=r.stderr.strip())
        return 1
    log('error', 'rollback complete', versions=versions)
    return 0


if __name__ == '__main__':
    sys.exit(main())
