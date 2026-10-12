module "storage" {
  source       = "./modules/s3"
  project_name = var.project_name
}

module "network" {
  source       = "./modules/network"
  project_name = var.project_name
  aws_region   = var.aws_region
}

module "backend" {
  source = "./modules/ec2"

  project_name      = var.project_name
  aws_region        = var.aws_region
  instance_type     = var.ec2_instance_type
  vpc_id            = module.network.vpc_id
  private_subnet_id = module.network.private_subnet_id
  vpc_cidr          = module.network.vpc_cidr
  deployment_bucket = module.storage.artifact_bucket_name
  runtime_parameter = "/${var.project_name}/prod/backend-env"
}

module "cdn" {
  source = "./modules/cloudfront"

  project_name         = var.project_name
  frontend_bucket      = module.storage.frontend_bucket_domain
  backend_instance_arn = module.backend.instance_arn
  backend_private_dns  = module.backend.private_dns

  depends_on = [module.network]
}

module "github_deploy" {
  source = "./modules/github-deploy"

  project_name          = var.project_name
  github_repository     = var.github_repository
  frontend_bucket_arn   = module.storage.frontend_bucket_arn
  artifact_bucket_arn   = module.storage.artifact_bucket_arn
  distribution_arn      = module.cdn.distribution_arn
  backend_instance_arn  = module.backend.instance_arn
  runtime_parameter_arn = module.backend.runtime_parameter_arn
}

resource "aws_s3_bucket_policy" "frontend_cloudfront" {
  bucket = module.storage.frontend_bucket_id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontReadOnly"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${module.storage.frontend_bucket_arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = module.cdn.distribution_arn } }
    }]
  })
}