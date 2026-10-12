data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  vpc_origin_zones = [
    for index, zone_id in data.aws_availability_zones.available.zone_ids : data.aws_availability_zones.available.names[index]
    if zone_id != "use1-az3"
  ]
}

resource "aws_vpc" "app" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name    = "${var.project_name}-vpc"
    Project = var.project_name
  }
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id

  tags = {
    Name    = "${var.project_name}-igw"
    Project = var.project_name
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.app.id
  cidr_block              = "10.42.0.0/24"
  availability_zone       = local.vpc_origin_zones[0]
  map_public_ip_on_launch = true

  tags = {
    Name    = "${var.project_name}-public"
    Project = var.project_name
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.app.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.app.id
  }

  tags = {
    Name    = "${var.project_name}-public"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_subnet" "private" {
  vpc_id                  = aws_vpc.app.id
  cidr_block              = "10.42.1.0/24"
  availability_zone       = local.vpc_origin_zones[0]
  map_public_ip_on_launch = false

  tags = {
    Name    = "${var.project_name}-private"
    Project = var.project_name
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.app.id

  tags = {
    Name    = "${var.project_name}-private"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

resource "aws_security_group" "nat" {
  name        = "${var.project_name}-nat"
  description = "Outbound NAT for the private backend subnet"
  vpc_id      = aws_vpc.app.id

  ingress {
    description = "Forwarded traffic from private application subnet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [aws_subnet.private.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-nat"
    Project = var.project_name
  }
}

resource "aws_iam_role" "nat" {
  name = "${var.project_name}-nat-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "nat_ssm" {
  role       = aws_iam_role.nat.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "nat" {
  name = "${var.project_name}-nat-instance-profile"
  role = aws_iam_role.nat.name
}

resource "aws_instance" "nat" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = "t3.nano"
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.nat.id]
  iam_instance_profile        = aws_iam_instance_profile.nat.name
  source_dest_check           = false
  user_data                   = file("${path.module}/nat_user_data.sh")
  user_data_replace_on_change = true

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size           = 8
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name      = "${var.project_name}-nat"
    Project   = var.project_name
    ManagedBy = "Terraform"
  }

  depends_on = [aws_route_table_association.public]
}

resource "aws_route" "private_default" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_instance.nat.primary_network_interface_id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.app.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]

  tags = {
    Name    = "${var.project_name}-s3"
    Project = var.project_name
  }
}
