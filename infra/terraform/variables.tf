variable "aws_region" {
  description = "AWS region for EC2 and the private application VPC."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix used for AWS resource names."
  type        = string
  default     = "intent-os"
}

variable "ec2_instance_type" {
  description = "EC2 instance size for the Spring Boot backend. t3.micro is the low-cost 1 GiB option for low-traffic demos."
  type        = string
  default     = "t3.micro"
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy, in owner/name form."
  type        = string
}