output "instance_id" { value = aws_instance.backend.id }
output "instance_arn" { value = aws_instance.backend.arn }
output "private_dns" { value = aws_instance.backend.private_dns }
output "runtime_parameter_name" { value = var.runtime_parameter }
output "runtime_parameter_arn" {
  value = aws_ssm_parameter.runtime.arn
}