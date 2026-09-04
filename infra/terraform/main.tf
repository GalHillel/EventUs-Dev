locals {
  es_url    = "http://elasticsearch.observability.svc.cluster.local:9200"
  log_index = "eventus-logs"
  mongo_uri = "mongodb://eventus:${var.mongo_password}@mongodb.eventus.svc.cluster.local:27017/EventUs?authSource=admin"
}

resource "kubernetes_namespace_v1" "app" {
  metadata { name = "eventus" }
}

resource "kubernetes_namespace_v1" "obs" {
  metadata { name = "observability" }
}

resource "kubernetes_namespace_v1" "platform" {
  metadata { name = "platform" }
}

resource "kubernetes_secret_v1" "mongodb" {
  metadata {
    name      = "mongodb-credentials"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  data = {
    MONGO_INITDB_ROOT_USERNAME = "eventus"
    MONGO_INITDB_ROOT_PASSWORD = var.mongo_password
    MONGODB_URI                = local.mongo_uri
  }
}

resource "kubernetes_service_v1" "mongodb" {
  metadata {
    name      = "mongodb"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    cluster_ip = "None"
    selector   = { app = "mongodb" }
    port { port = 27017 }
  }
}

resource "kubernetes_stateful_set_v1" "mongodb" {
  metadata {
    name      = "mongodb"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    service_name = kubernetes_service_v1.mongodb.metadata[0].name
    replicas     = 1

    selector {
      match_labels = { app = "mongodb" }
    }

    template {
      metadata {
        labels = { app = "mongodb" }
      }

      spec {
        container {
          name  = "mongodb"
          image = "mongo:7.0"

          port { container_port = 27017 }

          env_from {
            secret_ref { name = kubernetes_secret_v1.mongodb.metadata[0].name }
          }

          volume_mount {
            name       = "data"
            mount_path = "/data/db"
          }
        }
      }
    }

    volume_claim_template {
      metadata { name = "data" }

      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = "3Gi" }
        }
      }
    }
  }
}

resource "kubernetes_config_map_v1" "app" {
  metadata {
    name      = "eventus-api-config"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  data = {
    SERVICE_NAME     = "eventus-api"
    CHAOS_ERROR_RATE = "0"
  }
}

resource "kubernetes_deployment_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    replicas               = 2
    revision_history_limit = 5
    min_ready_seconds      = 5

    selector {
      match_labels = { app = "eventus-api" }
    }

    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_surge       = "1"
        max_unavailable = "0"
      }
    }

    template {
      metadata {
        labels = { app = "eventus-api" }
      }

      spec {
        container {
          name  = "api"
          image = var.app_image

          port { container_port = 3000 }

          env_from {
            config_map_ref { name = kubernetes_config_map_v1.app.metadata[0].name }
          }

          env {
            name = "MONGODB_URI"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.mongodb.metadata[0].name
                key  = "MONGODB_URI"
              }
            }
          }

          env {
            name = "POD_NAME"
            value_from {
              field_ref { field_path = "metadata.name" }
            }
          }

          readiness_probe {
            http_get {
              path = "/health/ready"
              port = 3000
            }
            initial_delay_seconds = 10
            period_seconds        = 10
          }

          liveness_probe {
            http_get {
              path = "/health/live"
              port = 3000
            }
            initial_delay_seconds = 30
            period_seconds        = 15
            failure_threshold     = 6
          }

          resources {
            requests = { cpu = "100m", memory = "160Mi" }
            limits   = { cpu = "600m", memory = "384Mi" }
          }

          security_context {
            allow_privilege_escalation = false
            capabilities { drop = ["ALL"] }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      spec[0].template[0].spec[0].container[0].image,
      metadata[0].annotations,
    ]
  }

  depends_on = [kubernetes_stateful_set_v1.mongodb]
}

resource "kubernetes_service_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    selector = { app = "eventus-api" }
    port {
      port        = 3000
      target_port = 3000
    }
  }
}

resource "kubernetes_ingress_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    ingress_class_name = "traefik"

    rule {
      http {
        path {
          path      = "/"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.api.metadata[0].name
              port { number = 3000 }
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector = { app = "elasticsearch" }
    port {
      port        = 9200
      target_port = 9200
    }
  }
}

resource "kubernetes_stateful_set_v1" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    service_name = kubernetes_service_v1.elasticsearch.metadata[0].name
    replicas     = 1

    selector {
      match_labels = { app = "elasticsearch" }
    }

    template {
      metadata {
        labels = { app = "elasticsearch" }
      }

      spec {
        security_context {
          fs_group = 1000
        }

        init_container {
          name    = "fix-permissions"
          image   = "busybox:1.36"
          command = ["sh", "-c", "chown -R 1000:1000 /usr/share/elasticsearch/data"]

          security_context {
            run_as_user = 0
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/elasticsearch/data"
          }
        }

        container {
          name  = "elasticsearch"
          image = "docker.elastic.co/elasticsearch/elasticsearch:${var.elastic_version}"

          port { container_port = 9200 }

          env {
            name  = "discovery.type"
            value = "single-node"
          }

          env {
            name  = "xpack.security.enabled"
            value = "false"
          }

          env {
            name  = "xpack.ml.enabled"
            value = "false"
          }

          env {
            name  = "ES_JAVA_OPTS"
            value = "-Xms1g -Xmx1g"
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/elasticsearch/data"
          }

          resources {
            requests = {
              memory = "1536Mi"
            }
            limits = {
              memory = "2560Mi"
            }
          }

          readiness_probe {
            http_get {
              path = "/_cluster/health?local=true"
              port = 9200
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            failure_threshold     = 20
          }
        }
      }
    }

    volume_claim_template {
      metadata { name = "data" }

      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = "5Gi" }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector = { app = "kibana" }
    port {
      port        = 5601
      target_port = 5601
    }
  }
}

resource "kubernetes_deployment_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    replicas = 1

    selector {
      match_labels = { app = "kibana" }
    }

    template {
      metadata {
        labels = { app = "kibana" }
      }

      spec {
        container {
          name  = "kibana"
          image = "docker.elastic.co/kibana/kibana:${var.elastic_version}"

          port { container_port = 5601 }

          env {
            name  = "ELASTICSEARCH_HOSTS"
            value = local.es_url
          }

          env {
            name  = "SERVER_PUBLICBASEURL"
            value = "http://kibana.local"
          }

          resources {
            requests = {
              memory = "512Mi"
            }
            limits = {
              memory = "1280Mi"
            }
          }

          readiness_probe {
            http_get {
              path = "/api/status"
              port = 5601
            }
            initial_delay_seconds = 40
            period_seconds        = 10
            failure_threshold     = 30
          }
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set_v1.elasticsearch]
}

resource "kubernetes_ingress_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    ingress_class_name = "traefik"

    rule {
      host = "kibana.local"

      http {
        path {
          path      = "/"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.kibana.metadata[0].name
              port { number = 5601 }
            }
          }
        }
      }
    }
  }
}

locals {
  filebeat_config = yamlencode({
    "filebeat.inputs" = [{
      type                          = "filestream"
      id                            = "eventus-api"
      "prospector.scanner.symlinks" = true
      paths                         = ["/var/log/containers/eventus-api-*.log"]
      parsers = [
        { container = { stream = "all", format = "auto" } },
        { ndjson = { target = "", overwrite_keys = true, add_error_key = true } },
      ]
    }]

    "output.elasticsearch" = {
      hosts = [local.es_url]
      index = "${local.log_index}-%%{+yyyy.MM.dd}"
    }

    "setup.template.enabled" = false
    "setup.ilm.enabled"      = false
  })
}

resource "kubernetes_service_account_v1" "filebeat" {
  metadata {
    name      = "filebeat"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }
}

resource "kubernetes_cluster_role_v1" "filebeat" {
  metadata { name = "filebeat" }

  rule {
    api_groups = [""]
    resources  = ["namespaces", "pods", "nodes"]
    verbs      = ["get", "list", "watch"]
  }
}

resource "kubernetes_cluster_role_binding_v1" "filebeat" {
  metadata { name = "filebeat" }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role_v1.filebeat.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.filebeat.metadata[0].name
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }
}

resource "kubernetes_config_map_v1" "filebeat" {
  metadata {
    name      = "filebeat-config"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  data = {
    "filebeat.yml" = local.filebeat_config
  }
}

resource "kubernetes_daemon_set_v1" "filebeat" {
  metadata {
    name      = "filebeat"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector {
      match_labels = { app = "filebeat" }
    }

    template {
      metadata {
        labels      = { app = "filebeat" }
        annotations = { "checksum/config" = sha256(local.filebeat_config) }
      }

      spec {
        service_account_name = kubernetes_service_account_v1.filebeat.metadata[0].name

        container {
          name  = "filebeat"
          image = "docker.elastic.co/beats/filebeat:${var.elastic_version}"
          args  = ["-c", "/etc/filebeat.yml", "-e"]

          security_context {
            run_as_user = 0
          }

          volume_mount {
            name       = "config"
            mount_path = "/etc/filebeat.yml"
            sub_path   = "filebeat.yml"
            read_only  = true
          }

          volume_mount {
            name       = "varlogcontainers"
            mount_path = "/var/log/containers"
            read_only  = true
          }

          volume_mount {
            name       = "varlogpods"
            mount_path = "/var/log/pods"
            read_only  = true
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/filebeat/data"
          }
        }

        volume {
          name = "config"
          config_map { name = kubernetes_config_map_v1.filebeat.metadata[0].name }
        }

        volume {
          name = "varlogcontainers"
          host_path { path = "/var/log/containers" }
        }

        volume {
          name = "varlogpods"
          host_path { path = "/var/log/pods" }
        }

        volume {
          name = "data"
          host_path {
            path = "/var/lib/filebeat-data"
            type = "DirectoryOrCreate"
          }
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set_v1.elasticsearch]
}

resource "kubernetes_service_account_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }
}

resource "kubernetes_role_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments"]
    verbs      = ["get", "list", "patch", "update"]
  }

  rule {
    api_groups = ["apps"]
    resources  = ["replicasets"]
    verbs      = ["get", "list"]
  }

  rule {
    api_groups = [""]
    resources  = ["events"]
    verbs      = ["create"]
  }
}

resource "kubernetes_role_binding_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.healer.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.healer.metadata[0].name
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }
}

resource "kubernetes_config_map_v1" "healer" {
  metadata {
    name      = "eventus-healer-config"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }

  data = {
    ES_URL           = local.es_url
    ES_INDEX         = "${local.log_index}-*"
    TARGET_DEPLOY    = kubernetes_deployment_v1.api.metadata[0].name
    ERROR_THRESHOLD  = "10"
    MIN_ERROR_RATIO  = "0.25"
    WINDOW_MINUTES   = "5"
    COOLDOWN_MINUTES = "10"
  }
}

resource "kubernetes_cron_job_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }

  spec {
    schedule                      = "* * * * *"
    concurrency_policy            = "Forbid"
    successful_jobs_history_limit = 3
    failed_jobs_history_limit     = 3

    job_template {
      metadata {}

      spec {
        backoff_limit              = 0
        active_deadline_seconds    = 50
        ttl_seconds_after_finished = 300

        template {
          metadata {}

          spec {
            service_account_name = kubernetes_service_account_v1.healer.metadata[0].name
            restart_policy       = "Never"

            container {
              name  = "healer"
              image = var.healer_image

              env_from {
                config_map_ref { name = kubernetes_config_map_v1.healer.metadata[0].name }
              }
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_role_binding_v1.healer]
}

resource "kubernetes_service_account_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }
}

resource "kubernetes_secret_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer-token"
    namespace = kubernetes_namespace_v1.app.metadata[0].name

    annotations = {
      "kubernetes.io/service-account.name" = kubernetes_service_account_v1.jenkins.metadata[0].name
    }
  }

  type                           = "kubernetes.io/service-account-token"
  wait_for_service_account_token = true
}

resource "kubernetes_role_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "deployments/status", "replicasets"]
    verbs      = ["get", "list", "patch", "update"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods", "pods/log"]
    verbs      = ["get", "list"]
  }
}

resource "kubernetes_role_binding_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.jenkins.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.jenkins.metadata[0].name
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }
}

output "api_url" {
  value = "http://localhost/"
}

output "kibana_url" {
  value = "http://kibana.local/"
}

output "log_index" {
  value = "${local.log_index}-*"
}
