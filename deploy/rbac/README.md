# 发布 Runner RBAC 合同

`overlays/dev`、`overlays/staging` 和 `overlays/production` 只创建目标 namespace 内的两个
ServiceAccount、一个 Role 和一个 RoleBinding：

- `dmh-deployer` 是受保护 ARC ephemeral Runner 使用的身份，`automountServiceAccountToken: true`，
  仅拥有该 namespace 的发布资源操作和六个固定 Secret 名称的 `get` 权限；
- `dmh-runtime` 是应用 Pod/迁移 Job 使用的身份，`automountServiceAccountToken: false`，不绑定
  发布 Role。

平台管理员必须把 ARC Runner Pod/ScaleSet 绑定到目标 namespace 的 `dmh-deployer`，并让 GitHub
Environment/Runner Group 只允许受信任的部署 Workflow 调度。不能把 `dmh-deployer` 绑定到业务
Deployment、StatefulSet 或 Job，也不能把 Runner 加入 PR 工作流可用的标签组。

应用 Helm Chart 通过 `lookup` 复用平台预置身份；如果目标 namespace 中缺少身份，部署 Workflow
会在 preflight 阶段失败。Chart 的本地兼容兜底只用于开发安装，不能代替平台 overlay。

验证实际 Runner kubeconfig，而不是只验证静态 Role：

```bash
kubectl auth whoami -o json | jq -r '.status.userInfo.username'
kubectl -n "$NAMESPACE" auth can-i list secrets
kubectl -n "$NAMESPACE" auth can-i get secret/dmh-runtime
```

预期身份为 `system:serviceaccount:<namespace>:dmh-deployer`；`list secrets` 必须为 `no`，命名
Secret 的 `get` 才允许为 `yes`。Runner token 可用于 Kubernetes API 认证，但流水线仍不得读取
Secret `.data` 或把环境变量写入日志/Artifact。
