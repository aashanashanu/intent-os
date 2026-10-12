output "app_url" {
  description = "Public HTTPS URL from CloudFront; no domain or DNS setup is required."
  value       = "https://${module.cdn.domain_name}"
}

output "cloudfront_domain_name" { value = module.cdn.domain_name }
output "cloudfront_distribution_id" { value = module.cdn.distribution_id }
output "frontend_bucket_name" { value = module.storage.frontend_bucket_name }
output "artifact_bucket_name" { value = module.storage.artifact_bucket_name }
output "backend_instance_id" { value = module.backend.instance_id }
output "runtime_parameter_name" { value = module.backend.runtime_parameter_name }
output "github_deploy_role_arn" { value = module.github_deploy.role_arn }