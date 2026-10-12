resource "aws_cloudfront_distribution" "app" {
  enabled             = true
  comment             = "${var.project_name} public application"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_100"
  default_root_object = "index.html"

  origin {
    domain_name              = var.frontend_bucket
    origin_id                = "${var.project_name}-frontend-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id

  }

  origin {
    domain_name = var.backend_private_dns
    origin_id   = "${var.project_name}-backend-ec2"

    vpc_origin_config {
      vpc_origin_id = aws_cloudfront_vpc_origin.backend.id
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "${var.project_name}-backend-ec2"
    viewer_protocol_policy = "https-only"

    allowed_methods = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods  = ["GET", "HEAD", "OPTIONS"]

    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
    compress                 = true
  }

  default_cache_behavior {
    target_origin_id       = "${var.project_name}-frontend-s3"
    viewer_protocol_policy = "redirect-to-https"

    allowed_methods = ["GET", "HEAD", "OPTIONS"]
    cached_methods  = ["GET", "HEAD", "OPTIONS"]

    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    compress        = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Project   = var.project_name
    ManagedBy = "Terraform"
  }

  depends_on = [aws_cloudfront_origin_access_control.frontend]

}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.project_name}-frontend-oac"
  description                       = "CloudFront access to the private ${var.project_name} frontend bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_vpc_origin" "backend" {
  vpc_origin_endpoint_config {
    arn                    = var.backend_instance_arn
    name                   = "${var.project_name}-backend"
    http_port              = 8080
    https_port             = 443
    origin_protocol_policy = "http-only"

    origin_ssl_protocols {
      quantity = 1
      items    = ["TLSv1.2"]
    }
  }
}