{{- define "dmh.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "dmh.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- include "dmh.name" . -}}
{{- end -}}
{{- end -}}
{{- define "dmh.labels" -}}
app.kubernetes.io/name: {{ include "dmh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: data-manager-hub
dmh.io/environment: {{ .Values.global.environment | quote }}
dmh.io/manifest-digest: {{ .Values.global.manifestDigest | quote }}
{{- end -}}
{{- define "dmh.validateNacosGroup" -}}
{{- $environment := .Values.global.environment | default "" -}}
{{- $group := .Values.global.nacosGroup | default "" -}}
{{- if eq $environment "staging" -}}
{{- if not (regexMatch "^DMH_STAGING_[0-9a-f]{40}$" $group) -}}
{{- fail "staging Helm renders require global.nacosGroup=DMH_STAGING_<40-char-master-sha>" -}}
{{- end -}}
{{- else if eq $environment "production" -}}
{{- if not (regexMatch "^DMH_PROD_[0-9a-f]{40}$" $group) -}}
{{- fail "production Helm renders require global.nacosGroup=DMH_PROD_<40-char-master-sha>" -}}
{{- end -}}
{{- else if eq $environment "dev" -}}
{{- if not (regexMatch "^(DEFAULT_GROUP|DMH_DEV_[A-Za-z0-9_-]+)$" $group) -}}
{{- fail "dev Helm renders require DEFAULT_GROUP or a DMH_DEV_* group" -}}
{{- end -}}
{{- else -}}
{{- fail "global.environment must be dev, staging, or production" -}}
{{- end -}}
{{- end -}}
