# EventUs on-premise platform

Kubernetes restarts a pod that dies. It does not notice a pod that is alive, passes
every probe, and answers half its requests with HTTP 500. This closes that gap: the
API emits structured JSON logs, Filebeat ships them to Elasticsearch, and a CronJob
queries Elasticsearch every minute and rolls the deployment back on a real error spike.

Ansible provisions the host. K3s runs the containers. Terraform defines everything
inside the cluster. Jenkins builds, pushes and deploys. Traefik, bundled with K3s, is
the ingress; no Nginx. Filebeat ships JSON straight to Elasticsearch; no Logstash.

## Layout

    backend/event-us/       NestJS API and its Dockerfile
    frontend/               Android client
    infra/ansible/          host provisioning, one playbook
    infra/terraform/        every cluster resource
    infra/healer/           the self-healing job
    infra/jenkins/          the CI image
    eventus.sh              every operation
    Jenkinsfile             build, push, deploy

## Run it

    export DOCKER_USER=<your docker hub account>
    ./eventus.sh up
    ./eventus.sh jenkins

| command | what it does |
|---|---|
| `up` | ansible, seed images, terraform, kubeconfig, kibana data view |
| `jenkins` | build and start the CI container, print the admin password |
| `status` | one screen with the state of everything |
| `traffic` | steady traffic until Ctrl+C |
| `break` | push the deliberate regression |
| `reset` | revert it and clear the healer cooldown |

| url | what |
|---|---|
| http://localhost/ | API through Traefik |
| http://localhost/docs | Swagger |
| http://kibana.local/ | Kibana |
| http://localhost:8080 | Jenkins |

## Self-healing

A rollback happens only when both hold over the last five minutes: at least 10 server
errors, and server errors are at least 25% of all requests. A ten minute cooldown
stops it from firing again while the previous rollback settles. If Elasticsearch is
unreachable the job logs it and exits without touching the cluster.

Thresholds live in the healer ConfigMap in `infra/terraform/main.tf`.

## Notes

- Image tags are `<git-sha>-<build-number>`. `rollout undo` cannot swap images without
  unique tags, so `variables.tf` rejects `:latest`.
- Terraform owns the deployment, Jenkins owns the tag. `ignore_changes` on the image
  field keeps them from fighting.
- The health endpoints are exempt from the chaos switch. A pod failing its probes gets
  killed by Kubernetes; the failure being demonstrated is a pod that stays healthy and
  serves errors.
