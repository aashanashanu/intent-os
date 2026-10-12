variable "project_name" {
  type = string
}

variable "frontend_bucket" {
  description = "Private S3 bucket name for the frontend build."
  type        = string
}

variable "backend_instance_arn" {
  description = "Private EC2 instance used as a CloudFront VPC origin."
  type        = string
}

variable "backend_private_dns" {
  description = "Private DNS name for the CloudFront VPC origin target."
  type        = string
}